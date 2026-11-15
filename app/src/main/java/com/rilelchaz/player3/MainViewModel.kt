package com.rilelchaz.player3

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rilelchaz.player3.data.AppSettings
import com.rilelchaz.player3.data.CoverStore
import com.rilelchaz.player3.data.Folder
import com.rilelchaz.player3.data.LibraryData
import com.rilelchaz.player3.data.LibraryStore
import com.rilelchaz.player3.data.MusicGroup
import com.rilelchaz.player3.data.MusicRepository
import com.rilelchaz.player3.data.Playlist
import com.rilelchaz.player3.data.SettingsRepository
import com.rilelchaz.player3.data.Track
import com.rilelchaz.player3.data.TrackEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application = app
    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val favorites: StateFlow<Set<Long>> =
        repo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun <T> setPref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.set(key, value) }
    }

    fun resetSettings() {
        viewModelScope.launch { repo.resetAppearance() }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // ---------- Плейлисты, папки, правки (хранятся в приложении) ----------
    val lib = MutableStateFlow(LibraryData())
    val covers = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var saveJob: Job? = null

    private fun updateLib(f: (LibraryData) -> LibraryData) {
        lib.update { f(it) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            withContext(Dispatchers.IO) { LibraryStore.save(ctx, lib.value) }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString().take(8)

    fun createPlaylist(name: String, folderId: String? = null): String {
        val id = newId()
        updateLib { it.copy(playlists = it.playlists + Playlist(id, name.trim(), emptyList(), folderId)) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = updateLib { l ->
        l.copy(playlists = l.playlists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePlaylist(id: String) {
        updateLib { l -> l.copy(playlists = l.playlists.filter { it.id != id }) }
        clearCover("p$id")
    }

    fun addToPlaylist(pid: String, trackId: Long) {
        updateLib { l ->
            l.copy(playlists = l.playlists.map {
                if (it.id == pid && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
            })
        }
        toast("Добавлено в плейлист")
    }

    fun removeFromPlaylist(pid: String, trackId: Long) = updateLib { l ->
        l.copy(playlists = l.playlists.map {
            if (it.id == pid) it.copy(trackIds = it.trackIds.filter { id -> id != trackId }) else it
        })
    }

    fun movePlaylistToFolder(pid: String, folderId: String?) = updateLib { l ->
        l.copy(playlists = l.playlists.map { if (it.id == pid) it.copy(folderId = folderId) else it })
    }

    fun createFolder(name: String): String {
        val id = newId()
        updateLib { it.copy(folders = it.folders + Folder(id, name.trim(), emptyList())) }
        return id
    }

    fun renameFolder(id: String, name: String) = updateLib { l ->
        l.copy(folders = l.folders.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteFolder(id: String) {
        // плейлисты из папки переезжают в корень
        updateLib { l ->
            l.copy(
                folders = l.folders.filter { it.id != id },
                playlists = l.playlists.map { if (it.folderId == id) it.copy(folderId = null) else it }
            )
        }
        clearCover("f$id")
    }

    fun addToFolder(fid: String, trackId: Long) {
        updateLib { l ->
            l.copy(folders = l.folders.map {
                if (it.id == fid && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
            })
        }
        toast("Добавлено в папку")
    }

    fun removeFromFolder(fid: String, trackId: Long) = updateLib { l ->
        l.copy(folders = l.folders.map {
            if (it.id == fid) it.copy(trackIds = it.trackIds.filter { id -> id != trackId }) else it
        })
    }

    fun hideTrack(id: Long) = updateLib { it.copy(hidden = it.hidden + id) }
    fun unhideTrack(id: Long) = updateLib { it.copy(hidden = it.hidden - id) }

    fun editTrack(id: Long, title: String, artist: String, album: String) {
        val raw = rawTracks.value.firstOrNull { it.id == id } ?: return
        val e = TrackEdit(
            title = title.trim().takeIf { it.isNotEmpty() && it != raw.title },
            artist = artist.trim().takeIf { it.isNotEmpty() && it != raw.artist },
            album = album.trim().takeIf { it.isNotEmpty() && it != raw.album },
        )
        updateLib { l ->
            if (e.title == null && e.artist == null && e.album == null) l.copy(edits = l.edits - id)
            else l.copy(edits = l.edits + (id to e))
        }
    }

    /** Возвращает название, исполнителя, альбом и обложку к тому, что записано в файле. */
    fun resetTrack(id: Long) {
        updateLib { it.copy(edits = it.edits - id) }
        clearCover("t$id")
    }

    fun setCover(key: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { CoverStore.save(ctx, key, uri) }
            covers.value = withContext(Dispatchers.IO) { CoverStore.scan(ctx) }
            if (!ok) toast("Не удалось загрузить картинку")
        }
    }

    fun clearCover(key: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CoverStore.delete(ctx, key) }
            covers.value = withContext(Dispatchers.IO) { CoverStore.scan(ctx) }
        }
    }

    /** Вызывается после того, как файлы реально удалены с телефона. */
    fun onFilesDeleted(ids: List<Long>) {
        val set = ids.toSet()
        rawTracks.value = rawTracks.value.filter { it.id !in set }
        updateLib { l ->
            l.copy(
                playlists = l.playlists.map { it.copy(trackIds = it.trackIds.filter { id -> id !in set }) },
                folders = l.folders.map { it.copy(trackIds = it.trackIds.filter { id -> id !in set }) },
                edits = l.edits - set,
                hidden = l.hidden - set,
                plays = l.plays - set,
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { CoverStore.delete(ctx, "t$it") } }
            covers.value = withContext(Dispatchers.IO) { CoverStore.scan(ctx) }
        }
        refresh()
    }

    // ---------- Библиотека ----------
    private val rawTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Все треки с применёнными правками (включая скрытые). */
    private val displayAll: StateFlow<List<Track>> = combine(rawTracks, lib, covers) { raw, l, cv ->
        raw.map { t ->
            val e = l.edits[t.id]
            val key = "t${t.id}"
            val stamp = cv[key]
            t.copy(
                title = e?.title ?: t.title,
                artist = e?.artist ?: t.artist,
                album = e?.album ?: t.album,
                artUri = if (stamp != null) CoverStore.uri(ctx, key, stamp) else t.uri,
                customCover = stamp != null,
                edited = e != null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allMap: StateFlow<Map<Long, Track>> =
        displayAll.map { l -> l.associateBy { it.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val hiddenTracks: StateFlow<List<Track>> = combine(displayAll, lib) { all, l ->
        all.filter { it.id in l.hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** «Все песни»: видимые треки (не скрытые, не короче порога). */
    val tracks: StateFlow<List<Track>> = combine(displayAll, lib, settings) { all, l, s ->
        sortTracks(all.filter { it.id !in l.hidden && it.durationMs >= s.minDurationSec * 1000L }, s.sort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trackMap: StateFlow<Map<Long, Track>> =
        tracks.map { l -> l.associateBy { it.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val albums: StateFlow<List<MusicGroup>> = tracks.map { list ->
        list.groupBy { it.albumId to it.album }.map { (k, ts) ->
            MusicGroup("album_${k.first}_${k.second}", k.second, ts.first().artist, ts)
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<MusicGroup>> = tracks.map { list ->
        list.groupBy { it.artist }.map { (name, ts) ->
            MusicGroup("artist_$name", name, tracksWord(ts.size), ts)
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun sortTracks(list: List<Track>, sort: Int): List<Track> = when (sort) {
        1 -> list.sortedBy { it.artist.lowercase() }
        2 -> list.sortedByDescending { it.dateAdded }
        3 -> list.sortedByDescending { it.durationMs }
        else -> list.sortedBy { it.title.lowercase() }
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    MusicRepository.load(ctx)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            rawTracks.value = list
            _loading.value = false
        }
    }

    // ---------- Состояние плеера ----------
    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _pitch = MutableStateFlow(1f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    private val _sleepLeft = MutableStateFlow(0)
    val sleepLeftSec: StateFlow<Int> = _sleepLeft.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var lastCounted: Long = -1L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromController(events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    init {
        viewModelScope.launch {
            val (l, c) = withContext(Dispatchers.IO) { LibraryStore.load(ctx) to CoverStore.scan(ctx) }
            lib.value = l
            covers.value = c
        }

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                syncFromController(true)
                sendOutput()
            } catch (e: Exception) {
                // сервис недоступен — просто работаем без плеера
            }
        }, ContextCompat.getMainExecutor(app))

        // когда меняются правки/библиотека — обновляем названия в плеере и очереди
        viewModelScope.launch { allMap.collect { syncFromController(true) } }

        // настройки выхода звука -> сервис
        viewModelScope.launch {
            settings.map { it.outputMode to it.pauseOnUnplug }.distinctUntilChanged().collect { sendOutput() }
        }

        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && _isPlaying.value) _position.value = c.currentPosition.coerceAtLeast(0L)
                delay(if (_isPlaying.value) 250L else 1000L)
            }
        }
    }

    private fun sendOutput() {
        val c = controller ?: return
        val s = settings.value
        val args = Bundle().apply {
            putInt(PlaybackService.EXTRA_MODE, s.outputMode)
            putBoolean(PlaybackService.EXTRA_NOISY, s.pauseOnUnplug)
        }
        c.sendCustomCommand(SessionCommand(PlaybackService.CMD_OUTPUT, Bundle.EMPTY), args)
    }

    private fun itemToTrack(mi: MediaItem, index: Int): Track {
        val id = mi.mediaId.toLongOrNull()
        if (id != null) allMap.value[id]?.let { return it }
        return Track(
            id = id ?: (-index - 1L),
            title = mi.mediaMetadata.title?.toString() ?: "—",
            artist = mi.mediaMetadata.artist?.toString() ?: "",
            album = mi.mediaMetadata.albumTitle?.toString() ?: "",
            albumId = 0L,
            durationMs = 0L,
            uri = mi.localConfiguration?.uri ?: Uri.EMPTY,
            dateAdded = 0L,
        )
    }

    private fun syncFromController(rebuildQueue: Boolean) {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        _shuffle.value = c.shuffleModeEnabled
        _repeat.value = c.repeatMode
        _speed.value = c.playbackParameters.speed
        _pitch.value = c.playbackParameters.pitch
        _duration.value = if (c.duration == C.TIME_UNSET) 0L else c.duration
        _position.value = c.currentPosition.coerceAtLeast(0L)
        val mi = c.currentMediaItem
        _current.value = mi?.let { itemToTrack(it, c.currentMediaItemIndex) }

        // счётчик прослушиваний для «Часто слушаемых»
        val id = mi?.mediaId?.toLongOrNull()
        if (id != null && c.isPlaying && id != lastCounted) {
            lastCounted = id
            updateLib { it.copy(plays = it.plays + (id to ((it.plays[id] ?: 0) + 1))) }
        }

        if (rebuildQueue) {
            val list = ArrayList<Track>(c.mediaItemCount)
            for (i in 0 until c.mediaItemCount) list.add(itemToTrack(c.getMediaItemAt(i), i))
            _queue.value = list
        }
    }

    // ---------- Управление ----------
    fun play(list: List<Track>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        play(list, list.indices.random())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _position.value = ms
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(track), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(track), 0) else c.addMediaItem(track.toMediaItem())
    }

    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun setSpeedPitch(speed: Float, pitch: Float) {
        controller?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    // ---------- Таймер сна ----------
    private var sleepJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepLeft.value = 0
            return
        }
        sleepJob = viewModelScope.launch {
            var left = minutes * 60
            while (left > 0) {
                _sleepLeft.value = left
                delay(1000)
                left--
            }
            _sleepLeft.value = 0
            controller?.pause()
        }
    }

    // ---------- Эквалайзер ----------
    fun openEqualizer(context: Context) {
        val c = controller ?: return
        val future = c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SESSION_ID, Bundle.EMPTY), Bundle.EMPTY)
        future.addListener({
            val id = try {
                future.get().extras.getInt(PlaybackService.EXTRA_SESSION_ID, 0)
            } catch (e: Exception) {
                0
            }
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "На этом устройстве нет системного эквалайзера", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

fun tracksWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m100 in 11..14 -> "треков"
        m10 == 1 -> "трек"
        m10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$n $w"
}
