package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.ui.components.RoundHeader
import com.panzane.multiblindtest.ui.components.TrackCover
import com.panzane.multiblindtest.ui.components.points

@Composable
fun RevealScreen(state: GameUiState, viewModel: GameViewModel) {
    val round = state.round ?: return

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundHeader(state)
        Text("Les réponses", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(round.songs, key = { it.track.id }) { song ->
                ListItem(
                    leadingContent = { TrackCover(song.track) },
                    headlineContent = { Text(song.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(song.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Text(
                            state.playerName(song.foundBy) ?: "Non trouvée",
                            color = if (song.isFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Scores", style = MaterialTheme.typography.titleMedium)
            }
            items(state.ranking, key = { "score-${it.id}" }) { player ->
                ListItem(
                    headlineContent = { Text(player.name) },
                    trailingContent = { Text(points(player.score)) },
                )
            }
        }

        Button(onClick = viewModel::nextRound, modifier = Modifier.fillMaxWidth()) {
            Text(
                state.nextMaster?.takeUnless { state.isLastRound }
                    ?.let { "Tour suivant · MJ : ${it.name}" }
                    ?: "Voir les résultats"
            )
        }
    }
}
