package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.model.VitalFinding
import com.ratig.app.domain.model.VitalKind
import com.ratig.app.domain.model.Vitals
import com.ratig.app.domain.model.VitalsAssessment

/**
 * Paramedic screening recorded next to one examination session.
 *
 * Kept device-local on purpose: blood pressure, pulse and breath alcohol are
 * health data, and the server has no column for them yet. Storing them in the
 * offline database means the screening still works in a blank spot, which is
 * the same constraint the reaction test has.
 *
 * One screening per session ([sessionLocalId] is the primary key): re-opening
 * the screen edits the existing record instead of stacking duplicates.
 */
@Entity(tableName = LocalHealthScreeningEntity.TABLE_NAME)
data class LocalHealthScreeningEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_local_id") val sessionLocalId: String,
    @ColumnInfo(name = "systolic") val systolic: Int? = null,
    @ColumnInfo(name = "diastolic") val diastolic: Int? = null,
    @ColumnInfo(name = "pulse") val pulse: Int? = null,
    @ColumnInfo(name = "alcohol_percent") val alcoholPercent: Double? = null,
    /** Worst finding severity; -1 when nothing was measured. */
    @ColumnInfo(name = "overall_severity") val overallSeverity: Int = -1,
    /** [FitnessRecommendation] name. */
    @ColumnInfo(name = "recommendation") val recommendation: String,
    /** Findings rendered as "Sistolik: Hipertensi Ringan" lines, one per row. */
    @ColumnInfo(name = "findings_text") val findingsText: String? = null,
    /** Free-text note from the examiner (context the numbers cannot carry). */
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "recorded_by") val recordedBy: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
) {
    fun toVitals(): Vitals = Vitals(
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
        alcoholPercent = alcoholPercent,
    )

    fun toAssessment(): VitalsAssessment = VitalsAssessment(
        findings = findingsText
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split(": ", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val kind = VitalKind.entries.firstOrNull { it.displayName == parts[0] }
                    ?: return@mapNotNull null
                VitalFinding(kind = kind, label = parts[1], severity = overallSeverity)
            }
            ?.toList()
            .orEmpty(),
        overallSeverity = overallSeverity,
    )

    fun recommendationValue(): FitnessRecommendation =
        FitnessRecommendation.entries.firstOrNull { it.name == recommendation }
            ?: FitnessRecommendation.FIT

    companion object {
        const val TABLE_NAME = "local_health_screenings"

        fun from(
            sessionLocalId: String,
            vitals: Vitals,
            assessment: VitalsAssessment,
            recommendation: FitnessRecommendation,
            note: String?,
            recordedBy: String,
            nowEpochMs: Long,
            createdAt: Long = nowEpochMs,
        ): LocalHealthScreeningEntity = LocalHealthScreeningEntity(
            sessionLocalId = sessionLocalId,
            systolic = vitals.systolic,
            diastolic = vitals.diastolic,
            pulse = vitals.pulse,
            alcoholPercent = vitals.alcoholPercent,
            overallSeverity = assessment.overallSeverity,
            recommendation = recommendation.name,
            findingsText = assessment.findings
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "${it.kind.displayName}: ${it.label}" },
            note = note?.takeIf { it.isNotBlank() },
            recordedBy = recordedBy,
            createdAt = createdAt,
            updatedAt = nowEpochMs,
        )
    }
}
