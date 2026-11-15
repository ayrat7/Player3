package com.rilelchaz.player3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rilelchaz.player3.MainViewModel
import com.rilelchaz.player3.tracksWord

/** Куда можно «зайти» из вкладок. */
sealed interface Detail {
    data class Group(val key: String) : Detail          // альбом или исполнитель
    data class Smart(val kind: Int) : Detail            // 0 все песни, 1 избранное, 2 часто слушаемые, 3 новые
    data class Pl(val id: String) : Detail              // плейлист
    data class Fo(val id: String) : Detail              // папка
}

private val smartTitles = listOf("Все песни", "Избранное", "Часто слушаемые", "Недавно добавленные")
private val smartIcons = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.Favorite, Icons.Rounded.TrendingUp, Icons.Rounded.NewReleases)

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun PlaylistsScreen(vm: MainViewModel, onOpen: (Detail) -> Unit, onDialog: (Dlg) -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val trackMap by vm.trackMap.collectAsStateWithLifecycle()

    val counts = listOf(
        tracks.size,
        tracks.count { it.id in favs },
        tracks.count { (lib.plays[it.id] ?: 0) > 0 }.coerceAtMost(50),
        tracks.size.coerceAtMost(50),
    )
    val rootPlaylists = lib.playlists.filter { p -> p.folderId == null || lib.folders.none { it.id == p.folderId } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onDialog(Dlg.Name(0, null, "", null, null)) }) {
                    Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Плейлист")
                }
                FilledTonalButton(onClick = { onDialog(Dlg.Name(1, null, "", null, null)) }) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Папка")
                }
            }
        }

        item { SectionLabel("Общие") }
        items(4, key = { "smart$it" }) { i ->
            ListRow(
                onClick = { onOpen(Detail.Smart(i)) },
                title = smartTitles[i],
                subtitle = tracksWord(counts[i]),
                leading = { IconTile(smartIcons[i]) }
            )
        }

        if (lib.folders.isNotEmpty()) {
            item { SectionLabel("Папки") }
            items(lib.folders, key = { "f" + it.id }) { f ->
                val inside = lib.playlists.count { it.folderId == f.id }
                ListRow(
                    onClick = { onOpen(Detail.Fo(f.id)) },
                    title = f.name,
                    subtitle = "$inside плейл. • ${tracksWord(f.trackIds.count { it in trackMap })}",
                    leading = { CoverFor(vm, "f${f.id}", null, Icons.Rounded.Folder, Modifier.size(52.dp), MaterialTheme.shapes.medium) }
                )
            }
        }

        item { SectionLabel("Плейлисты") }
        if (rootPlaylists.isEmpty()) {
            item {
                Text(
                    "Плейлистов пока нет. Нажмите «Плейлист» сверху.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(rootPlaylists, key = { "p" + it.id }) { p ->
            val first = p.trackIds.firstNotNullOfOrNull { trackMap[it] }
            ListRow(
                onClick = { onOpen(Detail.Pl(p.id)) },
                title = p.name,
                subtitle = tracksWord(p.trackIds.count { it in trackMap }),
                leading = { CoverFor(vm, "p${p.id}", first, Icons.Rounded.QueueMusic, Modifier.size(52.dp), MaterialTheme.shapes.medium) }
            )
        }
    }
}

/** Содержимое выбранного альбома, исполнителя, плейлиста, папки или умного списка. */
@Composable
fun DetailHost(
    d: Detail,
    vm: MainViewModel,
    actions: TrackActions,
    onBack: () -> Unit,
    onOpen: (Detail) -> Unit,
    onDialog: (Dlg) -> Unit,
    onPickCover: (String) -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val trackMap by vm.trackMap.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()

    when (d) {
        is Detail.Group -> {
            val g = (albums + artists).firstOrNull { it.key == d.key }
            if (g == null) {
                LaunchedEffect(Unit) { onBack() }
            } else {
                TrackListScreen(g.title, "${g.subtitle} • ${tracksWord(g.tracks.size)}", g.tracks, vm, actions, onBack)
            }
        }

        is Detail.Smart -> {
            val list = when (d.kind) {
                1 -> tracks.filter { it.id in favs }
                2 -> tracks.filter { (lib.plays[it.id] ?: 0) > 0 }
                    .sortedByDescending { lib.plays[it.id] ?: 0 }.take(50)
                3 -> tracks.sortedByDescending { it.dateAdded }.take(50)
                else -> tracks
            }
            TrackListScreen(smartTitles[d.kind], tracksWord(list.size), list, vm, actions, onBack)
        }

        is Detail.Pl -> {
            val pl = lib.playlists.firstOrNull { it.id == d.id }
            if (pl == null) {
                LaunchedEffect(Unit) { onBack() }
            } else {
                val list = pl.trackIds.mapNotNull { trackMap[it] }
                val key = "p${pl.id}"
                TrackListScreen(
                    title = pl.name,
                    subtitle = tracksWord(list.size),
                    tracks = list,
                    vm = vm,
                    actions = actions.copy(removeFromList = { vm.removeFromPlaylist(pl.id, it.id) }),
                    onBack = onBack,
                    cover = { CoverFor(vm, key, list.firstOrNull(), Icons.Rounded.QueueMusic, Modifier.size(56.dp), MaterialTheme.shapes.medium) },
                    menuContent = { dismiss ->
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { dismiss(); onDialog(Dlg.Name(0, pl.id, pl.name, null, null)) })
                        DropdownMenuItem(text = { Text("Своя обложка…") }, onClick = { dismiss(); onPickCover(key) })
                        if (covers.containsKey(key)) {
                            DropdownMenuItem(text = { Text("Убрать свою обложку") }, onClick = { dismiss(); vm.clearCover(key) })
                        }
                        DropdownMenuItem(text = { Text("Переместить в папку…") }, onClick = { dismiss(); onDialog(Dlg.MoveToFolder(pl.id)) })
                        DropdownMenuItem(
                            text = { Text("Удалить плейлист", color = MaterialTheme.colorScheme.error) },
                            onClick = { dismiss(); vm.deletePlaylist(pl.id); onBack() }
                        )
                    }
                )
            }
        }

        is Detail.Fo -> {
            val fo = lib.folders.firstOrNull { it.id == d.id }
            if (fo == null) {
                LaunchedEffect(Unit) { onBack() }
            } else {
                val list = fo.trackIds.mapNotNull { trackMap[it] }
                val inside = lib.playlists.filter { it.folderId == fo.id }
                val key = "f${fo.id}"
                TrackListScreen(
                    title = fo.name,
                    subtitle = "${inside.size} плейл. • ${tracksWord(list.size)}",
                    tracks = list,
                    vm = vm,
                    actions = actions.copy(removeFromList = { vm.removeFromFolder(fo.id, it.id) }),
                    onBack = onBack,
                    cover = { CoverFor(vm, key, null, Icons.Rounded.Folder, Modifier.size(56.dp), MaterialTheme.shapes.medium) },
                    menuContent = { dismiss ->
                        DropdownMenuItem(text = { Text("Новый плейлист в папке") }, onClick = { dismiss(); onDialog(Dlg.Name(0, null, "", fo.id, null)) })
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { dismiss(); onDialog(Dlg.Name(1, fo.id, fo.name, null, null)) })
                        DropdownMenuItem(text = { Text("Своя обложка…") }, onClick = { dismiss(); onPickCover(key) })
                        if (covers.containsKey(key)) {
                            DropdownMenuItem(text = { Text("Убрать свою обложку") }, onClick = { dismiss(); vm.clearCover(key) })
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить папку", color = MaterialTheme.colorScheme.error) },
                            onClick = { dismiss(); vm.deleteFolder(fo.id); onBack() }
                        )
                    },
                    topContent = {
                        items(inside, key = { "fp" + it.id }) { p ->
                            val first = p.trackIds.firstNotNullOfOrNull { trackMap[it] }
                            ListRow(
                                onClick = { onOpen(Detail.Pl(p.id)) },
                                title = p.name,
                                subtitle = "Плейлист • ${tracksWord(p.trackIds.count { it in trackMap })}",
                                leading = { CoverFor(vm, "p${p.id}", first, Icons.Rounded.QueueMusic, Modifier.size(52.dp), MaterialTheme.shapes.medium) }
                            )
                        }
                    }
                )
            }
        }
    }
}
