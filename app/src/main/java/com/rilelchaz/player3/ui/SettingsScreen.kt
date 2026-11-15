@file:OptIn(ExperimentalLayoutApi::class)

package com.rilelchaz.player3.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rilelchaz.player3.MainViewModel
import com.rilelchaz.player3.data.Keys
import kotlin.math.abs
import kotlin.math.roundToInt

private val presets = listOf(
    "Фиолет" to 265f, "Индиго" to 235f, "Океан" to 205f, "Бирюза" to 175f, "Лес" to 135f,
    "Лайм" to 85f, "Янтарь" to 42f, "Закат" to 18f, "Рубин" to 355f, "Роза" to 330f,
)

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val isDark = s.themeMode == 2 || s.themeMode == 0
    val dynamicAvailable = Build.VERSION.SDK_INT >= 31
    val hidden by vm.hiddenTracks.collectAsStateWithLifecycle()
    var showHidden by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Section("Тема") {
            ChipsRow("Режим", listOf("Системная", "Светлая", "Тёмная"), s.themeMode) { vm.setPref(Keys.themeMode, it) }
            if (dynamicAvailable) {
                SwitchRow("Material You", "Цвета из обоев телефона", s.dynamicColor) { vm.setPref(Keys.dynamicColor, it) }
            }
            if (isDark) {
                SwitchRow("AMOLED-чёрный", "Настоящий чёрный фон в тёмной теме", s.amoled) { vm.setPref(Keys.amoled, it) }
            }
            Text("Палитра", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.alpha(if (s.dynamicColor && dynamicAvailable) 0.5f else 1f)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(presets) { (_, hue) ->
                        val selected = !s.dynamicColor && abs(s.seedHue - hue) < 1f
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.hsl(hue, 0.6f, 0.45f))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable {
                                    vm.setPref(Keys.seedHue, hue)
                                    vm.setPref(Keys.dynamicColor, false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
            }
            SliderRow("Свой оттенок", s.seedHue, 0f..360f, "${s.seedHue.roundToInt()}°") {
                vm.setPref(Keys.seedHue, it)
                if (s.dynamicColor) vm.setPref(Keys.dynamicColor, false)
            }
        }

        Section("Форма и шрифт") {
            SliderRow("Скругление углов", s.corner, 0f..2f, "${(s.corner * 100).roundToInt()}%") { vm.setPref(Keys.corner, it) }
            ChipsRow("Шрифт", listOf("Стандартный", "С засечками", "Моноширинный", "Рукописный"), s.fontFamily) { vm.setPref(Keys.fontFamily, it) }
            SliderRow("Размер текста", s.fontScale, 0.8f..1.4f, "${(s.fontScale * 100).roundToInt()}%") { vm.setPref(Keys.fontScale, it) }
        }

        Section("Плеер") {
            ChipsRow("Форма обложки", listOf("Скруглённая", "Круг", "Квадрат", "Срезанная"), s.artShape) { vm.setPref(Keys.artShape, it) }
            SliderRow("Размер обложки", s.artSize, 0.5f..1f, "${(s.artSize * 100).roundToInt()}%") { vm.setPref(Keys.artSize, it) }
            ChipsRow("Анимация вокруг обложки", listOf("Нет", "Кольцо", "Пульс", "Волны"), s.artFx) { vm.setPref(Keys.artFx, it) }
            SwitchRow("Волнистая полоса", "Прогресс-«червяк» как в Material 3: волнится, пока играет", s.wavySeek) { vm.setPref(Keys.wavySeek, it) }
            if (s.wavySeek) {
                SliderRow("Высота волны", s.waveAmp, 0.4f..2f, "${(s.waveAmp * 100).roundToInt()}%") { vm.setPref(Keys.waveAmp, it) }
            }
            ChipsRow("Кнопка «играть»", listOf("Морфинг", "Квадрат", "Таблетка"), s.playButtonShape) { vm.setPref(Keys.playButtonShape, it) }
            SwitchRow("Цветной фон", "Градиент из цвета темы", s.artBackground) { vm.setPref(Keys.artBackground, it) }
            SwitchRow("Вращение обложки", "Обложка крутится, пока играет музыка", s.spinArt) { vm.setPref(Keys.spinArt, it) }
            SwitchRow("Оставшееся время", "Показывать «-1:23» вместо длительности", s.showRemaining) { vm.setPref(Keys.showRemaining, it) }
            SwitchRow("Вибро-отклик", null, s.haptics) { vm.setPref(Keys.haptics, it) }
            SwitchRow("Не гасить экран", "Пока приложение открыто", s.keepScreenOn) { vm.setPref(Keys.keepScreenOn, it) }
        }

        Section("Списки") {
            ChipsRow("Плотность", listOf("Компактно", "Обычно", "Крупно"), s.density) { vm.setPref(Keys.density, it) }
            SwitchRow("Обложки в списках", null, s.showArtInList) { vm.setPref(Keys.showArtInList, it) }
            SwitchRow("Прогресс в мини-плеере", null, s.miniProgress) { vm.setPref(Keys.miniProgress, it) }
        }

        Section("Библиотека") {
            SliderRow(
                "Скрывать короткие треки", s.minDurationSec.toFloat(), 0f..120f,
                if (s.minDurationSec == 0) "не скрывать" else "< ${s.minDurationSec} с", steps = 11
            ) { vm.setPref(Keys.minDuration, it.roundToInt()) }
            ChipsRow("Сортировка", listOf("Название", "Исполнитель", "Новые", "Длина"), s.sort) { vm.setPref(Keys.sort, it) }
            Button(onClick = { vm.refresh() }) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Text("  Пересканировать музыку")
            }
        }

        Section("Выход звука") {
            ChipsRow(
                "Принудительный выход",
                listOf("Авто", "Динамик", "Разговорный", "Проводные", "Bluetooth", "USB"),
                s.outputMode
            ) { vm.setPref(Keys.outputMode, it) }
            Text(
                "Если выбранное устройство не подключено, звук идёт как обычно, а при подключении переключится сам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow("Пауза при отключении наушников", "Выключите, чтобы музыка не останавливалась", s.pauseOnUnplug) { vm.setPref(Keys.pauseOnUnplug, it) }
        }

        Section("Скрытые треки и сброс") {
            OutlinedButton(onClick = { showHidden = true }) { Text("Скрытые треки (${hidden.size})") }
            OutlinedButton(onClick = { vm.resetSettings() }) { Text("Сбросить настройки к начальным") }
        }
        if (showHidden) HiddenDialog(vm) { showHidden = false }

        Section("О приложении") {
            Text("Player3 • версия 1.1", style = MaterialTheme.typography.titleSmall)
            Text(
                "com.rilelchaz.player3\nMaterial 3 • Jetpack Compose • Media3",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    var tmp by remember { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = tmp, onValueChange = { tmp = it; onChange(it) }, valueRange = range, steps = steps)
    }
}

@Composable
private fun ChipsRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, name ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(name) })
            }
        }
    }
}
