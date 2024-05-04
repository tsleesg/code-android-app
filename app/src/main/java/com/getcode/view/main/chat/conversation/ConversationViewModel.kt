@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.getcode.model.Conversation
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.TipMetadata
import com.getcode.model.TwitterUser
import com.getcode.network.ConversationController
import com.getcode.solana.keys.PublicKey
import com.getcode.util.CurrencyUtils
import com.getcode.util.formatted
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    conversationController: ConversationController,
    currencyUtils: CurrencyUtils,
    resources: ResourceHelper,
) : BaseViewModel2<ConversationViewModel.State, ConversationViewModel.Event>(
    initialState = State.Default,
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val messageId: ID?,
        val conversationId: ID?,
        val title: String,
        val tipAmount: KinAmount?,
        val tipAmountFormatted: String?,
        val textFieldState: TextFieldState,
        val identityRevealed: Boolean,
        val user: User?,
        val lastSeen: Instant?
    ) {
        data class User(
            val username: String,
            val publicKey: PublicKey,
            val imageUrl: String?,
        )

        companion object {
            val Default = State(
                messageId = null,
                conversationId = null,
                title = "Anonymous Tipper",
                tipAmount = null,
                tipAmountFormatted = null,
                textFieldState = TextFieldState(),
                identityRevealed = false,
                user = null,
                lastSeen = null
            )
        }
    }

    sealed interface Event {
        data class OnMessageIdChanged(val id: ID?) : Event
        data class OnConversationChanged(val conversation: Conversation) : Event
        data class OnUserRevealed(
            val username: String,
            val publicKey: PublicKey,
            val imageUrl: String?,
        ) : Event

        data class OnUserActivity(val activity: Instant) : Event
        data class OnTitleChanged(val title: String) : Event
        data class OnTipAmountFormatted(val amount: String) : Event
        data object SendMessage : Event
        data object RevealIdentity : Event

        data object OnIdentityRevealed: Event
    }

    init {
        stateFlow
            .map { it.messageId }
            .filterNotNull()
            .flatMapLatest { conversationController.observeConversationForMessage(it) }
            .filterNotNull()
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnConversationChanged(it)) }
            .launchIn(viewModelScope)

        stateFlow
            .map { it.tipAmount }
            .filterNotNull()
            .distinctUntilChanged()
            .mapNotNull {
                val currency =
                    currencyUtils.getCurrency(it.rate.currency.name) ?: return@mapNotNull null
                val title =
                    it.formatted(currency = currency, resources = resources, suffix = "Tipper")
                val formatted = it.formatted(currency = currency, resources = resources)
                title to formatted
            }
            .onEach { (title, formattedAmount) ->
                dispatchEvent(Event.OnTitleChanged(title))
                dispatchEvent(Event.OnTipAmountFormatted(formattedAmount))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendMessage>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString()
                Timber.d("sending message of $text")
                textFieldState.clearText()
                conversationController.sendMessage(it.conversationId!!, text)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RevealIdentity>()
            .mapNotNull { stateFlow.value.messageId  }
            .onEach { delay(300) }
            .onEach { conversationController.revealIdentity(it) }
            .launchIn(viewModelScope)
    }

    val messages = stateFlow
        .map { it.conversationId }
        .filterNotNull()
        .flatMapLatest { conversationController.conversationPagingData(it) }
        .cachedIn(viewModelScope)


    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            Timber.d("event=${event}")
            when (event) {
                is Event.OnConversationChanged -> { state ->
                    state.copy(
                        conversationId = event.conversation.id,
                        tipAmount = event.conversation.tipAmount,
                        identityRevealed = event.conversation.hasRevealedIdentity
                    )
                }

                is Event.OnTitleChanged -> { state ->
                    state.copy(
                        title = event.title
                    )
                }

                is Event.OnTipAmountFormatted -> { state ->
                    state.copy(tipAmountFormatted = event.amount)
                }

                Event.RevealIdentity,
                is Event.SendMessage -> { state -> state }

                is Event.OnMessageIdChanged -> { state ->
                    state.copy(messageId = event.id)
                }

                is Event.OnIdentityRevealed -> { state ->
                    state.copy(identityRevealed = true)
                }

                is Event.OnUserRevealed -> { state ->
                    state.copy(
                        user = State.User(
                            username = event.username,
                            publicKey = event.publicKey,
                            imageUrl = event.imageUrl,
                        )
                    )
                }

                is Event.OnUserActivity -> { state ->
                    state.copy(lastSeen = event.activity)
                }
            }
        }
    }
}