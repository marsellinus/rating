package com.ratig.app.data.remote

import com.ratig.app.core.json.JsonCodec
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.remote.dto.FatigueRuleDto
import com.ratig.app.data.remote.dto.FatigueRuleInsertDto
import com.ratig.app.data.remote.dto.ProtocolInsertDto
import com.ratig.app.data.remote.dto.TestProtocolDto
import com.ratig.app.domain.model.ApprovalStatus
import com.ratig.app.domain.model.FatigueRule
import com.ratig.app.domain.model.ProtocolConfiguration
import com.ratig.app.domain.model.ProtocolStatus
import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.repository.ProtocolRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed [ProtocolRepository] for test protocols and fatigue rules.
 * Approval is a separate, admin-only step enforced by RLS on the server.
 */
@Singleton
class ProtocolRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : ProtocolRepository {

    override suspend fun listActiveProtocols(): AppResult<List<TestProtocol>> = AppResult.of {
        supabase.postgrest["test_protocols"].select {
            filter { eq("status", "active") }
            order("created_at", Order.DESCENDING)
        }.decodeList<TestProtocolDto>().map { it.toDomain() }
    }

    override suspend fun listAllProtocols(): AppResult<List<TestProtocol>> = AppResult.of {
        supabase.postgrest["test_protocols"].select {
            order("created_at", Order.DESCENDING)
        }.decodeList<TestProtocolDto>().map { it.toDomain() }
    }

    override suspend fun getProtocol(id: String): AppResult<TestProtocol> = AppResult.of {
        protocolRow(id)?.toDomain() ?: error("Protokol tidak ditemukan.")
    }

    override suspend fun upsertProtocol(protocol: TestProtocol): AppResult<TestProtocol> =
        AppResult.of {
            if (protocol.id.isBlank()) {
                val createdBy = currentUserId()
                supabase.postgrest["test_protocols"].insert(
                    ProtocolInsertDto(
                        name = protocol.name,
                        protocolVersion = protocol.protocolVersion,
                        trialCount = protocol.trialCount,
                        stimulusDelayMinMs = protocol.stimulusDelayMinMs,
                        stimulusDelayMaxMs = protocol.stimulusDelayMaxMs,
                        responseTimeoutMs = protocol.responseTimeoutMs,
                        configuration = protocol.configuration.toJsonElement(),
                        status = "draft",
                        createdBy = createdBy,
                    ),
                ) { select() }.decodeSingle<TestProtocolDto>().toDomain()
            } else {
                supabase.postgrest["test_protocols"].update({
                    set("name", protocol.name)
                    set("protocol_version", protocol.protocolVersion)
                    set("trial_count", protocol.trialCount)
                    set("stimulus_delay_min_ms", protocol.stimulusDelayMinMs)
                    set("stimulus_delay_max_ms", protocol.stimulusDelayMaxMs)
                    set("response_timeout_ms", protocol.responseTimeoutMs)
                    set("configuration", protocol.configuration.toJsonElement())
                }) { filter { eq("id", protocol.id) } }
                protocolRow(protocol.id)?.toDomain()
                    ?: error("Protokol tidak ditemukan setelah penyimpanan.")
            }
        }

    override suspend fun setProtocolStatus(id: String, status: ProtocolStatus): AppResult<Unit> =
        AppResult.of {
            supabase.postgrest["test_protocols"].update({
                set("status", status.wireValue())
            }) { filter { eq("id", id) } }
        }

    override suspend fun effectiveRule(protocolId: String): AppResult<FatigueRule?> =
        AppResult.of {
            val now = TimeProvider.nowUtc().toString()
            // Currently effective: approved, already valid, not expired.
            val effective = supabase.postgrest["fatigue_rules"].select {
                filter {
                    eq("protocol_id", protocolId)
                    eq("approval_status", "approved")
                    lte("effective_from", now)
                    or {
                        exact("effective_until", null)
                        gt("effective_until", now)
                    }
                }
                order("effective_from", Order.DESCENDING)
                limit(1)
            }.decodeList<FatigueRuleDto>().firstOrNull()
                // Fallback: latest approved rule regardless of the effective window.
                ?: supabase.postgrest["fatigue_rules"].select {
                    filter {
                        eq("protocol_id", protocolId)
                        eq("approval_status", "approved")
                    }
                    order("effective_from", Order.DESCENDING)
                    limit(1)
                }.decodeList<FatigueRuleDto>().firstOrNull()
            effective?.toDomain()
        }

    override suspend fun listRules(protocolId: String?): AppResult<List<FatigueRule>> =
        AppResult.of {
            supabase.postgrest["fatigue_rules"].select {
                filter { if (protocolId != null) eq("protocol_id", protocolId) }
                order("effective_from", Order.DESCENDING)
            }.decodeList<FatigueRuleDto>().map { it.toDomain() }
        }

    override suspend fun upsertRule(rule: FatigueRule): AppResult<FatigueRule> = AppResult.of {
        if (rule.id.isBlank()) {
            supabase.postgrest["fatigue_rules"].insert(
                FatigueRuleInsertDto(
                    protocolId = rule.protocolId,
                    ruleVersion = rule.ruleVersion,
                    config = rule.config.toJsonElement(),
                    effectiveFrom = rule.effectiveFrom,
                    effectiveUntil = rule.effectiveUntil,
                    approvalStatus = "draft",
                    createdBy = currentUserId(),
                ),
            ) { select() }.decodeSingle<FatigueRuleDto>().toDomain()
        } else {
            // Upserts produce drafts; activation happens via approveRule only.
            supabase.postgrest["fatigue_rules"].update({
                set("rule_version", rule.ruleVersion)
                set("config", rule.config.toJsonElement())
                set("effective_from", rule.effectiveFrom.toString())
                if (rule.effectiveUntil != null) set("effective_until", rule.effectiveUntil.toString())
                set("approval_status", "draft")
            }) { filter { eq("id", rule.id) } }
            ruleRow(rule.id)?.toDomain() ?: error("Aturan tidak ditemukan setelah penyimpanan.")
        }
    }

    override suspend fun approveRule(ruleId: String): AppResult<Unit> = AppResult.of {
        val approverId = currentUserId()
        supabase.postgrest["fatigue_rules"].update({
            set("approval_status", "approved")
            set("approved_by", approverId)
        }) { filter { eq("id", ruleId) } }
    }

    override suspend fun approveProtocol(protocolId: String): AppResult<Unit> = AppResult.of {
        val approverId = currentUserId()
        supabase.postgrest["test_protocols"].update({
            set("status", "active")
            set("approved_by", approverId)
        }) { filter { eq("id", protocolId) } }
    }

    private suspend fun protocolRow(id: String): TestProtocolDto? =
        supabase.postgrest["test_protocols"].select {
            filter { eq("id", id) }
        }.decodeList<TestProtocolDto>().firstOrNull()

    private suspend fun ruleRow(id: String): FatigueRuleDto? =
        supabase.postgrest["fatigue_rules"].select {
            filter { eq("id", id) }
        }.decodeList<FatigueRuleDto>().firstOrNull()

    private suspend fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("Pengguna belum masuk.")

    private fun ProtocolStatus.wireValue(): String = when (this) {
        ProtocolStatus.DRAFT -> "draft"
        ProtocolStatus.ACTIVE -> "active"
        ProtocolStatus.RETIRED -> "retired"
    }

    private fun ApprovalStatus.wireValue(): String = when (this) {
        ApprovalStatus.DRAFT -> "draft"
        ApprovalStatus.APPROVED -> "approved"
        ApprovalStatus.RETIRED -> "retired"
    }

    private fun ProtocolConfiguration.toJsonElement() =
        JsonCodec.json.encodeToJsonElement(ProtocolConfiguration.serializer(), this)

    private fun RuleConfig.toJsonElement() =
        JsonCodec.json.encodeToJsonElement(RuleConfig.serializer(), this)
}
