package com.rilelchaz.player3.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

object MusicRepository {

    fun load(context: Context): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val result = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol).cleanOr("Неизвестный исполнитель")
                val album = c.getString(albumCol).cleanOr("Неизвестный альбом")
                result.add(
                    Track(
                        id = id,
                        title = c.getString(titleCol).cleanOr("Без названия"),
                        artist = artist,
                        album = album,
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durCol),
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = c.getLong(dateCol),
                    )
                )
            }
        }
        return result
    }

    private fun String?.cleanOr(fallback: String): String =
        if (this.isNullOrBlank() || this == "<unknown>") fallback else this
}
