package com.getcode

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.navigation.core.BottomSheetNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.MainRoot
import com.getcode.navigation.transitions.SheetSlideTransition
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeColors
import com.getcode.util.getActivity
import com.getcode.util.getActivityScopedViewModel
import com.getcode.view.components.AuthCheck
import com.getcode.view.components.BottomBarContainer
import com.getcode.view.components.CodeScaffold
import com.getcode.view.components.TitleBar
import com.getcode.view.components.TopBarContainer

@Composable
fun CodeApp() {
    val tlvm = MainRoot.getActivityScopedViewModel<TopLevelViewModel>()
    val activity = LocalContext.current.getActivity()
    CodeTheme {
        val appState = rememberCodeAppState()
        AppNavHost {
            val codeNavigator = LocalCodeNavigator.current

            CodeScaffold(
                scaffoldState = appState.scaffoldState
            ) { innerPaddingModifier ->

                Navigator(
                    screen = MainRoot,
                ) { navigator ->
                    appState.navigator = codeNavigator

                    LaunchedEffect(navigator.lastItem) {
                        // update global navigator for platform access to support push/pop from a single
                        // navigator current
                        codeNavigator.screensNavigator = navigator
                    }

                    val (isVisibleTopBar, isVisibleBackButton) = appState.isVisibleTopBar
                    if (isVisibleTopBar && appState.currentTitle.isNotBlank()) {
                        TitleBar(
                            title = appState.currentTitle,
                            backButton = isVisibleBackButton,
                            onBackIconClicked = appState::upPress
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(innerPaddingModifier)
                    ) {
                        when (navigator.lastItem) {
                            is LoginScreen, is MainRoot -> CrossfadeTransition(navigator = navigator)
                            else -> SlideTransition(navigator = navigator)
                        }
                    }
                }
            }

            //Listen for authentication changes here
            AuthCheck(
                navigator = codeNavigator,
                onNavigate = { screens ->
                    codeNavigator.replaceAll(screens, inSheet = false)
                },
                onSwitchAccounts = { seed ->
                    activity?.let {
                        tlvm.logout(it) {
                            appState.navigator.replaceAll(LoginScreen(seed))
                        }
                    }
                }
            )
        }

        TopBarContainer(appState)
        BottomBarContainer(appState)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppNavHost(content: @Composable () -> Unit) {
    var combinedNavigator by remember {
        mutableStateOf<CombinedNavigator?>(null)
    }
    BottomSheetNavigator(
        modifier = Modifier.fillMaxSize(),
        sheetBackgroundColor = LocalCodeColors.current.background,
        sheetContentColor = LocalCodeColors.current.onBackground,
        sheetContent = { sheetNav ->
            combinedNavigator = combinedNavigator?.apply { sheetNavigator = sheetNav }
                ?: CombinedNavigator(sheetNav)
            combinedNavigator?.let {
                CompositionLocalProvider(LocalCodeNavigator provides it) {
                    SheetSlideTransition(navigator = it)
                }
            }

        }
    ) { sheetNav ->
        combinedNavigator =
            combinedNavigator?.apply { sheetNavigator = sheetNav } ?: CombinedNavigator(sheetNav)
        combinedNavigator?.let {
            CompositionLocalProvider(LocalCodeNavigator provides it) {
                content()
            }
        }
    }
}

@Composable
private fun CrossfadeTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() }
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        content = content,
        transition = { fadeIn() togetherWith fadeOut() }
    )
}