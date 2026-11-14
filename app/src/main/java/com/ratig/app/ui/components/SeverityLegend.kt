package com.ratig.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratig.app.domain.model.SeverityScale

/**
 * Legend for the fatigue colour scale.
 *
 * A bare row of four words ("Normal / Ringan / Sedang / Berat") does not tell
 * the reader what each band *means*, which range it covers, or what to do. This
 * shows all three: the colour chip, the band with its measured range, and the
 * operational meaning.
 *
 * [bands] carries the ranges actually configured for the organization, so the
 * legend can never disagree with the classification that was applied. When it
 * is null the ranges are unknown and only the meaning is shown - the legend
 * then explicitly says the thresholds follow the active rule rather than
 * inventing numbers.
 */
@Composable
fun SeverityLegendCard(
    modifier: Modifier = Modifier,
    bands: List<LegendBand>? = null,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Indikator Kelelahan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Warna mengikuti kategori hasil. Rentang di bawah adalah " +
                    "rata-rata waktu reaksi yang dipakai aturan aktif.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entries(bands).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SeverityBadge(label = entry.band, severity = entry.severity)
                    Column(modifier = Modifier.weight(1f)) {
                        entry.range?.let { range ->
                            Text(
                                text = range,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = entry.meaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "Hasil ini alat bantu keputusan, bukan diagnosis. Keputusan " +
                    "kelayakan kerja tetap pada pemeriksa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One legend row: band name, its range (if known), and its meaning. */
data class LegendBand(
    val band: String,
    val severity: Int,
    val range: String?,
    val meaning: String,
)

/**
 * Fixed meanings of the four bands, independent of any organization's
 * thresholds. Only the ranges come from configuration.
 */
private fun entries(bands: List<LegendBand>?): List<LegendBand> {
    if (!bands.isNullOrEmpty()) return bands
    // No resolved rule: keep the fixed meanings, omit ranges rather than invent
    // thresholds the organization has not approved.
    return listOf(
        LegendBand("Normal", SeverityScale.NORMAL, null, bandMeaning(SeverityScale.NORMAL)),
        LegendBand("Ringan", SeverityScale.MILD, null, bandMeaning(SeverityScale.MILD)),
        LegendBand("Sedang", SeverityScale.MODERATE, null, bandMeaning(SeverityScale.MODERATE)),
        LegendBand("Berat", SeverityScale.SEVERE, null, bandMeaning(SeverityScale.SEVERE)),
    )
}

/**
 * Human-readable range of a rule band, e.g. "0–239 ms", "240–399 ms",
 * "≥ 580 ms". Uses the exclusive upper bound as the last included millisecond so
 * the printed ranges are contiguous and leave no gap the reader has to guess.
 */
fun com.ratig.app.domain.model.RuleBand.rangeLabel(): String {
    val upper = maxMsExclusive
    return if (upper == null) "≥ $minMs ms" else "$minMs–${upper - 1} ms"
}

/** Short operational meaning of a band severity, for the legend. */
fun bandMeaning(severity: Int): String = when (severity) {
    SeverityScale.NORMAL -> "Lanjutkan pekerjaan seperti biasa."
    SeverityScale.MILD -> "Perhatikan kondisi; pastikan cukup istirahat."
    SeverityScale.MODERATE -> "Pertimbangkan istirahat singkat dan pemeriksaan ulang."
    SeverityScale.SEVERE -> "Tunda pekerjaan berisiko; evaluasi oleh petugas."
    else -> "Kategori belum ditetapkan aturan."
}
