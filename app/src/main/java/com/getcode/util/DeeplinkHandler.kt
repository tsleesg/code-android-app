package com.getcode.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.manager.AnalyticsService
import com.getcode.manager.AnalyticsServiceNull
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.MainRoot
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * This class is used to manage intent state across navigation.
 *
 * This hack is in place because determining the users authenticated
 * state takes a long time. The app will try to handle incoming intents
 * before the authentication state is determined.
 * This class caches the incoming deeplink intent so the navigation controller
 * does not override the intent sent by handleDeepLink(intent) in the main activity.
 *
 * If this was not in place the app would try to handle the deeplink before the
 * authentication state is complete and override navigation - dropping the intent
 * in favour of the latest request in the navigation graph.
 */

val LocalDeeplinks: ProvidableCompositionLocal<DeeplinkHandler?> = staticCompositionLocalOf { null }

class DeeplinkHandler @Inject constructor() {
    var debounceIntent: Intent? = null
        set(value) {
            field = value
            intent.value = value
        }
    val intent = MutableStateFlow(debounceIntent)

    fun checkIntent(intent: Intent): Intent? {
        Timber.d("checking intent=${intent.data}")
        handle(intent) ?: return null
        return intent
    }
    fun handle(intent: Intent? = debounceIntent): Pair<Type, List<Screen>>? {
        val uri = intent?.data ?: return null

        val type = when (val segment = uri.lastPathSegment) {
            "cash", "c" -> {
                Timber.d("cashlink in ${uri}")
                val fragment = uri.fragment
                val separator = "="
                val result = Key.entries
                    .map { key -> "/${key.value}$separator" }
                    .filter { prefix -> fragment?.startsWith(prefix) == true }
                    .firstNotNullOfOrNull { prefix -> fragment?.removePrefix(prefix) }

                Type.Cash(result)
            }
            else -> Type.Unknown(segment)
        }

        return when (type) {
            is Type.Cash -> {
                Timber.d("cashlink=${type.link}")
                type to listOf(HomeScreen(cashLink = type.link))
            }
            Type.Sdk -> null
            is Type.Unknown -> null
        }
    }

    sealed interface Type {
        data class Cash(val link: String?): Type
        data object Sdk: Type
        data class Unknown(val path: String?): Type
    }

    @Suppress("ClassName")
    sealed interface Key {
        val value: String
        data object entropy: Key {
            override val value: String = "e"
        }
        data object payload: Key {
            override val value: String = "p"
        }
        // unused
        data object key: Key {
            override val value: String = "k"
        }
        // unused
        data object data: Key {
            override val value: String = "d"
        }

        companion object {
            val entries = listOf(entropy, payload, key, data)
        }
    }
}