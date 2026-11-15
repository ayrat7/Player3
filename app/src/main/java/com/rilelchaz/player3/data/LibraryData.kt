package com.rilelchaz.player3.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long> = emptyList(),
    val folderId: String? = null,
)

data class Folder(
    val id: String,
    val name: String,
    val trackIds: List<Long> = emptyList(),
)

/** Правки метаданных живут в приложении — сам mp3-файл не меняется. */
data class TrackEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

data class LibraryData(
    val playlists: List<Playlist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val edits: Map<Long, TrackEdit> = emptyMap(),
    val hidden: Set<Long> = emptySet(),
    val plays: Map<Long, Int> = emptyMap(),
)

object LibraryStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "library.json")

    fun load(ctx: Context): LibraryData {
        val f = file(ctx)
        if (!f.exists()) return LibraryData()
        return try {
            parse(JSONObject(f.readText()))
        } catch (e: Exception) {
            LibraryData()
        }
    }

    fun save(ctx: Context, d: LibraryData) {
        try {
            val tmp = File(ctx.filesDir, "library.json.tmp")
            tmp.writeText(toJson(d).toString())
            tmp.renameTo(file(ctx))
        } catch (e: Exception) {
            // не критично
        }
    }

    private fun longs(a: JSONArray): List<Long> = List(a.length()) { a.getLong(it) }

    private fun optStr(o: JSONObject, k: String): String? =
        if (o.has(k) && !o.isNull(k)) o.getString(k) else null

    private fun parse(o: JSONObject): LibraryData {
        val pls = ArrayList<Playlist>()
        o.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                pls.add(Playlist(p.getString("id"), p.getString("name"), longs(p.getJSONArray("tracks")), optStr(p, "folder")))
            }
        }
        val fls = ArrayList<Folder>()
        o.optJSONArray("folders")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.getJSONObject(i)
                fls.add(Folder(f.getString("id"), f.getString("name"), longs(f.getJSONArray("tracks"))))
            }
        }
        val edits = HashMap<Long, TrackEdit>()
        o.optJSONObject("edits")?.let { e ->
            for (k in e.keys()) {
                val v = e.getJSONObject(k)
                val id = k.toLongOrNull() ?: continue
                edits[id] = TrackEdit(optStr(v, "t"), optStr(v, "a"), optStr(v, "al"))
            }
        }
        val hidden = o.optJSONArray("hidden")?.let { longs(it).toSet() } ?: emptySet()
        val plays = HashMap<Long, Int>()
        o.optJSONObject("plays")?.let { e ->
            for (k in e.keys()) {
                val id = k.toLongOrNull() ?: continue
                plays[id] = e.getInt(k)
            }
        }
        return LibraryData(pls, fls, edits, hidden, plays)
    }

    private fun toJson(d: LibraryData): JSONObject = JSONObject().apply {
        put("playlists", JSONArray().apply {
            d.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("tracks", JSONArray(p.trackIds))
                    put("folder", p.folderId ?: JSONObject.NULL)
                })
            }
        })
        put("folders", JSONArray().apply {
            d.folders.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("tracks", JSONArray(f.trackIds))
                })
            }
        })
        put("edits", JSONObject().apply {
            d.edits.forEach { (id, e) ->
                put(id.toString(), JSONObject().apply {
                    e.title?.let { put("t", it) }
                    e.artist?.let { put("a", it) }
                    e.album?.let { put("al", it) }
                })
            }
        })
        put("hidden", JSONArray(d.hidden.toList()))
        put("plays", JSONObject().apply { d.plays.forEach { (id, n) -> put(id.toString(), n) } })
    }
}

/** Свои обложки (аватарки) треков, плейлистов и папок. Ключи: t<id>, p<id>, f<id>. */
object CoverStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "covers")
    private fun path(ctx: Context, key: String) = File(dir(ctx), "$key.jpg")

    fun scan(ctx: Context): Map<String, Long> =
        dir(ctx).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.associate { it.nameWithoutExtension to it.lastModified() }
            ?: emptyMap()

    fun uri(ctx: Context, key: String, stamp: Long): Uri =
        Uri.fromFile(path(ctx, key)).buildUpon().appendQueryParameter("v", stamp.toString()).build()

    fun delete(ctx: Context, key: String) {
        path(ctx, key).delete()
    }

    fun save(ctx: Context, key: String, source: Uri): Boolean {
        return try {
            dir(ctx).mkdirs()
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
            val bmp = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false
            val side = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = if (side > 1024) Bitmap.createScaledBitmap(square, 1024, 1024, true) else square
            FileOutputStream(path(ctx, key)).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
