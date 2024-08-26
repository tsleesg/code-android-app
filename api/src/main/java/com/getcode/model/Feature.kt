package com.getcode.model

import com.getcode.network.repository.BetaOptions

sealed interface Feature {
    val enabled: Boolean
    val available: Boolean
}

data class BuyModuleFeature(
    override val enabled: Boolean = BetaOptions.Defaults.buyModuleEnabled,
    override val available: Boolean = false, // server driven availability
): Feature

data class TipCardFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipsEnabled,
    override val available: Boolean = true, // always available
): Feature

data class TipCardOnHomeScreenFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipCardOnHomeScreen,
    override val available: Boolean = true, // always available
): Feature

data class ConversationsFeature(
    override val enabled: Boolean = BetaOptions.Defaults.conversationsEnabled,
    override val available: Boolean = true, // always available
): Feature

data class ConversationCashFeature(
    override val enabled: Boolean = BetaOptions.Defaults.conversationCashEnabled,
    override val available: Boolean = true, // always available
): Feature


data class RequestKinFeature(
    override val enabled: Boolean = BetaOptions.Defaults.giveRequestsEnabled,
    override val available: Boolean = true, // always available
): Feature

data class BalanceCurrencyFeature(
    override val enabled: Boolean = BetaOptions.Defaults.balanceCurrencySelectionEnabled,
    override val available: Boolean = true, // always available
): Feature

data class CameraAFFeature(
    override val enabled: Boolean = BetaOptions.Defaults.cameraAFEnabled,
    override val available: Boolean = true, // always available
): Feature

data class CameraZoomFeature(
    override val enabled: Boolean = BetaOptions.Defaults.cameraPinchZoomEnabled,
    override val available: Boolean = true, // always available
): Feature