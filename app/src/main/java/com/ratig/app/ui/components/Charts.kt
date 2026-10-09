package com.ratig.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratig.app.domain.model.ClassificationCount
import com.ratig.app.domain.model.DailyCount
import com.ratig.app.domain.model.NamedCount
import com.ratig.app.domain.model.ReactionTrendPoint
import com.ratig.app.ui.theme.SeverityHigh
import com.ratig.app.ui.theme.SeverityInfo
import com.ratig.app.ui.theme.SeverityOk
import com.ratig.app.ui.theme.SeverityUnknown
import com.ratig.app.ui.theme.SeverityWarn
import java.util.Locale

/**
 * Dependency-free charts drawn with Compose [Canvas]. Every chart exposes a
 * [contentDescription] summarizing its data for screen readers, shows real
 * Text labels next to the drawn shapes (never color alone) and renders a
 * friendly empty note when there is no data.
 */

/** Horizontal bar chart: one row per category with label, bar and count value. */
@Composable
fun BarChartSimple(
    data: List<NamedCount>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (data.isEmpty()) {
        "Grafik batang: belum ada data"
    } else {
        "Grafik batang. " + data.joinToString(separator = ", ") { "${it.name}: ${it.count}" }
    }
    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data.isEmpty()) {
            ChartEmptyNote()
        } else {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1L)
            data.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.34f),
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(0.5f)
                            .height(14.dp),
                    ) {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(color = trackColor, cornerRadius = radius)
                        val fraction = (item.count.toFloat() / maxCount).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = barColor,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = radius,
                            )
                        }
                    }
                    Text(
                        text = item.count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 32.dp),
                    )
                }
            }
        }
    }
}

/** Line + area chart of session counts per day. */
@Composable
fun DailyTrendChart(
    data: List<DailyCount>,
    modifier: Modifier = Modifier,
) {
    val description = if (data.isEmpty()) {
        "Grafik tren harian: belum ada data"
    } else {
        "Grafik tren harian. " + data.joinToString(separator = ", ") { "${it.day}: ${it.count}" }
    }
    TrendLineChart(
        labels = data.map { it.day },
        values = data.map { it.count.toDouble() },
        emptyLabel = "Tren harian belum memiliki sesi",
        description = description,
        modifier = modifier,
    )
}

/** Line + area chart of mean reaction time (ms) per day; null days are skipped. */
@Composable
fun ReactionTrendChart(
    data: List<ReactionTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val measured = data.filter { it.meanMs != null }
    val description = if (measured.isEmpty()) {
        "Grafik tren waktu reaksi: belum ada data"
    } else {
        "Grafik tren waktu reaksi. " + measured.joinToString(separator = ", ") {
            "${it.date}: ${formatMs(it.meanMs)} ms (${it.sampleCount} sesi)"
        }
    }
    TrendLineChart(
        labels = data.map { it.date },
        values = data.map { it.meanMs },
        emptyLabel = "Belum ada data tren waktu reaksi untuk periode ini",
        description = description,
        modifier = modifier,
    )
}

/** Donut chart of result classifications using the severity color scale. */
@Composable
fun DonutClassificationChart(
    counts: List<ClassificationCount>,
    modifier: Modifier = Modifier,
) {
    val total = counts.sumOf { it.count }
    val description = if (total == 0L) {
        "Diagram klasifikasi hasil: belum ada data"
    } else {
        "Diagram klasifikasi hasil pemeriksaan, total $total sesi. " +
            counts.joinToString(separator = ", ") { "${it.label}: ${it.count}" }
    }
    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (counts.isEmpty() || total == 0L) {
            ChartEmptyNote()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(132.dp)) {
                        val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                        var start = -90f
                        counts.forEach { c ->
                            if (c.count > 0L) {
                                val sweep = c.count.toFloat() / total.toFloat() * 360f
                                drawArc(
                                    color = severityChartColor(c.severity),
                                    startAngle = start,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = stroke,
                                )
                                start += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = total.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "sesi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    counts.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(severityChartColor(c.severity)),
                            )
                            Text(
                                text = c.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = c.count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLineChart(
    labels: List<String>,
    values: List<Double?>,
    emptyLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.16f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val maxValue = (values.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        if (values.size < 2 || values.filterNotNull().isEmpty()) {
            ChartEmptyNote(emptyLabel)
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val n = values.size
                val stepX = size.width / (n - 1)
                val scaleY = (size.height / (maxValue * 1.15)).toFloat()
                val points: List<Offset?> = values.mapIndexed { i, v ->
                    if (v == null) null else Offset(i * stepX, (size.height - v * scaleY).toFloat())
                }
                listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                    val y = size.height * f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val anchors = points.mapIndexedNotNull { i, p -> if (p != null) i else null }
                if (anchors.size == 1) {
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = points[anchors.first()]!!)
                } else {
                    val area = Path()
                    var closedArea = false
                    points.forEach { p ->
                        if (p != null) {
                            if (!closedArea) {
                                area.moveTo(p.x, size.height)
                                closedArea = true
                            }
                            area.lineTo(p.x, p.y)
                        }
                    }
                    points.lastOrNull { it != null }?.let { area.lineTo(it.x, size.height) }
                    area.close()
                    drawPath(area, fillColor)

                    val line = Path()
                    var penDown = false
                    points.forEach { p ->
                        if (p != null) {
                            if (!penDown) {
                                line.moveTo(p.x, p.y)
                                penDown = true
                            } else {
                                line.lineTo(p.x, p.y)
                            }
                        }
                    }
                    drawPath(
                        line,
                        lineColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    points.forEach { p -> p?.let { drawCircle(lineColor, radius = 3.dp.toPx(), center = it) } }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = labelColor)
                if (labels.size > 2) {
                    Text(
                        labels[labels.size / 2],
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
                Text(
                    labels.last(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun severityChartColor(severity: Int): Color = when {
    severity < 0 -> SeverityUnknown
    severity <= 1 -> SeverityOk
    severity == 2 -> SeverityInfo
    severity == 3 -> SeverityWarn
    else -> SeverityHigh
}

@Composable
private fun ChartEmptyNote(text: String = "Belum ada data untuk ditampilkan") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatMs(value: Double?): String =
    if (value == null) "-" else String.format(Locale.US, "%.0f", value)
