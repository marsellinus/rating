package com.ratig.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolVersionTest {

    @Test
    fun `version already prefixed is not doubled`() {
        assertEquals("v1", formatProtocolVersion("v1"))
        assertEquals("V2", formatProtocolVersion("V2"))
        assertEquals("v1", formatVersionLabel("v1"))
    }

    @Test
    fun `bare version gets one prefix`() {
        assertEquals("v1", formatProtocolVersion("1"))
        assertEquals("v1.2", formatProtocolVersion("1.2"))
        assertEquals("v1", formatProtocolVersion("  v1  "))
    }
}
