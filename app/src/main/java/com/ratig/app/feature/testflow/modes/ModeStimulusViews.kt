package com.ratig.app.feature.testflow.modes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratig.app.core.timing.modes.ModeStimulus
import com.ratig.app.core.timing.modes.RgbColor
import com.ratig.app.core.timing.modes.RgbShape
import kotlin.math.cos
import kotlin.math.sin

/** Neutral mode backdrop; stimulus colors below stay unambiguous on it. */
internal val ModeBackdrop = Color(0xFF101010)
internal val ModeCueColor = Color(0xFF232323)
internal val ModeContentColor = Color.White

internal fun rgbColorOf(color: RgbColor): Color = when (color) {
    RgbColor.RED -> Color(0xFFE53935)
    RgbColor.GREEN -> Color(0xFF43A047)
    RgbColor.BLUE -> Color(0xFF1E88E5)
}

private val FocusGoColor = Color(0xFF43A047)
private val FocusNoGoColor = Color(0xFFE53935)

/**
 * Subtle progress: thin bar at the very top plus a small "x/y" counter -
 * never distracting from the stimulus area.
 */
@Composable
internal fun ModeProgressHeader(completed: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LinearProgressIndicator(
            progress = { if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = ModeContentColor.copy(alpha = 0.55f),
            trackColor = ModeContentColor.copy(alpha = 0.15f),
        )
        Text(
            text = "Percobaan $completed dari $total",
            color = ModeContentColor.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * RGB_RANDOM stimulus: colored square with a distinct outlined shape + distinct
 * symbol. Shape/symbol come frozen from [RgbColor] so color is never the only
 * cue. Randomized position renders inside a 3x3 slot layout.
 */
@Composable
internal fun RgbStimulusView(stimulus: ModeStimulus.Rgb, modifier: Modifier = Modifier) {
    val position = stimulus.position
    if (position == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RgbSquare(stimulus)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(SLOT_ROWS) { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(SLOT_COLS) { col ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (row * SLOT_COLS + col == position) RgbSquare(stimulus)
                        }
                    }
                }
            }
        }
    }
}

private const val SLOT_ROWS = 3
private const val SLOT_COLS = 3

@Composable
private fun RgbSquare(stimulus: ModeStimulus.Rgb) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .background(color = rgbColorOf(stimulus.color), shape = RoundedCornerShape(20.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            when (stimulus.color.shape) {
                RgbShape.CIRCLE -> drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f,
                    style = stroke,
                )
                RgbShape.TRIANGLE -> drawPath(
                    path = regularPolygonPath(center, size.minDimension / 2f, sides = 3, rotationDeg = -90f),
                    color = Color.White,
                    style = stroke,
                )
                RgbShape.DIAMOND -> drawPath(
                    path = regularPolygonPath(center, size.minDimension / 2f, sides = 4, rotationDeg = -90f),
                    color = Color.White,
                    style = stroke,
                )
            }
        }
        Text(
            text = stimulus.color.symbol,
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/**
 * RANDOM_BUTTON stimulus: gridSize x gridSize buttons (UI enforces >= 64dp
 * touch targets); target symbol on one seeded cell, distractor symbols on up
 * to k seeded cells, blanks stay neutral and disabled.
 */
@Composable
internal fun GridStimulusView(
    stimulus: ModeStimulus.Grid,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = kotlin.math.sqrt(stimulus.symbols.size.toDouble()).toInt().coerceAtLeast(2)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        for (row in 0 until grid) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (col in 0 until grid) {
                    val index = row * grid + col
                    val symbol = stimulus.symbols.getOrNull(index)
                    GridCell(
                        symbol = symbol,
                        enabled = enabled && symbol != null,
                        onClick = { onCellTap(index) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .requiredSizeIn(minWidth = 64.dp, minHeight = 64.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    symbol: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    if (symbol == null) {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = ModeCueColor,
            contentColor = ModeContentColor.copy(alpha = 0.25f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { }
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = ModeCueColor,
            contentColor = ModeContentColor,
            onClick = onClick,
            enabled = enabled,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = symbol,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * FOCUS_INHIBITION stimulus: solid green circle (go) or red circle (no-go),
 * centered.
 */
@Composable
internal fun FocusStimulusView(stimulus: ModeStimulus.FocusCircle, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            drawCircle(color = if (stimulus.isGo) FocusGoColor else FocusNoGoColor)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

internal fun regularPolygonPath(
    center: Offset,
    radius: Float,
    sides: Int,
    rotationDeg: Float,
): Path {
    val path = Path()
    for (i in 0 until sides) {
        val angleDeg = rotationDeg + (360.0 * i / sides)
        val angle = Math.toRadians(angleDeg)
        val point = Offset(
            x = center.x + (radius * cos(angle)).toFloat(),
            y = center.y + (radius * sin(angle)).toFloat(),
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}
