package com.rilelchaz.player3.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rilelchaz.player3.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val ctx = LocalContext.current
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            vm.refresh()
            askNotifications()
        }
    }

    LaunchedEffect(Unit) {
        if (granted) {
            vm.refresh()
            askNotifications()
        } else {
            audioLauncher.launch(audioPermission)
        }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            Text("Player3 нужен доступ к музыке на устройстве", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Button(onClick = { audioLauncher.launch(audioPermission) }, modifier = Modifier.padding(top = 20.dp)) {
                Text("Разрешить")
            }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Detail>() }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    val current by vm.current.collectAsStateWithLifecycle()
    val allMap by vm.allMap.collectAsStateWithLifecycle()

    // выбор своей картинки (обложки) из галереи
    var coverKey by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val k = coverKey
        coverKey = null
        if (uri != null && k != null) vm.setCover(k, uri)
    }
    val pickCover: (String) -> Unit = { key ->
        coverKey = key
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val deleter = rememberTrackDeleter(vm)

    val actions = remember(vm) {
        TrackActions(
            playNext = { vm.playNext(it) },
            addQueue = { vm.addToQueue(it) },
            toggleFav = { vm.toggleFavorite(it.id) },
            addTo = { dialog = Dlg.AddTo(it.id) },
            edit = { dialog = Dlg.Edit(it.id) },
            hide = {
                vm.hideTrack(it.id)
                Toast.makeText(ctx, "Скрыто. Вернуть: Настройки → Скрытые треки", Toast.LENGTH_LONG).show()
            },
            delete = { dialog = Dlg.Delete(it.id) },
            share = { shareTrack(ctx, it) },
        )
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val titles = listOf("Треки", "Альбомы", "Исполнители", "Плейлисты", "Настройки")
    val navLabels = listOf("Треки", "Альбомы", "Артисты", "Списки", "Ещё")
    val icons = listOf(Icons.Rounded.MusicNote, Icons.Rounded.Album, Icons.Rounded.Person, Icons.Rounded.QueueMusic, Icons.Rounded.Settings)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (stack.isEmpty()) {
                    CenterAlignedTopAppBar(
                        title = { Text(titles[tab]) },
                        actions = {
                            if (tab == 0) IconButton(onClick = { vm.refresh() }) { Icon(Icons.Rounded.Refresh, "Обновить") }
                        }
                    )
                }
            },
            bottomBar = {
                Column {
                    MiniPlayer(vm) { showPlayer = true }
                    NavigationBar {
                        icons.forEachIndexed { i, icon ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i; stack.clear() },
                                icon = { Icon(icon, titles[i]) },
                                label = { Text(navLabels[i], maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val top = stack.lastOrNull()
                if (top != null) {
                    DetailHost(
                        d = top,
                        vm = vm,
                        actions = actions,
                        onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                        onOpen = { stack.add(it) },
                        onDialog = { dialog = it },
                        onPickCover = pickCover,
                    )
                } else {
                    when (tab) {
                        0 -> SongsScreen(vm, actions)
                        1 -> AlbumsScreen(vm) { stack.add(Detail.Group(it.key)) }
                        2 -> ArtistsScreen(vm) { stack.add(Detail.Group(it.key)) }
                        3 -> PlaylistsScreen(vm, onOpen = { stack.add(it) }, onDialog = { dialog = it })
                        else -> SettingsScreen(vm)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(vm) { showPlayer = false }
        }
    }

    // ----- окна -----
    when (val d = dialog) {
        null -> {}
        is Dlg.Edit -> EditTrackDialog(vm, d.id, pickCover) { dialog = null }
        is Dlg.AddTo -> AddToDialog(vm, d.id, onNew = { isFolder ->
            dialog = Dlg.Name(if (isFolder) 1 else 0, null, "", null, d.id)
        }) { dialog = null }
        is Dlg.MoveToFolder -> MoveToFolderDialog(vm, d.playlistId) { dialog = null }
        is Dlg.Delete -> {
            val t = allMap[d.id]
            if (t == null) {
                LaunchedEffect(Unit) { dialog = null }
            } else {
                ConfirmDeleteDialog(t, onConfirm = { dialog = null; deleter(t) }, onDismiss = { dialog = null })
            }
        }
        is Dlg.Name -> {
            val title = when {
                d.kind == 0 && d.id == null -> "Новый плейлист"
                d.kind == 0 -> "Переименовать плейлист"
                d.id == null -> "Новая папка"
                else -> "Переименовать папку"
            }
            NameDialog(title, d.initial, onDismiss = { dialog = null }) { name ->
                dialog = null
                val id = d.id
                if (d.kind == 0) {
                    if (id == null) {
                        val newId = vm.createPlaylist(name, d.folderId)
                        d.addTrack?.let { vm.addToPlaylist(newId, it) }
                    } else vm.renamePlaylist(id, name)
                } else {
                    if (id == null) {
                        val newId = vm.createFolder(name)
                        d.addTrack?.let { vm.addToFolder(newId, it) }
                    } else vm.renameFolder(id, name)
                }
            }
        }
    }
}
