package com.vultisig.wallet.ui.screens.send

import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * Guards the stable Compose `testTag` selectors that QA Maestro flows depend on. A silent removal
 * during a refactor (see #4180) breaks every automated send flow, so this test fails the build the
 * moment any of the four selectors disappears from the send form sources.
 */
class SendFormScreenTestTagsTest {

    @Test
    fun `SendFormScreen testTags are present in send form sources`() {
        val sendFormSources =
            listOf("src/main/java/com/vultisig/wallet/ui/screens/send").map(::File).flatMap { dir ->
                require(dir.isDirectory) { "Expected source directory: ${dir.absolutePath}" }
                dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }

        val combined = sendFormSources.joinToString(separator = "\n") { it.readText() }

        assertAll(
            REQUIRED_TEST_TAGS.map { tag ->
                {
                    assert(combined.contains("\"$tag\"")) {
                        "Expected testTag \"$tag\" to be present in app/src/main/java/com/vultisig/wallet/ui/screens/send/. " +
                            "If this is intentional, update SendFormScreenTestTagsTest, otherwise restore the testTag — " +
                            "QA Maestro flows depend on it (see issue #4180)."
                    }
                }
            }
        )
    }

    private companion object {
        val REQUIRED_TEST_TAGS =
            listOf(
                "SendFormScreen.chainSelector",
                "SendFormScreen.addressField",
                "SendFormScreen.amountField",
                "SendFormScreen.bondAddressField",
            )
    }
}
