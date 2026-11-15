package com.rilelchaz.player3.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "player3_settings")

object Keys {
    val themeMode = intPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val amoled = booleanPreferencesKey("amoled")
    val seedHue = floatPreferencesKey("seed_hue")
    val corner = floatPreferencesKey("corner")
    val fontFamily = intPreferencesKey("font_family")
    val fontScale = floatPreferencesKey("font_scale")
    val artShape = intPreferencesKey("art_shape")
    val artSize = floatPreferencesKey("art_size")
    val artBackground = booleanPreferencesKey("art_background")
    val spinArt = booleanPreferencesKey("spin_art")
    val playButtonShape = intPreferencesKey("play_button_shape")
    val showRemaining = booleanPreferencesKey("show_remaining")
    val haptics = booleanPreferencesKey("haptics")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val density = intPreferencesKey("density")
    val showArtInList = booleanPreferencesKey("show_art_in_list")
    val miniProgress = booleanPreferencesKey("mini_progress")
    val minDuration = intPreferencesKey("min_duration")
    val sort = intPreferencesKey("sort")
    val favorites = stringSetPreferencesKey("favorites")
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = p[Keys.themeMode] ?: d.themeMode,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            amoled = p[Keys.amoled] ?: d.amoled,
            seedHue = p[Keys.seedHue] ?: d.seedHue,
            corner = p[Keys.corner] ?: d.corner,
            fontFamily = p[Keys.fontFamily] ?: d.fontFamily,
            fontScale = p[Keys.fontScale] ?: d.fontScale,
            artShape = p[Keys.artShape] ?: d.artShape,
            artSize = p[Keys.artSize] ?: d.artSize,
            artBackground = p[Keys.artBackground] ?: d.artBackground,
            spinArt = p[Keys.spinArt] ?: d.spinArt,
            playButtonShape = p[Keys.playButtonShape] ?: d.playButtonShape,
            showRemaining = p[Keys.showRemaining] ?: d.showRemaining,
            haptics = p[Keys.haptics] ?: d.haptics,
            keepScreenOn = p[Keys.keepScreenOn] ?: d.keepScreenOn,
            density = p[Keys.density] ?: d.density,
            showArtInList = p[Keys.showArtInList] ?: d.showArtInList,
            miniProgress = p[Keys.miniProgress] ?: d.miniProgress,
            minDurationSec = p[Keys.minDuration] ?: d.minDurationSec,
            sort = p[Keys.sort] ?: d.sort,
        )
    }

    val favorites: Flow<Set<Long>> = context.dataStore.data.map { p ->
        (p[Keys.favorites] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun toggleFavorite(id: Long) {
        context.dataStore.edit { p ->
            val cur = (p[Keys.favorites] ?: emptySet()).toMutableSet()
            val s = id.toString()
            if (!cur.add(s)) cur.remove(s)
            p[Keys.favorites] = cur
        }
    }
}
