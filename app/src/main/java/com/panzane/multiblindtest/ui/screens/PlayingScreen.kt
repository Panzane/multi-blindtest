package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panzane.multiblindtest.game.FINAL_SONGS
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.game.Player
import com.panzane.multiblindtest.game.Song
import com.panzane.multiblindtest.ui.components.RoundHeader
import com.panzane.multiblindtest.ui.components.TrackCover

@Composable
fun PlayingScreen(state: GameUiState, viewModel: GameViewModel) {
    val round = state.round ?: return
    val isPlaying by viewModel.audio.isPlaying.collectAsStateWithLifecycle()
    // Chanson pour laquelle le MJ désigne qui l'a trouvée (ou annule)
    var pendingSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFinalDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundHeader(state)

        Text(
            when {
                round.isAudioLoading -> "Chargement des extraits…"
                round.isFinal -> "Plus que $FINAL_SONGS chansons : il faut donner les deux !"
                else -> "${round.remaining.size} chansons en même temps. Touchez une chanson quand elle est trouvée."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        if (round.isAudioLoading) {
            CircularProgressIndicator()
        } else {
            FilledIconButton(onClick = viewModel::togglePause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Reprendre",
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(round.songs, key = { it.track.id }) { song ->
                SongItem(
                    song = song,
                    foundByName = state.playerName(song.foundBy),
                    // En finale, les deux dernières se valident ensemble
                    enabled = !round.isAudioLoading && (song.isFound || !round.isFinal),
                    onClick = { viewModel.pauseMusic(); pendingSongId = song.track.id },
                )
            }
        }

        if (round.isFinal) {
            Button(
                onClick = { viewModel.pauseMusic(); showFinalDialog = true },
                enabled = !round.isAudioLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Qui a donné les $FINAL_SONGS chansons ?")
            }
        }
    }

    round.songs.firstOrNull { it.track.id == pendingSongId }?.let { song ->
        val id = song.track.id
        if (song.isFound) {
            AlertDialog(
                onDismissRequest = { pendingSongId = null },
                title = { Text("« ${song.track.title} »") },
                text = { Text("Trouvée par ${state.playerName(song.foundBy)}. Annuler ?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.unmarkFound(id); pendingSongId = null }) { Text("Annuler la réponse") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSongId = null }) { Text("Fermer") }
                },
            )
        } else {
            PlayerPickerDialog(
                title = "Qui a trouvé « ${song.track.title} » ?",
                players = state.guessers,
                onPick = { player -> viewModel.markFound(id, player.id); pendingSongId = null },
                onDismiss = { pendingSongId = null },
            )
        }
    }

    if (showFinalDialog) {
        PlayerPickerDialog(
            title = "Qui a donné les $FINAL_SONGS dernières chansons ?",
            players = state.guessers,
            onPick = { player -> showFinalDialog = false; viewModel.resolveFinal(player.id) },
            onDismiss = { showFinalDialog = false },
            noOneLabel = "Personne",
            onNoOne = { showFinalDialog = false; viewModel.resolveFinal(null) },
        )
    }
}

@Composable
private fun SongItem(song: Song, foundByName: String?, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = { TrackCover(song.track) },
        headlineContent = { Text(song.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (song.isFound) "${song.track.artist} · trouvée par $foundByName" else song.track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (song.isFound) Icon(Icons.Default.CheckCircle, contentDescription = "Trouvée", tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (song.isFound) 0.5f else 1f),
    )
}

@Composable
private fun PlayerPickerDialog(
    title: String,
    players: List<Player>,
    onPick: (Player) -> Unit,
    onDismiss: () -> Unit,
    noOneLabel: String? = null,
    onNoOne: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(players, key = { it.id }) { player ->
                    ListItem(
                        headlineContent = { Text(player.name) },
                        modifier = Modifier.clickable { onPick(player) },
                    )
                }
            }
        },
        confirmButton = {
            if (noOneLabel != null) TextButton(onClick = onNoOne) { Text(noOneLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Retour") }
        },
    )
}
