package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class InboundLpAddMemoTest {

    @Test
    fun `asset side of a paired Maya add from Ethereum is an LP add`() {
        assertTrue(payload(EVM, "+:ETH.ETH:maya1self").isInboundLpAddMemo())
    }

    @Test
    fun `asset side of a paired add from Bitcoin is an LP add`() {
        assertTrue(payload(UTXO, "+:BTC.BTC:maya1self").isInboundLpAddMemo())
    }

    @Test
    fun `single-sided and long-form adds are LP adds`() {
        assertTrue(payload(UTXO, "+:BTC.BTC").isInboundLpAddMemo())
        assertTrue(payload(UTXO, "add:BTC.BTC:thor1self").isInboundLpAddMemo())
    }

    @Test
    fun `a memo that does not name a CHAIN dot ASSET pool is not an LP add`() {
        assertFalse(payload(UTXO, "+:BTC").isInboundLpAddMemo())
        assertFalse(payload(UTXO, "+:").isInboundLpAddMemo())
        assertFalse(payload(UTXO, "+:BTC.BTC:maya1self:extra").isInboundLpAddMemo())
    }

    @Test
    fun `other memos are not LP adds`() {
        assertFalse(payload(UTXO, "=:ETH.ETH:0xself").isInboundLpAddMemo())
        assertFalse(payload(UTXO, "-:BTC.BTC:10000").isInboundLpAddMemo())
        assertFalse(payload(UTXO, null).isInboundLpAddMemo())
    }

    @Test
    fun `an LP memo on a chain that carries its own deposit flag is left to that flag`() {
        val maya = BlockChainSpecific.MayaChain(BigInteger.ONE, BigInteger.ONE, isDeposit = false)
        assertFalse(payload(maya, "+:ETH.ETH:0xself").isInboundLpAddMemo())
    }

    @Test
    fun `a payload that describes itself is not reread from its memo`() {
        val withApproval =
            payload(EVM, "+:ETH.ETH:maya1self")
                .copy(approvePayload = ERC20ApprovePayload(BigInteger.ONE, "0xrouter"))

        assertFalse(withApproval.isInboundLpAddMemo())
    }

    private fun payload(specific: BlockChainSpecific, memo: String?) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "ETH",
                    logo = "",
                    address = "0xself",
                    decimal = 18,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "0xinbound",
            toAmount = BigInteger.TEN,
            blockChainSpecific = specific,
            memo = memo,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        val UTXO = BlockChainSpecific.UTXO(byteFee = BigInteger.ONE, sendMaxAmount = false)
        val EVM =
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = BigInteger.ONE,
                priorityFeeWei = BigInteger.ONE,
                nonce = BigInteger.ZERO,
                gasLimit = BigInteger.ONE,
            )
    }
}
