package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.game.MAX_PLAYERS
import com.panzane.multiblindtest.game.MIN_PLAYERS
import com.panzane.multiblindtest.game.SONGS_PER_ROUND_RANGE
import com.panzane.multiblindtest.game.TURNS_PER_PLAYER_RANGE

@Composable
fun PlayersScreen(state: GameUiState, viewModel: GameViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    val canAdd = name.isNotBlank() && state.players.size < MAX_PLAYERS
    val submit = {
        if (canAdd) {
            viewModel.addPlayer(name)
            name = ""
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Multi Blindtest", style = MaterialTheme.typography.headlineMedium)
        Text("Ajoutez les joueurs (${state.players.size}/$MAX_PLAYERS)", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("Nom du joueur") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = submit, enabled = canAdd) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Ajouter")
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(state.players, key = { it.id }) { player ->
                ListItem(
                    headlineContent = { Text(player.name) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removePlayer(player.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Retirer ${player.name}")
                        }
                    },
                )
            }
        }

        HorizontalDivider()
        Text("Options", style = MaterialTheme.typography.titleMedium)
        val settings = state.settings
        NumberSetting(
            label = "Chansons par manche",
            value = settings.songsPerRound,
            range = SONGS_PER_ROUND_RANGE,
            onValueChange = viewModel::setSongsPerRound,
        )
        NumberSetting(
            label = "Tours par joueur",
            value = settings.turnsPerPlayer,
            range = TURNS_PER_PLAYER_RANGE,
            onValueChange = viewModel::setTurnsPerPlayer,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ordre des MJ aléatoire", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = settings.randomMasterOrder, onCheckedChange = viewModel::setRandomMasterOrder)
        }

        Text(
            "Chaque joueur sera maître du jeu à tour de rôle" +
                (if (settings.randomMasterOrder) ", dans un ordre tiré au sort" else "") +
                (if (settings.turnsPerPlayer > 1) " (${settings.turnsPerPlayer} fois chacun)" else "") +
                " : il choisit ${settings.songsPerRound} chansons jouées toutes en même temps, " +
                "et les autres doivent les reconnaître.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.padding(4.dp))
        Button(
            onClick = viewModel::startGame,
            enabled = state.players.size >= MIN_PLAYERS,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.players.size >= MIN_PLAYERS) "Commencer la partie"
                else "Au moins $MIN_PLAYERS joueurs requis"
            )
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onValueChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = "Diminuer : $label")
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        IconButton(onClick = { onValueChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = "Augmenter : $label")
        }
    }
}
