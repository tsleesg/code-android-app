package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.ConnectedTipAccount
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TwitterUserFetchError
import com.getcode.utils.getOrPutIfNonNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

typealias TipUser = Pair<String, CodePayload>

@Singleton
class TipController @Inject constructor(
    private val client: Client,
    private val prefRepository: PrefRepository,
) {

    private var pollTimer: Timer? = null
    private var lastPoll: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    private var cachedUsers = mutableMapOf<String, TwitterUser>()

    var scannedUserData: TipUser? = null
        private set
    var userMetadata: TwitterUser? = null
        private set

    val connectedAccount: StateFlow<ConnectedTipAccount?> = prefRepository.observeOrDefault(PrefsString.KEY_TIP_ACCOUNT, "")
        .map { runCatching { Json.decodeFromString<TwitterUser>(it) }.getOrNull() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val showTwitterSplat: Flow<Boolean> =
        combine(
            connectedAccount,
            prefRepository.observeOrDefault(PrefsBool.SEEN_TIP_CARD, false)
        ) { connected, seen ->
            connected != null && !seen
        }

    private fun startPollTimer() {
        Timber.d("twitter poll start")
        pollTimer?.cancel()
        pollTimer = fixedRateTimer("twitterPollTimer", false, 0, 1000 * 20) {
            scope.launch {
                val time = System.currentTimeMillis()
                val isPastThrottle = time - lastPoll > 1000 * 10 || lastPoll == 0L

                if (isPastThrottle) {
                    callForConnectedUser()
                }
            }
        }
    }

    private suspend fun callForConnectedUser() {
        Timber.d("twitter poll call")
        val tipAddress = SessionManager.getOrganizer()?.primaryVault ?: return
        // only set lastPoll if we actively attempt to reach RPC
        lastPoll = System.currentTimeMillis()
        client.fetchTwitterUser(tipAddress)
            .onSuccess {
                Timber.d("current user twitter connected @ ${it.username}")
                prefRepository.set(PrefsString.KEY_TIP_ACCOUNT, Json.encodeToString(it))
            }
            .onFailure {
                when (it) {
                    is TwitterUserFetchError -> {
                        prefRepository.set(PrefsString.KEY_TIP_ACCOUNT, "")
                    }
                }
            }
    }

    init {
        SessionManager.authState
            .map { it.organizer }
            .filterNotNull()
            .map { it.primaryVault }
            .onEach { checkForConnection() }
            .launchIn(scope)
    }

    fun checkForConnection() {
        startPollTimer()
    }

    suspend fun fetch(username: String, payload: CodePayload) {
        val metadata = fetch(username)
        scannedUserData = username to payload
        userMetadata = metadata
    }

    suspend fun fetch(username: String): TwitterUser? {
        val key = username.lowercase()
        return cachedUsers.getOrPutIfNonNull(key) {
            Timber.d("fetching user $username")
            client.fetchTwitterUser(username).getOrThrow()
        }
    }

    fun reset() {
        scannedUserData = null
        userMetadata = null
    }

    fun clearTwitterSplat() {
        prefRepository.set(PrefsBool.SEEN_TIP_CARD, true)
    }

    fun stopTimer() {
        pollTimer?.cancel()
    }
}