package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.game.MAX_PLAYERS
import com.panzane.multiblindtest.game.MIN_PLAYERS
import com.panzane.multiblindtest.game.SONGS_PER_ROUND

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

        Text(
            "Chaque joueur sera maître du jeu à tour de rôle : il choisit $SONGS_PER_ROUND chansons " +
                "jouées toutes en même temps, et les autres doivent les reconnaître.",
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
