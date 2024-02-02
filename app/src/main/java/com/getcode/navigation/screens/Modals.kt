package com.getcode.navigation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.sheetHeight
import com.getcode.util.keyboardAsState
import com.getcode.util.recomposeHighlighter
import com.getcode.view.components.SheetTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal interface ModalContent {

    @Composable
    fun Screen.ModalContainer(
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        ModalContainer(
            navigator = LocalCodeNavigator.current,
            displayLogo = false,
            backButton = { false },
            onLogoClicked = {},
            closeButton = closeButton,
            screenContent = screenContent,
        )
    }

    @Composable
    fun Screen.ModalContainer(
        displayLogo: Boolean = false,
        onLogoClicked: () -> Unit = { },
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        ModalContainer(
            navigator = LocalCodeNavigator.current,
            displayLogo = displayLogo,
            backButton = { false },
            onLogoClicked = onLogoClicked,
            closeButton = closeButton,
            screenContent = screenContent,
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Screen.ModalContainer(
        navigator: CodeNavigator = LocalCodeNavigator.current,
        displayLogo: Boolean = false,
        backButton: (Screen?) -> Boolean = { false },
        onBackClicked: (() -> Unit)? = null,
        closeButton: (Screen?) -> Boolean = { false },
        onCloseClicked: (() -> Unit)? = null,
        onLogoClicked: () -> Unit = { },
        screenContent: @Composable () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val lastItem by remember(navigator.lastModalItem) {
                derivedStateOf { navigator.lastModalItem }
            }

            val isBackEnabled by remember(backButton, lastItem) {
                derivedStateOf { backButton(lastItem) }
            }

            val isCloseEnabled by remember(closeButton, lastItem) {
                derivedStateOf { closeButton(lastItem) }
            }

            val keyboardController = LocalSoftwareKeyboardController.current
            val composeScope = rememberCoroutineScope()

            val keyboardVisible by keyboardAsState()

            val hideSheet = {
                composeScope.launch {
                    if (keyboardVisible) {
                        keyboardController?.hide()
                        delay(500)
                    }
                    navigator.hide()
                }
            }

            SheetTitle(
                modifier = Modifier,
                title = {
                    val name = (lastItem as? NamedScreen)?.name
                    val sheetName by remember(lastItem) {
                        derivedStateOf { name }
                    }
                    sheetName.takeIf { !displayLogo && lastItem == this@ModalContainer }
                },
                displayLogo = displayLogo,
                onLogoClicked = onLogoClicked,
                // hide while transitioning to/from other destinations
                backButton = isBackEnabled,
                closeButton = isCloseEnabled,
                onBackIconClicked = onBackClicked?.let { { it() } } ?: { navigator.pop() },
                onCloseIconClicked = onCloseClicked?.let { { it() } } ?: { hideSheet() }
            )
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                CompositionLocalProvider(
                    LocalOverscrollConfiguration provides null
                ) {
                    screenContent()
                }
            }
        }
    }
}

internal sealed interface ModalRoot : ModalContent

data object MainRoot : Screen {
    override val key: ScreenKey = uniqueScreenKey
    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        // TODO: potentially add a loading state here
        //  so app doesn't appear stuck in a dead state
        //  while we wait for auth check to complete
        Box(modifier = Modifier.fillMaxSize().background(CodeTheme.colors.background))
    }
}