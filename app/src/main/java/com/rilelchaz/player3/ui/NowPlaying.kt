@file:OptIn(ExperimentalMaterial3Api::class)

package com.rilelchaz.player3.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rilelchaz.player3.MainViewModel

// ---------------------------------------------------------------- мини-плеер

@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val track by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val t = track ?: return

    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArt(
                    t, Modifier.size(46.dp),
                    if (s.artShape == 1) CircleShape else RoundedCornerShape((12 * s.corner).dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        t.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза/играть",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Дальше", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (s.miniProgress) {
                val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                if (s.wavySeek) {
                    WavyLine(
                        progress = frac,
                        playing = playing,
                        amplitude = (2.5f * s.waveAmp).dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp).height(14.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- большой плеер

@Composable
fun NowPlayingScreen(vm: MainViewModel, onClose: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val track by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val sleepLeft by vm.sleepLeftSec.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val t = track
    if (t == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    fun tick() {
        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    var sheet by remember { mutableIntStateOf(0) } // 0 нет, 1 очередь, 2 звук, 3 таймер сна
    var drag by remember { mutableStateOf<Float?>(null) }
    var angle by remember { mutableFloatStateOf(0f) }

    val spin = s.spinArt
    var fxT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, spin, s.artFx) {
        if (playing && (spin || s.artFx != 0)) {
            var last = androidx.compose.runtime.withFrameMillis { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameMillis { it }
                val dt = now - last
                if (spin) angle = (angle + dt * 0.03f) % 360f
                fxT += dt
                last = now
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val bg = if (s.artBackground) Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface, colors.surface))
    else Brush.verticalGradient(listOf(colors.surface, colors.surface))

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // верхняя строка
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(32.dp)) }
                Text(
                    "Сейчас играет",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { sheet = 1 }) { Icon(Icons.Rounded.QueueMusic, "Очередь") }
            }

            // обложка
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoxWithConstraints {
                    val fxOn = s.artFx != 0
                    val side = minOf(maxWidth * s.artSize, maxHeight) * (if (fxOn) 0.82f else 1f)
                    val scale by animateFloatAsState(
                        targetValue = if (playing) 1f else 0.88f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "artScale"
                    )
                    val shape = artShape(s.artShape, s.corner)
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        if (fxOn) CoverFx(s.artFx, fxT, Modifier.requiredSize(side * 1.34f))
                        TrackArt(
                            t,
                            Modifier
                                .size(side)
                                .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = angle }
                                .shadow(18.dp, shape),
                            shape,
                            sizePx = 1024
                        )
                    }
                }
            }

            // название + избранное
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        t.artist, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                val fav = t.id in favs
                IconButton(onClick = { tick(); vm.toggleFavorite(t.id) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное",
                        tint = if (fav) colors.tertiary else colors.onSurfaceVariant
                    )
                }
            }

            // перемотка
            val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            if (s.wavySeek) {
                WavySeekBar(
                    value = drag ?: frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { vm.seekTo((it * dur).toLong()) }
                        drag = null
                    },
                    playing = playing,
                    amplitude = (4 * s.waveAmp).dp,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            } else {
                Slider(
                    value = drag ?: frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { vm.seekTo((it * dur).toLong()) }
                        drag = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(((drag ?: frac) * dur).toLong()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    if (s.showRemaining) "-" + formatTime(dur - pos) else formatTime(dur),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // управление
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { tick(); vm.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Перемешать", tint = if (shuffle) colors.primary else colors.onSurfaceVariant)
                }
                IconButton(onClick = { tick(); vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Назад", Modifier.size(36.dp))
                }

                val morph by animateDpAsState(if (playing) 26.dp else 42.dp, label = "playMorph")
                val (w, h, shape) = when (s.playButtonShape) {
                    1 -> Triple(84.dp, 84.dp, RoundedCornerShape(26.dp))
                    2 -> Triple(116.dp, 72.dp, RoundedCornerShape(36.dp))
                    else -> Triple(84.dp, 84.dp, RoundedCornerShape(morph))
                }
                FilledIconButton(
                    onClick = { tick(); vm.togglePlay() },
                    modifier = Modifier.size(width = w, height = h),
                    shape = shape
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Играть/пауза", Modifier.size(42.dp))
                }

                IconButton(onClick = { tick(); vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Дальше", Modifier.size(36.dp))
                }
                IconButton(onClick = { tick(); vm.cycleRepeat() }) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Повтор",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) colors.primary else colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // нижний ряд
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { sheet = 3 }) {
                    Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (sleepLeft > 0) formatTime(sleepLeft * 1000L) else "Сон")
                }
                FilledTonalButton(onClick = { sheet = 2 }) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Звук")
                }
                FilledTonalButton(onClick = { sheet = 1 }) {
                    Icon(Icons.Rounded.QueueMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Очередь")
                }
            }
        }
    }

    when (sheet) {
        1 -> QueueSheet(vm) { sheet = 0 }
        2 -> SoundSheet(vm) { sheet = 0 }
        3 -> SleepSheet(vm) { sheet = 0 }
    }
}

// ---------------------------------------------------------------- шторки

@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Очередь (${queue.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn {
            itemsIndexed(queue) { index, t ->
                val isCur = current?.id == t.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.jumpTo(index) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArt(t, Modifier.size(44.dp), RoundedCornerShape((10 * s.corner).dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isCur) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { vm.removeFromQueue(index) }) { Icon(Icons.Rounded.Close, "Убрать") }
                }
            }
        }
    }
}

@Composable
private fun SoundSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val speed by vm.speed.collectAsStateWithLifecycle()
    val pitch by vm.pitch.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var sp by remember { mutableFloatStateOf(speed) }
    var pi by remember { mutableFloatStateOf(pitch) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Звук", style = MaterialTheme.typography.titleLarge)
            Text("Скорость: ${"%.2f".format(sp)}×", style = MaterialTheme.typography.labelLarge)
            Slider(value = sp, onValueChange = { sp = it; vm.setSpeedPitch(sp, pi) }, valueRange = 0.5f..2f)
            Text("Тон: ${"%.2f".format(pi)}×", style = MaterialTheme.typography.labelLarge)
            Slider(value = pi, onValueChange = { pi = it; vm.setSpeedPitch(sp, pi) }, valueRange = 0.5f..2f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { sp = 1f; pi = 1f; vm.setSpeedPitch(1f, 1f) }) { Text("Сбросить") }
                Button(onClick = { vm.openEqualizer(ctx) }) { Text("Эквалайзер") }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SleepSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val left by vm.sleepLeftSec.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Таймер сна", style = MaterialTheme.typography.titleLarge)
            Text(
                if (left > 0) "Музыка остановится через ${formatTime(left * 1000L)}" else "Выключен",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                    FilterChip(selected = false, onClick = { vm.setSleepTimer(m); onDismiss() }, label = { Text("$m мин") })
                }
            }
            if (left > 0) TextButton(onClick = { vm.setSleepTimer(0); onDismiss() }) { Text("Отключить таймер") }
        }
    }
}
