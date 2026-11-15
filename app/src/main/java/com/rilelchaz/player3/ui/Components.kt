package com.rilelchaz.player3.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rilelchaz.player3.data.AppSettings
import com.rilelchaz.player3.data.ArtLoader
import com.rilelchaz.player3.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun artShape(kind: Int, corner: Float): Shape = when (kind) {
    1 -> CircleShape
    2 -> RectangleShape
    3 -> CutCornerShape((28 * corner.coerceAtLeast(0.5f)).dp)
    else -> RoundedCornerShape((28 * corner).dp)
}

@Composable
fun TrackArt(
    track: Track?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    sizePx: Int = 256,
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(
        initialValue = track?.let { ArtLoader.peek(it.uri, sizePx) },
        key1 = track?.uri,
        key2 = sizePx
    ) {
        value = if (track == null) null else withContext(Dispatchers.IO) {
            ArtLoader.load(context, track.uri, sizePx)
        }
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(0.45f)
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    s: AppSettings,
    favorite: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onToggleFav: () -> Unit,
) {
    val (artSize, vPad) = when (s.density) {
        0 -> 40.dp to 3.dp
        2 -> 64.dp to 10.dp
        else -> 52.dp to 6.dp
    }
    var menu by remember { mutableStateOf(false) }
    val bg = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f) else Color.Transparent
    val listShape = if (s.artShape == 1) CircleShape else RoundedCornerShape((10 * s.corner).dp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = vPad, bottom = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.showArtInList) {
            TrackArt(track, Modifier.size(artSize), listShape)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} • ${formatTime(track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent && isPlaying) {
            Icon(
                Icons.Rounded.GraphicEq, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).size(20.dp)
            )
        }
        if (favorite) {
            Icon(
                Icons.Rounded.Favorite, null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp)
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Меню") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Играть следующим") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = { menu = false; onAddQueue() })
                DropdownMenuItem(
                    text = { Text(if (favorite) "Убрать из избранного" else "В избранное") },
                    onClick = { menu = false; onToggleFav() }
                )
            }
        }
    }
}
