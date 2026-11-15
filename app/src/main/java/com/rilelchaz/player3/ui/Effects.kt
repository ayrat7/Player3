package com.rilelchaz.player3.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

/** Фаза волны: крутится, только пока играет музыка. */
@Composable
fun rememberWavePhase(playing: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                phase.floatValue = (phase.floatValue + (now - last) * 0.006f) % TWO_PI
                last = now
            }
        }
    }
    return phase
}

fun DrawScope.drawWavy(
    progress: Float,
    phase: Float,
    ampPx: Float,
    strokePx: Float,
    waveLenPx: Float,
    active: Color,
    inactive: Color,
    thumbH: Float,
) {
    val w = size.width
    val cy = size.height / 2f
    val pad = strokePx / 2f
    val x = pad + (w - 2 * pad) * progress.coerceIn(0f, 1f)

    // ещё не проигранная часть — ровная линия
    if (x < w - pad) {
        drawLine(inactive, Offset(x, cy), Offset(w - pad, cy), strokeWidth = strokePx, cap = StrokeCap.Round)
    }

    // проигранная часть — «червяк»
    val path = Path()
    path.moveTo(pad, cy)
    var px = pad
    while (px < x) {
        px = minOf(px + 3f, x)
        val ramp = ((px - pad) / waveLenPx).coerceIn(0f, 1f)
        path.lineTo(px, cy + sin((px / waveLenPx) * TWO_PI - phase) * ampPx * ramp)
    }
    drawPath(path, active, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))

    if (thumbH > 0f) {
        drawRoundRect(
            color = active,
            topLeft = Offset(x - strokePx / 2f, cy - thumbH / 2f),
            size = Size(strokePx, thumbH),
            cornerRadius = CornerRadius(strokePx / 2f)
        )
    }
}

/** Волнистая полоска без перемотки (для мини-плеера). */
@Composable
fun WavyLine(progress: Float, playing: Boolean, modifier: Modifier = Modifier, amplitude: Dp = 3.dp) {
    val phase by rememberWavePhase(playing)
    val amp by animateFloatAsState(if (playing) 1f else 0f, label = "waveAmp")
    val active = MaterialTheme.colorScheme.primary
    val inactive = active.copy(alpha = 0.22f)
    Canvas(modifier) {
        drawWavy(progress, phase, amplitude.toPx() * amp, 3.dp.toPx(), 22.dp.toPx(), active, inactive, 0f)
    }
}

/** Полоса прогресса «как червяк» (Material 3 Expressive): волна во время игры, прямая на паузе. */
@Composable
fun WavySeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    playing: Boolean,
    modifier: Modifier = Modifier,
    amplitude: Dp = 4.dp,
) {
    val phase by rememberWavePhase(playing)
    val amp by animateFloatAsState(if (playing) 1f else 0f, label = "waveAmp")
    val active = MaterialTheme.colorScheme.primary
    val inactive = active.copy(alpha = 0.22f)
    var width by remember { mutableIntStateOf(1) }
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)

    Canvas(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    change((o.x / width).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> change((o.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onHorizontalDrag = { c, _ ->
                        c.consume()
                        change((c.position.x / width).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        drawWavy(value, phase, amplitude.toPx() * amp, 4.dp.toPx(), 28.dp.toPx(), active, inactive, 26.dp.toPx())
    }
}

/**
 * Анимация вокруг обложки. kind: 1 — вращающееся кольцо, 2 — пульс, 3 — волнистый контур.
 * t — время в мс (останавливается на паузе). Canvas в 1.34 раза больше обложки.
 */
@Composable
fun CoverFx(kind: Int, t: Float, modifier: Modifier = Modifier) {
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val c3 = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension / 2f / 1.34f // радиус самой обложки
        when (kind) {
            1 -> {
                drawCircle(c1.copy(alpha = 0.16f), radius = base * 1.10f, center = center, style = Stroke(2.dp.toPx()))
                rotate(t * 0.09f, center) {
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, c3, c2, c1), center),
                        radius = base * 1.10f,
                        center = center,
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                rotate(-t * 0.05f, center) {
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, c1.copy(alpha = 0.5f), c2.copy(alpha = 0.5f)), center),
                        radius = base * 1.20f,
                        center = center,
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            2 -> {
                for (i in 0..2) {
                    val p = ((t / 2400f) + i / 3f) % 1f
                    drawCircle(
                        c1.copy(alpha = (1f - p) * 0.55f),
                        radius = base * (1.02f + 0.30f * p),
                        center = center,
                        style = Stroke(3.dp.toPx())
                    )
                }
            }
            3 -> {
                val n = 240
                val amp = 6.dp.toPx()
                fun wobble(radius: Float, k: Int, speed: Float, a: Float): Path {
                    val path = Path()
                    for (i in 0..n) {
                        val ang = i.toFloat() / n * TWO_PI
                        val r = radius + sin(ang * k + t * speed) * a
                        val x = center.x + cos(ang) * r
                        val y = center.y + sin(ang) * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    return path
                }
                drawPath(wobble(base * 1.10f, 9, 0.003f, amp), c2, style = Stroke(4.dp.toPx(), join = StrokeJoin.Round))
                drawPath(wobble(base * 1.20f, 6, -0.002f, amp * 0.8f), c1.copy(alpha = 0.6f), style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}
