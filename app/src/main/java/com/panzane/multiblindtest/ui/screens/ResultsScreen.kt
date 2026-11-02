package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.ui.components.points

@Composable
fun ResultsScreen(state: GameUiState, viewModel: GameViewModel) {
    val ranking = state.ranking
    val bestScore = ranking.firstOrNull()?.score

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text("Résultats", style = MaterialTheme.typography.headlineMedium)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn {
                items(ranking, key = { it.id }) { player ->
                    // Les ex æquo partagent le même rang
                    val rank = ranking.indexOfFirst { it.score == player.score } + 1
                    ListItem(
                        leadingContent = { Text("$rank.", style = MaterialTheme.typography.titleLarge) },
                        headlineContent = {
                            Text(
                                player.name,
                                style = if (player.score == bestScore) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                            )
                        },
                        trailingContent = { Text(points(player.score)) },
                    )
                }
            }
        }

        Button(onClick = viewModel::startGame, modifier = Modifier.fillMaxWidth()) {
            Text("Rejouer avec les mêmes joueurs")
        }
        OutlinedButton(onClick = viewModel::newGame, modifier = Modifier.fillMaxWidth()) {
            Text("Modifier les joueurs")
        }
    }
}
