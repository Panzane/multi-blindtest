package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panzane.multiblindtest.game.FINAL_SONGS
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.ui.components.RoundHeader
import com.panzane.multiblindtest.ui.components.points

/*
 * Écrans de l'hôte quand le MJ pilote la manche depuis son propre téléphone :
 * aucune chanson n'y est visible, et le joueur qui utilise l'hôte peut y buzzer.
 */

/** Le MJ choisit ses chansons sur son téléphone. */
@Composable
fun RemoteSelectionScreen(state: GameUiState, viewModel: GameViewModel) {
    val masterName = state.master?.name.orEmpty()
    HostWaitingLayout(state, viewModel) {
        Text("$masterName choisit ses chansons…", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Sur son téléphone : ${state.selection.size} / ${state.settings.songsPerRound}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Manche en cours, pilotée par le MJ depuis son téléphone. */
@Composable
fun RemotePlayingScreen(state: GameUiState, viewModel: GameViewModel) {
    val round = state.round ?: return
    val owner = state.hostOwner?.takeIf { it.id != state.master?.id }
    val buzzerName = state.playerName(round.buzzerId)
    val ownerLocked = owner != null && owner.id in round.lockedOut

    HostWaitingLayout(state, viewModel) {
        Text(
            when {
                round.isAudioLoading -> "Chargement des extraits…"
                buzzerName != null && round.buzzerId == owner?.id -> "À toi ! Donne ta réponse"
                buzzerName != null -> "$buzzerName a buzzé"
                ownerLocked -> "Mauvaise réponse : bloqué jusqu'à la prochaine chanson trouvée"
                round.isFinal -> "Finale : il faut donner les $FINAL_SONGS chansons !"
                else -> "${round.remaining.size} chansons à trouver"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (round.isAudioLoading) CircularProgressIndicator()

        if (owner != null) {
            Text("${owner.name} · ${points(owner.score)}", style = MaterialTheme.typography.titleMedium)
            val canBuzz = !round.isAudioLoading && round.buzzerId == null && !ownerLocked && round.remaining.isNotEmpty()
            val won = round.buzzerId == owner.id
            HostBuzzer(
                label = when {
                    won -> "À TOI !"
                    ownerLocked -> "BLOQUÉ"
                    else -> "BUZZ"
                },
                enabled = canBuzz,
                won = won,
                onBuzz = viewModel::hostBuzz,
            )
        }
    }
}

/** Gros bouton buzzer ; comme sur la page web, le buzz part dès que le doigt touche l'écran. */
@Composable
private fun HostBuzzer(label: String, enabled: Boolean, won: Boolean, onBuzz: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = when {
            won -> colors.tertiary
            enabled -> colors.primary
            else -> colors.surfaceVariant
        },
        contentColor = when {
            won -> colors.onTertiary
            enabled -> colors.onPrimary
            else -> colors.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBuzz()
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 40.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** Mise en page commune : en-tête, contenu centré, et secours pour reprendre la main sur l'hôte. */
@Composable
private fun HostWaitingLayout(state: GameUiState, viewModel: GameViewModel, content: @Composable () -> Unit) {
    val master = state.master ?: return
    val masterConnected = master.id in state.connectedIds
    var confirmTakeOver by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundHeader(state)
        Spacer(Modifier.weight(1f))
        content()
        Spacer(Modifier.weight(1f))

        if (!masterConnected) {
            Text(
                "Le téléphone de ${master.name} est déconnecté.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { confirmTakeOver = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reprendre la main ici")
            }
        } else {
            TextButton(onClick = { confirmTakeOver = true }) { Text("Reprendre la main ici") }
        }
    }

    if (confirmTakeOver) {
        AlertDialog(
            onDismissRequest = { confirmTakeOver = false },
            title = { Text("Reprendre la main ?") },
            text = {
                Text(
                    "Les commandes du MJ passent sur ce téléphone jusqu'à la fin de la manche. " +
                        "Les chansons choisies par ${master.name} y seront visibles."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmTakeOver = false; viewModel.takeOverMaster() }) { Text("Reprendre") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTakeOver = false }) { Text("Annuler") }
            },
        )
    }
}
