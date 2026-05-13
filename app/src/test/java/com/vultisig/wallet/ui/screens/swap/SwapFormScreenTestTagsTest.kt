package com.vultisig.wallet.ui.screens.swap

import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * Guards the stable Compose `testTag` selectors that QA Maestro flows depend on for the swap
 * surface. A silent removal during a refactor (see #4181) breaks every cross-chain swap flow, so
 * this test fails the build the moment any of the selectors disappears.
 */
class SwapFormScreenTestTagsTest {

    @Test
    fun `SwapFormScreen testTags are present in swap screen sources`() {
        val swapSources =
            File("src/main/java/com/vultisig/wallet/ui/screens/swap")
                .also {
                    require(it.isDirectory) { "Expected source directory: ${it.absolutePath}" }
                }
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()

        val combined = swapSources.joinToString(separator = "\n") { it.readText() }

        assertAll(
            REQUIRED_TEST_TAGS.map { tag ->
                {
                    assert(combined.contains("\"$tag\"")) {
                        "Expected testTag \"$tag\" to be present in app/src/main/java/com/vultisig/wallet/ui/screens/swap/. " +
                            "If this is intentional, update SwapFormScreenTestTagsTest, otherwise restore the testTag — " +
                            "QA Maestro flows depend on it (see issue #4181)."
                    }
                }
            }
        )
    }

    private companion object {
        val REQUIRED_TEST_TAGS =
            listOf(
                "SwapFormScreen.fromChain",
                "SwapFormScreen.fromToken",
                "SwapFormScreen.fromAmount",
                "SwapFormScreen.toChain",
                "SwapFormScreen.toToken",
                "SwapFormScreen.swapButton",
            )
    }
}
