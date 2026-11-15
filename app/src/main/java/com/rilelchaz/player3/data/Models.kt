package com.rilelchaz.player3.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val dateAdded: Long,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(uri) // обложку достаёт наш BitmapLoader
                .build()
        )
        .build()
}

data class MusicGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
)

/** Все настройки внешнего вида и поведения. */
data class AppSettings(
    // Тема
    val themeMode: Int = 0,            // 0 система, 1 светлая, 2 тёмная
    val dynamicColor: Boolean = true,  // Material You (Android 12+)
    val amoled: Boolean = false,
    val seedHue: Float = 265f,
    // Форма и шрифт
    val corner: Float = 1f,            // множитель скругления
    val fontFamily: Int = 0,           // 0 стандарт, 1 сериф, 2 моно, 3 рукописный
    val fontScale: Float = 1f,
    // Плеер
    val artShape: Int = 0,             // 0 скруглённый, 1 круг, 2 квадрат, 3 срезанный
    val artSize: Float = 0.85f,
    val artBackground: Boolean = true,
    val spinArt: Boolean = false,
    val playButtonShape: Int = 0,      // 0 морфинг круг<->скругл., 1 скруглённый, 2 таблетка
    val showRemaining: Boolean = false,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    // Список
    val density: Int = 1,              // 0 компактно, 1 обычно, 2 крупно
    val showArtInList: Boolean = true,
    val miniProgress: Boolean = true,
    // Библиотека
    val minDurationSec: Int = 30,
    val sort: Int = 0,                 // 0 название, 1 исполнитель, 2 новые, 3 длина
)
