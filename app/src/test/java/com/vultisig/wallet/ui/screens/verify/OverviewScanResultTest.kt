package com.vultisig.wallet.ui.screens.verify

import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.ui.models.transaction.TransactionScanStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Which scan result the overview sheet shows in place of its figures. */
internal class OverviewScanResultTest {

    @Test
    fun `a clean scan shows the safe confirmation, never the verdict`() {
        overviewScanResult(scanned(isSecure = true), showScanningWarning = true) shouldBe
            OverviewScanResult.Safe
    }

    @Test
    fun `a risky scan shows its verdict`() {
        val status = scanned(isSecure = false, riskLevel = SecurityRiskLevel.HIGH)

        overviewScanResult(status, showScanningWarning = true) shouldBe
            OverviewScanResult.Verdict(status.result)
    }

    @Test
    fun `nothing is shown until the result is opened`() {
        overviewScanResult(scanned(isSecure = false), showScanningWarning = false).shouldBeNull()
    }

    @Test
    fun `nothing is shown without a finished scan`() {
        listOf(
                TransactionScanStatus.NotStarted,
                TransactionScanStatus.Scanning,
                TransactionScanStatus.Error(message = "boom", provider = "blockaid"),
            )
            .forEach { status ->
                overviewScanResult(status, showScanningWarning = true).shouldBeNull()
            }
    }

    private fun scanned(isSecure: Boolean, riskLevel: SecurityRiskLevel = SecurityRiskLevel.NONE) =
        TransactionScanStatus.Scanned(
            SecurityScannerResult(
                provider = "blockaid",
                isSecure = isSecure,
                riskLevel = riskLevel,
                warnings = emptyList(),
                description = null,
                recommendations = "",
            )
        )
}
