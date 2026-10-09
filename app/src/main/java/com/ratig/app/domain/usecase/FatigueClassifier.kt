package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.ClassificationLogic
import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.TestMetrics
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of applying a [RuleConfig] to computed [TestMetrics]. */
data class ClassificationOutcome(
    val bandCode: String,
    val bandLabel: String,
    val severity: Int,
    /** Human-readable explanation: metric used, value, rule basis. */
    val explanation: String,
) {
    /** True when the rule bands are still placeholder (not yet validated by the org). */
    val needsValidation: Boolean get() = bandLabel == UNVALIDATED_LABEL || bandCode == NOT_CONFIGURED

    companion object {
        const val UNVALIDATED_LABEL = "Perlu validasi"
        const val NOT_CONFIGURED = "NOT_CONFIGURED"
        const val INSUFFICIENT_DATA = "INSUFFICIENT_DATA"
    }
}

/**
 * Rule-based fatigue classification. Deterministic, auditable, versioned.
 *
 * IMPORTANT: RATIG results are decision SUPPORT for Fit-to-Work examination.
 * They are never an automatic diagnosis or a final work-fitness decision -
 * operational decisions follow the organization's SOP and authorized examiner.
 */
@Singleton
class FatigueClassifier @Inject constructor() {

    fun classify(metrics: TestMetrics, config: RuleConfig): ClassificationOutcome {
        if (metrics.validTrialCount < config.minValidTrials) {
            return ClassificationOutcome(
                bandCode = ClassificationOutcome.INSUFFICIENT_DATA,
                bandLabel = "Data tidak cukup",
                severity = -1,
                explanation = "Hanya ${metrics.validTrialCount} percobaan valid " +
                    "(minimal ${config.minValidTrials}). Klasifikasi tidak dapat dilakukan.",
            )
        }

        return when (config.logic) {
            ClassificationLogic.MEAN_MS_BAND -> {
                val value = metrics.meanReactionTimeMs ?: return insufficient(config)
                classifyBand(
                    valueMs = value.toLong(),
                    metricName = "rata-rata (mean)",
                    valueDisplay = "${format(value)} ms",
                    config = config,
                )
            }

            ClassificationLogic.MEDIAN_MS_BAND -> {
                val value = metrics.medianReactionTimeMs ?: return insufficient(config)
                classifyBand(
                    valueMs = value.toLong(),
                    metricName = "median",
                    valueDisplay = "${format(value)} ms",
                    config = config,
                )
            }

            ClassificationLogic.SLOW_COUNT_BAND -> {
                if (config.slowCountBands.isEmpty()) return unconfigured(config)
                val band = config.slowCountBands.lastOrNull { metrics.slowResponseCount >= it.minSlowCount }
                    ?: return unconfigured(config)
                ClassificationOutcome(
                    bandCode = band.code,
                    bandLabel = band.label,
                    severity = band.severity,
                    explanation = "Klasifikasi berdasarkan jumlah respons lambat " +
                        "(${metrics.slowResponseCount}, ambang ${config.slowResponseThresholdMs} ms): \"${band.label}\".",
                )
            }
        }
    }

    private fun classifyBand(
        valueMs: Long,
        metricName: String,
        valueDisplay: String,
        config: RuleConfig,
    ): ClassificationOutcome {
        if (config.bands.isEmpty()) return unconfigured(config)
        val band = config.bands.firstOrNull { it.contains(valueMs) }
            ?: return unconfigured(config)
        return ClassificationOutcome(
            bandCode = band.code,
            bandLabel = band.label,
            severity = band.severity,
            explanation = "Klasifikasi berdasarkan $metricName reaksi $valueDisplay " +
                "ke dalam rentang ${band.minMs}–${band.maxMsExclusive ?: "∞"} ms: \"${band.label}\".",
        )
    }

    private fun insufficient(config: RuleConfig) = ClassificationOutcome(
        bandCode = ClassificationOutcome.INSUFFICIENT_DATA,
        bandLabel = "Data tidak cukup",
        severity = -1,
        explanation = "Metrik tidak tersedia untuk klasifikasi (minimal ${config.minValidTrials} percobaan valid).",
    )

    private fun unconfigured(config: RuleConfig) = ClassificationOutcome(
        bandCode = ClassificationOutcome.NOT_CONFIGURED,
        bandLabel = "Belum dikonfigurasi",
        severity = -1,
        explanation = "Aturan klasifikasi belum memiliki rentang yang sesuai. " +
            "Periksa konfigurasi aturan pada modul Administrasi.",
    )

    private fun format(value: Double): String =
        String.format(Locale.US, "%.1f", value)
}
