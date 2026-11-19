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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Sélection complète : on range le clavier pour laisser voir le bouton de lancement
    LaunchedEffect(isFull) {
        if (isFull) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

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
            label = { Text("Titre, artiste ou lien de playlist Deezer") },
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
                if (state.searchResults.isNotEmpty()) item { SectionTitle("Résultats") }
                items(state.searchResults.filterNot { it.id in selectedIds }, key = { it.id }) { track ->
                    TrackItem(track, selected = false, enabled = !isFull) { viewModel.toggleSelection(track) }
                }
                // Messages d'aide placés dans la liste (et non par-dessus) pour ne pas
                // masquer les titres déjà sélectionnés ; centrés tant que la liste est vide.
                if (!state.isLoading && state.searchResults.isEmpty()) {
                    item(key = "empty-hint") {
                        Box(
                            if (state.selection.isEmpty()) Modifier.fillParentMaxSize()
                            else Modifier.fillParentMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.query.isNotBlank()) {
                                Text(
                                    "Aucun titre trouvé.",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            } else {
                                SearchHint()
                            }
                        }
                    }
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Button(onClick = viewModel::launchRound, enabled = isFull, modifier = Modifier.fillMaxWidth()) {
            Text(if (isFull) "Lancer les $songsPerRound chansons" else "Encore ${songsPerRound - state.selection.size} chanson(s) à choisir")
        }
    }
}

@Composable
private fun SearchHint() {
    Column(
        Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Recherchez un titre, un artiste ou collez le lien d'une playlist Deezer publique.",
            textAlign = TextAlign.Center,
        )
        Text(
            "Exemples acceptés :\nhttps://www.deezer.com/fr/playlist/1234567890\n" +
                "ou le numéro seul : 1234567890",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
