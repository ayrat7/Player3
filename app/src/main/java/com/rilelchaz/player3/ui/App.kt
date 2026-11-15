package com.rilelchaz.player3.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.rilelchaz.player3.data.MusicGroup

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
    var detail by remember { mutableStateOf<MusicGroup?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val current by vm.current.collectAsStateWithLifecycle()

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && detail != null) { detail = null }

    val titles = listOf("Треки", "Альбомы", "Исполнители", "Настройки")

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (detail == null) {
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
                        listOf(Icons.Rounded.MusicNote, Icons.Rounded.Album, Icons.Rounded.Person, Icons.Rounded.Settings)
                            .forEachIndexed { i, icon ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i; detail = null },
                                    icon = { Icon(icon, titles[i]) },
                                    label = { Text(titles[i]) }
                                )
                            }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val d = detail
                if (d != null && tab in 1..2) {
                    DetailScreen(d, vm) { detail = null }
                } else {
                    when (tab) {
                        0 -> SongsScreen(vm)
                        1 -> AlbumsScreen(vm) { detail = it }
                        2 -> ArtistsScreen(vm) { detail = it }
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
}
