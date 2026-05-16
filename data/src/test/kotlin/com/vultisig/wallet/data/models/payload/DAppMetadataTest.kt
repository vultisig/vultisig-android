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
    fun `isEmpty is true only when every field is empty`() {
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
    fun `isEmpty is false when iconUrl is set`() {
        assertFalse(DAppMetadata(name = "", url = "", iconUrl = "https://x.io/favicon.ico").isEmpty)
    }

    @Test
    fun `safeIconUrl returns https iconUrl unchanged`() {
        val metadata = DAppMetadata(name = "", url = "", iconUrl = "https://1inch.io/favicon.ico")
        assertEquals("https://1inch.io/favicon.ico", metadata.safeIconUrl)
    }

    @Test
    fun `safeIconUrl is null for non-https schemes so Coil never loads file or data URIs`() {
        val cases =
            listOf(
                "http://insecure.example/favicon.ico",
                "file:///etc/passwd",
                "data:image/svg+xml,<svg/>",
                "content://com.attacker.fileprovider/x",
            )
        for (icon in cases) {
            val metadata = DAppMetadata(name = "", url = "", iconUrl = icon)
            assertEquals(null, metadata.safeIconUrl, "expected null safeIconUrl for $icon")
        }
    }

    @Test
    fun `safeIconUrl is null when iconUrl is empty`() {
        val metadata = DAppMetadata(name = "", url = "", iconUrl = "")
        assertEquals(null, metadata.safeIconUrl)
    }
}
