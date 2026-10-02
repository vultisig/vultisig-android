package com.vultisig.wallet.ui.utils

import android.os.Build
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class VsClipboardServiceTest {

    @Test
    fun `app confirms copies below Android 13, where the system shows nothing`() {
        assertTrue(VsClipboardService.needsCopyConfirmation(Build.VERSION_CODES.O))
        assertTrue(VsClipboardService.needsCopyConfirmation(Build.VERSION_CODES.S_V2))
    }

    @Test
    fun `app leaves copies to the system preview from Android 13 on`() {
        assertFalse(VsClipboardService.needsCopyConfirmation(Build.VERSION_CODES.TIRAMISU))
        assertFalse(VsClipboardService.needsCopyConfirmation(Build.VERSION_CODES.VANILLA_ICE_CREAM))
    }
}
