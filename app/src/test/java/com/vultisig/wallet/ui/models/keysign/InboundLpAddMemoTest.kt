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
import vultisig.keysign.v1.SignSolana
import vultisig.keysign.v1.TransactionType

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
    fun `asset side of a THORChain add from Gaia, Solana, Tron and XRP is an LP add`() {
        assertTrue(payload(COSMOS_SEND, "+:GAIA.ATOM:thor1self").isInboundLpAddMemo())
        assertTrue(payload(SOLANA, "+:SOL.SOL:thor1self").isInboundLpAddMemo())
        assertTrue(payload(TRON, "+:TRON.TRX:thor1self").isInboundLpAddMemo())
        assertTrue(payload(RIPPLE, "+:XRP.XRP:thor1self").isInboundLpAddMemo())
    }

    @Test
    fun `a Cosmos IBC transfer or XRP trust line carrying an LP memo is not an LP add`() {
        val ibc = COSMOS_SEND.copy(transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER)
        val trustSet =
            RIPPLE.copy(transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET)

        assertFalse(payload(ibc, "+:GAIA.ATOM:thor1self").isInboundLpAddMemo())
        assertFalse(payload(trustSet, "+:XRP.XRP:thor1self").isInboundLpAddMemo())
    }

    @Test
    fun `a pre-built Solana transaction is not reread from its memo`() {
        val prebuilt =
            payload(SOLANA, "+:SOL.SOL:thor1self")
                .copy(signSolana = SignSolana(rawTransactions = listOf("AQ==")))

        assertFalse(prebuilt.isInboundLpAddMemo())
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
        val COSMOS_SEND =
            BlockChainSpecific.Cosmos(
                accountNumber = BigInteger.ONE,
                sequence = BigInteger.ZERO,
                gas = BigInteger.ONE,
                ibcDenomTraces = null,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )
        val SOLANA =
            BlockChainSpecific.Solana(
                recentBlockHash = "hash",
                priorityFee = BigInteger.ONE,
                priorityLimit = BigInteger.ONE,
            )
        val TRON =
            BlockChainSpecific.Tron(
                timestamp = 0u,
                expiration = 0u,
                blockHeaderTimestamp = 0u,
                blockHeaderNumber = 0u,
                blockHeaderVersion = 0u,
                blockHeaderTxTrieRoot = "",
                blockHeaderParentHash = "",
                blockHeaderWitnessAddress = "",
                gasFeeEstimation = 0u,
            )
        val RIPPLE = BlockChainSpecific.Ripple(sequence = 1u, gas = 12u, lastLedgerSequence = 2u)
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
