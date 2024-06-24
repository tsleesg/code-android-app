package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.CurrencyCode
import com.getcode.model.EncryptedData
import com.getcode.model.Fiat
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.MessageStatus
import com.getcode.model.Rate
import com.getcode.network.repository.toPublicKey

sealed interface MessageContent {
    val isFromSelf: Boolean

    data class Localized(
        val value: String,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class RawText(
        val value: String,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class Exchange(
        val amount: GenericAmount,
        val verb: Verb,
        val reference: Reference,
        val didThank: Boolean,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class SodiumBox(
        val data: EncryptedData,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class ThankYou(
        val tipIntentId: ID,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class IdentityRevealed(
        val memberId: ID,
        val identity: Identity,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    data class Decrypted(
        val data: String,
        override val isFromSelf: Boolean = false,
    ) : MessageContent

    companion object {
        operator fun invoke(
            proto: ChatService.Content,
            isFromSelf: Boolean = false,
        ): MessageContent? {
            return when (proto.typeCase) {
                ChatService.Content.TypeCase.LOCALIZED -> Localized(
                    isFromSelf = isFromSelf,
                    value = proto.localized.keyOrText
                )

                ChatService.Content.TypeCase.EXCHANGE_DATA -> {
                    val verb = Verb(proto.exchangeData.verb)
                    when (proto.exchangeData.exchangeDataCase) {
                        ChatService.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                            val exact = proto.exchangeData.exact
                            val currency = CurrencyCode.tryValueOf(exact.currency) ?: return null
                            val kinAmount = KinAmount.newInstance(
                                kin = Kin.fromQuarks(exact.quarks),
                                rate = Rate(
                                    fx = exact.exchangeRate,
                                    currency = currency
                                )
                            )


                            val reference = Reference(proto.exchangeData)
                            Exchange(
                                isFromSelf = isFromSelf,
                                amount = GenericAmount.Exact(kinAmount),
                                verb = verb,
                                reference = reference,
                                didThank = false,
                            )
                        }

                        ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            val reference = Reference(proto.exchangeData)

                            Exchange(
                                isFromSelf = isFromSelf,
                                amount = GenericAmount.Partial(fiat),
                                verb = verb,
                                reference = reference,
                                didThank = false
                            )
                        }

                        ChatService.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                        else -> return null
                    }
                }

                ChatService.Content.TypeCase.NACL_BOX -> {
                    val encryptedContent = proto.naclBox
                    val peerPublicKey =
                        encryptedContent.peerPublicKey.value.toByteArray().toPublicKey()

                    val data = EncryptedData(
                        peerPublicKey = peerPublicKey,
                        nonce = encryptedContent.nonce.toByteArray().toList(),
                        encryptedData = encryptedContent.encryptedPayload.toByteArray().toList(),
                    )
                    SodiumBox(isFromSelf = isFromSelf, data = data)
                }

                ChatService.Content.TypeCase.THANK_YOU -> {
                    ThankYou(
                        isFromSelf = isFromSelf,
                        tipIntentId = proto.thankYou.tipIntent.toByteArray().toList()
                    )
                }

                ChatService.Content.TypeCase.IDENTITY_REVEALED -> {
                    IdentityRevealed(
                        isFromSelf = isFromSelf,
                        memberId = proto.identityRevealed.memberId.toByteArray().toList(),
                        identity = Identity(proto.identityRevealed.identity)
                    )
                }

                ChatService.Content.TypeCase.TEXT -> RawText(
                    isFromSelf = isFromSelf,
                    value = proto.text.text
                )

                ChatService.Content.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }
    }
}