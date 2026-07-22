@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.blockchain.tron.TronStakingOperation
import com.vultisig.wallet.data.blockchain.tron.tronStakingMemo
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * TRON freeze/unfreeze is routed through the Send form and carries its operation only as an
 * internal memo prefix. The Verify screen builds off [TransactionToUiModelMapper]; without
 * operation-aware mapping it would report a stake/unstake as a plain send and leak the raw
 * "UNFREEZE:BANDWIDTH" prefix into the Memo row (issue #5274).
 */
internal class TransactionToUiModelMapperTronStakingTest {

    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk(relaxed = true)
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)

    private fun mapper() =
        TransactionToUiModelMapperImpl(
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
        )

    @Test
    fun `unfreeze surfaces the unfreeze header and keeps the resource in the memo`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val uiModel =
            mapper()
                .invoke(
                    tronTransaction(
                        tronStakingMemo(TronStakingOperation.UNFREEZE, TronResourceType.BANDWIDTH)
                    )
                )

        uiModel.headerTitleRes shouldBe R.string.tron_unfreeze_screen_title
        uiModel.memo shouldBe TronResourceType.BANDWIDTH.name
    }

    @Test
    fun `freeze surfaces the freeze header and keeps the resource in the memo`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val uiModel =
            mapper()
                .invoke(
                    tronTransaction(
                        tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.ENERGY)
                    )
                )

        uiModel.headerTitleRes shouldBe R.string.tron_freeze_screen_title
        uiModel.memo shouldBe TronResourceType.ENERGY.name
    }

    @Test
    fun `a TRC20 send that echoes a staking memo is not mislabeled as staking`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val stakingMemo = tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.ENERGY)
        val uiModel = mapper().invoke(tronTransaction(stakingMemo, token = trc20))

        uiModel.headerTitleRes shouldBe null
        uiModel.memo shouldBe stakingMemo
    }

    @Test
    fun `a plain TRON send keeps the generic header and its memo`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val uiModel = mapper().invoke(tronTransaction("gm"))

        uiModel.headerTitleRes shouldBe null
        uiModel.memo shouldBe "gm"
    }

    private fun tronTransaction(memo: String?, token: Coin = trx): Transaction =
        Transaction(
            id = "tx-1",
            vaultId = "vault-1",
            chainId = Chain.Tron.id,
            token = token,
            srcAddress = "Towner",
            dstAddress = "Tdest",
            tokenValue = TokenValue(value = BigInteger.TEN, token = token),
            fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            gasFee = TokenValue(value = BigInteger.ONE, token = token),
            totalGas = "0",
            memo = memo,
            estimatedFee = "0",
            blockChainSpecific = mockk<BlockChainSpecific.Tron>(relaxed = true),
        )

    private companion object {
        val trx =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "trx",
                address = "Towner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "tron",
                contractAddress = "",
                isNativeToken = true,
            )

        val trc20 =
            trx.copy(
                ticker = "USDT",
                contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                isNativeToken = false,
            )
    }
}
