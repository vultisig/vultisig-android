package com.vultisig.wallet.data.models.payload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DAppMetadataTest {

    @Test
    fun `host extracts hostname from a well-formed https url`() {
        val metadata =
            DAppMetadata(name = "Uniswap", url = "https://app.uniswap.org/swap", iconUrl = "")
        assertEquals("app.uniswap.org", metadata.host)
    }

    @Test
    fun `host returns empty string when url is empty`() {
        val metadata = DAppMetadata(name = "Uniswap", url = "", iconUrl = "")
        assertEquals("", metadata.host)
    }

    @Test
    fun `host returns empty for unparseable url so banner hides it`() {
        val metadata = DAppMetadata(name = "", url = "not a url", iconUrl = "")
        assertEquals("", metadata.host)
    }

    @Test
    fun `host returns empty for non-http schemes so we never display attacker-controlled text`() {
        // A hostile dApp could send `javascript:`, `data:`, `file:`, mailto: — none should leak
        // into the banner as if they were hostnames.
        val cases =
            listOf(
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "file:///etc/passwd",
                "mailto:nobody@example.com",
            )
        for (url in cases) {
            val metadata = DAppMetadata(name = "", url = url, iconUrl = "")
            assertEquals("", metadata.host, "expected empty host for $url")
        }
    }

    @Test
    fun `host preserves port and path off the hostname`() {
        val metadata =
            DAppMetadata(name = "", url = "https://app.uniswap.org:8443/swap?x=1", iconUrl = "")
        assertEquals("app.uniswap.org", metadata.host)
    }

    @Test
    fun `isEmpty is true when name and url are both empty`() {
        assertTrue(DAppMetadata(name = "", url = "", iconUrl = "").isEmpty)
    }

    @Test
    fun `isEmpty is false when name is set`() {
        assertFalse(DAppMetadata(name = "Uniswap", url = "", iconUrl = "").isEmpty)
    }

    @Test
    fun `isEmpty is false when url is set`() {
        assertFalse(DAppMetadata(name = "", url = "https://x.io", iconUrl = "").isEmpty)
    }

    @Test
    fun `isEmpty is true when only iconUrl is set so attacker can't force a network fetch via icon-only metadata`() {
        // Without name or url there's nothing identifying to show — gating on iconUrl alone would
        // both render an empty text stack and let a hostile dApp trigger a fetch to an
        // attacker-controlled origin via the banner.
        assertTrue(DAppMetadata(name = "", url = "", iconUrl = "https://x.io/favicon.ico").isEmpty)
    }
}
