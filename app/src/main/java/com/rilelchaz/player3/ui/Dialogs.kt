package com.rilelchaz.player3.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rilelchaz.player3.MainViewModel
import com.rilelchaz.player3.data.Track

/** Какое окно сейчас открыто поверх приложения. */
sealed interface Dlg {
    data class Edit(val id: Long) : Dlg
    data class AddTo(val id: Long) : Dlg
    data class Delete(val id: Long) : Dlg
    /** kind: 0 плейлист, 1 папка. id == null — создание, иначе переименование. */
    data class Name(val kind: Int, val id: String?, val initial: String, val folderId: String?, val addTrack: Long?) : Dlg
    data class MoveToFolder(val playlistId: String) : Dlg
}

fun shareTrack(ctx: Context, t: Track) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, t.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(i, "Поделиться треком"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Название") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun EditTrackDialog(vm: MainViewModel, id: Long, onPickCover: (String) -> Unit, onDismiss: () -> Unit) {
    val all by vm.allMap.collectAsStateWithLifecycle()
    val t = all[id]
    if (t == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var title by remember { mutableStateOf(t.title) }
    var artist by remember { mutableStateOf(t.artist) }
    var album by remember { mutableStateOf(t.album) }
    val key = "t$id"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить трек") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackArt(t, Modifier.size(76.dp), RoundedCornerShape(16.dp), 256)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        FilledTonalButton(onClick = { onPickCover(key) }) { Text("Своя обложка") }
                        if (t.customCover) TextButton(onClick = { vm.clearCover(key) }) { Text("Убрать обложку") }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Альбом") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "В файле: ${t.origTitle} — ${t.origArtist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (t.edited || t.customCover) {
                    TextButton(onClick = { vm.resetTrack(id); onDismiss() }) { Text("Сбросить к данным из файла") }
                }
                Text(
                    "Правки видны только в Player3, сам mp3-файл не меняется.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.editTrack(id, title, artist, album); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun AddToDialog(
    vm: MainViewModel,
    id: Long,
    onNew: (isFolder: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Плейлисты", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                lib.playlists.forEach { p ->
                    val has = id in p.trackIds
                    TextButton(onClick = { vm.addToPlaylist(p.id, id); onDismiss() }, enabled = !has, modifier = Modifier.fillMaxWidth()) {
                        Text(if (has) "${p.name} ✓" else p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = { onNew(false) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Новый плейлист", modifier = Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(8.dp))
                Text("Папки", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                lib.folders.forEach { f ->
                    val has = id in f.trackIds
                    TextButton(onClick = { vm.addToFolder(f.id, id); onDismiss() }, enabled = !has, modifier = Modifier.fillMaxWidth()) {
                        Text(if (has) "${f.name} ✓" else f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = { onNew(true) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Новая папка", modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun MoveToFolderDialog(vm: MainViewModel, playlistId: String, onDismiss: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить в папку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { vm.movePlaylistToFolder(playlistId, null); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Без папки", modifier = Modifier.fillMaxWidth())
                }
                lib.folders.forEach { f ->
                    TextButton(onClick = { vm.movePlaylistToFolder(playlistId, f.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (lib.folders.isEmpty()) {
                    Text("Папок ещё нет — создайте их на вкладке «Плейлисты».", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun ConfirmDeleteDialog(track: Track, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить с телефона?") },
        text = { Text("«${track.title}» будет удалён из памяти телефона. Вернуть файл будет нельзя.\n\nЕсли нужно просто убрать трек из списка, лучше выбрать «Скрыть из библиотеки».") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun HiddenDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val hidden by vm.hiddenTracks.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скрытые треки") },
        text = {
            if (hidden.isEmpty()) {
                Text("Скрытых треков нет.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    hidden.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { vm.unhideTrack(t.id) }) { Text("Вернуть") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/**
 * Удаление файла с телефона. Android 11+ сам покажет системное подтверждение,
 * на Android 10 попросит доступ, на 8–9 нужно разрешение на запись.
 */
@Composable
fun rememberTrackDeleter(vm: MainViewModel): (Track) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Track?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val t = pending
        pending = null
        if (t != null && res.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 30) {
                vm.onFilesDeleted(listOf(t.id))
                toast("Удалено")
            } else {
                // Android 10: доступ выдан — удаляем ещё раз
                try {
                    ctx.contentResolver.delete(t.uri, null, null)
                    vm.onFilesDeleted(listOf(t.id))
                    toast("Удалено")
                } catch (e: Exception) {
                    toast("Не удалось удалить")
                }
            }
        }
    }

    fun attempt(t: Track) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(t.uri))
                pending = t
                senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                ctx.contentResolver.delete(t.uri, null, null)
                vm.onFilesDeleted(listOf(t.id))
                toast("Удалено")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                pending = t
                senderLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                toast("Нет прав на удаление этого файла")
            }
        } catch (e: Exception) {
            toast("Не удалось удалить")
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = pending
        pending = null
        if (ok && t != null) attempt(t) else toast("Без разрешения на запись удалить нельзя")
    }

    return { t ->
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = t
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            attempt(t)
        }
    }
}
