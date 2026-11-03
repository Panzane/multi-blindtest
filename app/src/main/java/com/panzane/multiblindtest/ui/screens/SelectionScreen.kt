package com.panzane.multiblindtest.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panzane.multiblindtest.data.deezer.Track
import com.panzane.multiblindtest.game.GameUiState
import com.panzane.multiblindtest.game.GameViewModel
import com.panzane.multiblindtest.ui.components.RoundHeader
import com.panzane.multiblindtest.ui.components.TrackCover

@Composable
fun SelectionScreen(state: GameUiState, viewModel: GameViewModel) {
    val selectedIds = state.selection.map { it.id }.toSet()
    val songsPerRound = state.settings.songsPerRound
    val isFull = state.selection.size >= songsPerRound

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundHeader(state)
        Text(
            "${state.master?.name.orEmpty()}, choisissez $songsPerRound chansons en cachette !",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Titre, artiste ou lien de playlist") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { viewModel.search() }) {
                    Icon(Icons.Default.Search, contentDescription = "Rechercher")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn {
                if (state.selection.isNotEmpty()) {
                    item { SectionTitle("Votre sélection (${state.selection.size}/$songsPerRound)") }
                    items(state.selection, key = { "selected-${it.id}" }) { track ->
                        TrackItem(track, selected = true, enabled = true) { viewModel.toggleSelection(track) }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                item { SectionTitle(if (state.query.isBlank()) "Titres du moment" else "Résultats") }
                items(state.searchResults.filterNot { it.id in selectedIds }, key = { it.id }) { track ->
                    TrackItem(track, selected = false, enabled = !isFull) { viewModel.toggleSelection(track) }
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.searchResults.isEmpty()) {
                Text("Aucun titre trouvé.", modifier = Modifier.align(Alignment.Center))
            }
        }

        Button(onClick = viewModel::launchRound, enabled = isFull, modifier = Modifier.fillMaxWidth()) {
            Text(if (isFull) "Lancer les $songsPerRound chansons" else "Encore ${songsPerRound - state.selection.size} chanson(s) à choisir")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun TrackItem(track: Track, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = { TrackCover(track) },
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                if (selected) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                contentDescription = if (selected) "Retirer" else "Ajouter",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
