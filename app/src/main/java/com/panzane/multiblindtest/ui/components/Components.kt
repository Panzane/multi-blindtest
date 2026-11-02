package com.panzane.multiblindtest.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panzane.multiblindtest.data.deezer.Track
import com.panzane.multiblindtest.game.GameUiState

@Composable
fun TrackCover(track: Track, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = track.coverUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)),
    )
}

/** En-tête commun aux écrans d'une manche : numéro du tour et maître du jeu. */
@Composable
fun RoundHeader(state: GameUiState) {
    Text(
        "Tour ${state.roundIndex + 1} / ${state.totalRounds} · MJ : ${state.master?.name.orEmpty()}",
        style = MaterialTheme.typography.titleLarge,
    )
}

fun points(score: Int): String = "$score pt${if (score > 1) "s" else ""}"
