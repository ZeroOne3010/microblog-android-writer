package io.github.zeroone3010.yablogwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HostInputSanitizerTest {
    @Test
    fun `sanitizeHostInput removes all whitespace`() {
        assertEquals("https://subdomain.micro.blog", sanitizeHostInput("https:// subdomain .micro.blog"))
    }

    @Test
    fun `sanitizeHostInput preserves non-whitespace characters`() {
        assertEquals("https://micro.blog", sanitizeHostInput("https://micro.blog"))
    }
}
