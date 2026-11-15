package com.rilelchaz.player3.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rilelchaz.player3.MainViewModel
import com.rilelchaz.player3.data.Keys
import com.rilelchaz.player3.data.MusicGroup
import com.rilelchaz.player3.data.Track

@Composable
fun SongsScreen(vm: MainViewModel, actions: TrackActions) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val shown = remember(tracks, query, onlyFav, favs) {
        tracks.filter { t ->
            (!onlyFav || t.id in favs) &&
                (query.isBlank() ||
                    t.title.contains(query, true) ||
                    t.artist.contains(query, true) ||
                    t.album.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Поиск по трекам, исполнителям, альбомам") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { vm.playShuffled(shown) }, enabled = shown.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Перемешать")
                    }
                    FilterChip(
                        selected = onlyFav,
                        onClick = { onlyFav = !onlyFav },
                        label = { Text("Избранное") }
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Сортировка") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("По названию", "По исполнителю", "Сначала новые", "По длительности")
                                .forEachIndexed { i, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        trailingIcon = {
                                            if (s.sort == i) Icon(Icons.Rounded.Check, null)
                                        },
                                        onClick = { sortMenu = false; vm.setPref(Keys.sort, i) }
                                    )
                                }
                        }
                    }
                }
            }
            if (shown.isEmpty() && !loading) {
                item {
                    Text(
                        if (tracks.isEmpty()) "Музыка не найдена.\nПоложите mp3 в память телефона и нажмите «обновить» сверху."
                        else "Ничего не найдено",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(shown, key = { _, t -> t.id }) { index, t ->
                TrackRow(
                    track = t,
                    isCurrent = current?.id == t.id,
                    isPlaying = playing,
                    s = s,
                    favorite = t.id in favs,
                    actions = actions,
                    onClick = { vm.play(shown, index) },
                )
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: MainViewModel, onOpen: (MusicGroup) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    if (albums.isEmpty()) {
        EmptyHint("Альбомов пока нет")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.key }) { g ->
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(g) }
                    .padding(bottom = 4.dp)
            ) {
                TrackArt(
                    g.tracks.first(),
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    artShape(if (s.artShape == 1) 0 else s.artShape, s.corner * 0.6f),
                    sizePx = 400
                )
                Text(
                    g.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
                Text(
                    g.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: MainViewModel, onOpen: (MusicGroup) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    if (artists.isEmpty()) {
        EmptyHint("Исполнителей пока нет")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists.size, key = { artists[it].key }) { i ->
            val g = artists[i]
            ListRow(
                onClick = { onOpen(g) },
                title = g.title,
                subtitle = g.subtitle,
                leading = { TrackArt(g.tracks.first(), Modifier.size(52.dp), CircleShape) }
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Универсальный экран со списком треков: альбом, исполнитель, плейлист, папка, умный список.
 * topContent — доп. элементы сверху (например, плейлисты внутри папки).
 */
@Composable
fun TrackListScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    vm: MainViewModel,
    actions: TrackActions,
    onBack: () -> Unit,
    cover: (@Composable () -> Unit)? = null,
    menuContent: (@Composable androidx.compose.foundation.layout.ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    topContent: (LazyListScope.() -> Unit)? = null,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                if (cover != null) {
                    cover()
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (menuContent != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Меню") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            menuContent(this) { menu = false }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Играть")
                }
                FilledTonalButton(onClick = { vm.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Перемешать")
                }
            }
        }
        topContent?.invoke(this)
        if (tracks.isEmpty()) {
            item {
                Text(
                    "Здесь пока нет треков.\nДобавляйте их через меню «⋮» → «В плейлист или папку…»",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            TrackRow(
                track = t,
                isCurrent = current?.id == t.id,
                isPlaying = playing,
                s = s,
                favorite = t.id in favs,
                actions = actions,
                onClick = { vm.play(tracks, index) },
            )
        }
    }
}
