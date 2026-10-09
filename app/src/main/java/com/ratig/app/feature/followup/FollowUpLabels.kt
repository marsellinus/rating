package com.ratig.app.feature.followup

import com.ratig.app.domain.model.FollowUpAction
import com.ratig.app.domain.model.FollowUpStatus

/** Bahasa Indonesia display labels for follow-up codes and statuses. */
object FollowUpLabels {

    fun actionType(code: String?): String = when (code) {
        FollowUpAction.RECHECK -> "Pemeriksaan Ulang"
        FollowUpAction.REST -> "Istirahat"
        FollowUpAction.EVALUATION -> "Evaluasi Petugas"
        FollowUpAction.ESCALATION -> "Eskalasi Fit-to-Work"
        FollowUpAction.OTHER -> "Lainnya"
        else -> code ?: "-"
    }

    fun statusLabel(status: FollowUpStatus): String = when (status) {
        FollowUpStatus.OPEN -> "Terbuka"
        FollowUpStatus.IN_PROGRESS -> "Diproses"
        FollowUpStatus.COMPLETED -> "Selesai"
        FollowUpStatus.CANCELLED -> "Dibatalkan"
    }

    fun statusSeverity(status: FollowUpStatus): Int = when (status) {
        FollowUpStatus.OPEN -> 3
        FollowUpStatus.IN_PROGRESS -> 2
        FollowUpStatus.COMPLETED -> 1
        FollowUpStatus.CANCELLED -> -1
    }
}
