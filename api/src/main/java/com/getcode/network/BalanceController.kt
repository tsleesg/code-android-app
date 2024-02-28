package com.getcode.network

import android.content.Context
import com.getcode.manager.SessionManager
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import com.getcode.utils.FormatUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BalanceDisplay(
    val flag: Int? = null,
    val marketValue: Double = 0.0,
    val formattedValue: String = "",

)
class BalanceController @Inject constructor(
    exchange: Exchange,
    networkObserver: NetworkConnectivityListener,
    getCurrency: suspend (rates: Map<CurrencyCode, Rate>) -> Currency,
    @ApplicationContext
    private val context: Context,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val privacyMigration: PrivacyMigration,
    private val transactionReceiver: TransactionReceiver,
    val getDefaultCountry: () -> String,
    val suffix: () -> String,
): CoroutineScope by CoroutineScope(Dispatchers.IO) {

    fun observeRawBalance(): Flow<Double> = balanceRepository.balanceFlow

    val rawBalance: Double
        get() = balanceRepository.balanceFlow.value

    private val _balanceDisplay = MutableStateFlow<BalanceDisplay?>(null)
    val formattedBalance: SharedFlow<BalanceDisplay?>
        get() = _balanceDisplay
            .stateIn(this, SharingStarted.Eagerly, BalanceDisplay())

    init {
        combine(
            exchange.observeRates()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .map { getCurrency(it) }
                .onEach {
                    val display = _balanceDisplay.value ?: BalanceDisplay()
                    _balanceDisplay.value = display.copy(flag = it.resId)
                }
                .mapNotNull { currency -> CurrencyCode.tryValueOf(currency.code) }
                .mapNotNull {
                    exchange.fetchRatesIfNeeded()
                    exchange.rateFor(it)
                },
            balanceRepository.balanceFlow,
            networkObserver.state
        ) { rate, balance, _ ->
            rate to balance
        }.map { (rate, balance) ->
            refreshBalance(balance, rate.fx)
        }.distinctUntilChanged().onEach { (marketValue, amountText) ->
            val display = _balanceDisplay.value ?: BalanceDisplay()
            _balanceDisplay.value = display.copy(marketValue = marketValue, formattedValue = amountText)
        }.launchIn(this)
    }

    fun setTray(organizer: Organizer, tray: Tray) {
        organizer.set(tray)
        balanceRepository.setBalance(organizer.availableBalance.toKinTruncatingLong().toDouble())
    }

    fun fetchBalance(): Completable {
        if (SessionManager.isAuthenticated() != true) {
            Timber.d("FetchBalance - Not authenticated")
            return Completable.complete()
        }
        val owner = SessionManager.getKeyPair() ?: return Completable.error(IllegalStateException("Missing Owner"))

        fun getTokenAccountInfos(): Completable {
            return accountRepository.getTokenAccountInfos(owner)
                .flatMapCompletable { infos ->
                    val organizer = SessionManager.getOrganizer() ?:
                    return@flatMapCompletable Completable.error(IllegalStateException("Missing Organizer"))

                    organizer.setAccountInfo(infos)
                    balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
                    transactionReceiver.receiveFromIncomingCompletable(organizer)
                }
                .timeout(15, TimeUnit.SECONDS)
        }

        return getTokenAccountInfos()
            .doOnSubscribe {
                Timber.i("Fetching Balance account info")
            }
            .onErrorResumeNext {
                Timber.i("Error: ${it.javaClass.simpleName} ${it.cause}")
                val organizer = SessionManager.getOrganizer() ?: return@onErrorResumeNext Completable.error(IllegalStateException("Missing Organizer"))

                when (it) {
                    is AccountRepository.FetchAccountInfosException.MigrationRequiredException -> {
                        val amountToMigrate = it.accountInfo.balance
                        privacyMigration.migrateToPrivacy(
                            context = context,
                            amountToMigrate = amountToMigrate,
                            organizer = organizer
                        )
                            .ignoreElement()
                            .concatWith(getTokenAccountInfos())
                    }
                    is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                        transactionRepository.createAccounts(
                            organizer = organizer
                        )
                            .ignoreElement()
                            .concatWith(getTokenAccountInfos())
                    }
                    else -> {
                        Completable.error(it)
                    }
                }
            }
    }



    suspend fun fetchBalanceSuspend() {
        Timber.d("fetchBalance")
        if (SessionManager.isAuthenticated() != true) {
            Timber.d("FetchBalance - Not authenticated")
            return
        }
        val owner = SessionManager.getKeyPair() ?: throw IllegalStateException("Missing Owner")

        try {
            withTimeout(15000) {
                val accountInfo = accountRepository.getTokenAccountInfos(owner).blockingGet()
                val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")

                Timber.d("updating balance and organizer")
                organizer.setAccountInfo(accountInfo)
                balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
                transactionReceiver.receiveFromIncoming(organizer)
                transactionRepository.swapIfNeeded(organizer)
            }
        } catch (ex: Exception) {
            Timber.i("Error: ${ex.javaClass.simpleName} ${ex.cause}")
            val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")

            when (ex) {
                is AccountRepository.FetchAccountInfosException.MigrationRequiredException -> {
                    val amountToMigrate = ex.accountInfo.balance
                    privacyMigration.migrateToPrivacy(
                        context = context,
                        amountToMigrate = amountToMigrate,
                        organizer = organizer
                    )
                }
                is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                    transactionRepository.createAccounts(
                        organizer = organizer
                    )
                }
            }
        }
    }

    private fun refreshBalance(balance: Double, rate: Double): Pair<Double, String> {
        val fiatValue = FormatUtils.getFiatValue(balance, rate)
        val locale = Locale(
            Locale.getDefault().language,
            getDefaultCountry()
        )
        val fiatValueFormatted = FormatUtils.formatCurrency(fiatValue, locale)
        val amountText = StringBuilder().apply {
            append(fiatValueFormatted)
            append(" ")
            append(suffix())
        }.toString()

        return fiatValue to amountText
    }
}