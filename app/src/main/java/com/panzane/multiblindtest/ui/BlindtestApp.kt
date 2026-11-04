package com.panzane.multiblindtest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.game.Screen
import com.panzane.multiblindtest.ui.screens.PlayersScreen
import com.panzane.multiblindtest.ui.screens.PlayingScreen
import com.panzane.multiblindtest.ui.screens.ResultsScreen
import com.panzane.multiblindtest.ui.screens.RevealScreen
import com.panzane.multiblindtest.ui.screens.SelectionScreen

@Composable
fun BlindtestApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmQuit by rememberSaveable { mutableStateOf(false) }

    // Mode buzzers : l'écran reste allumé pour que les téléphones des joueurs restent connectés
    if (state.settings.buzzers) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    BackHandler(enabled = state.screen != Screen.PLAYERS) {
        when (state.screen) {
            Screen.SELECTION, Screen.PLAYING, Screen.REVEAL -> confirmQuit = true
            Screen.RESULTS -> viewModel.newGame()
            Screen.PLAYERS -> Unit
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quitter la partie ?") },
            text = { Text("Les scores de la partie en cours seront perdus.") },
            confirmButton = {
                TextButton(onClick = { confirmQuit = false; viewModel.newGame() }) { Text("Quitter") }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Continuer") }
            },
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.PLAYERS -> PlayersScreen(state, viewModel)
                Screen.SELECTION -> SelectionScreen(state, viewModel)
                Screen.PLAYING -> PlayingScreen(state, viewModel)
                Screen.REVEAL -> RevealScreen(state, viewModel)
                Screen.RESULTS -> ResultsScreen(state, viewModel)
            }
        }
    }
}
