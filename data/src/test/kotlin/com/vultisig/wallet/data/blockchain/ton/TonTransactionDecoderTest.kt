package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_TONSTAKERS_STAKE
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.asSignedTransactionContent
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

/**
 * The Tonstakers half of the TON reader: what a co-signer (a relayed [KeysignPayload]) and the
 * initiator (a [DepositTransaction]) read out of the same message batch.
 */
internal class TonTransactionDecoderTest {

    private val decoder = TonTransactionDecoder()

    private val owner = "EQBfwesEQte6-OnnVoRroXg2Fhs5kKQtfIITGP22CG98-SSR"
    private val jettonWallet = "EQBiuk7kPCTMhLvpPJvGoccFY7pA6b4gSdmZ0NvMYi_JlPzJ"
    private val nativeCoin = Coins.Ton.TON.copy(address = owner)

    private val depositMessage =
        TonMessage(
            to = Tonstakers.POOL_ADDRESS,
            amount = "6000000000",
            payload = Tonstakers.depositBody(),
        )

    private val burnMessage =
        TonMessage(
            to = jettonWallet,
            amount = Tonstakers.UNSTAKE_ATTACHED_VALUE.toString(),
            payload = Tonstakers.burnBody(BigInteger.valueOf(4_300_000_000L), owner),
        )

    @Test
    fun `a deposit to the pool reads as a stake of the full transfer value`() {
        val decoded = decoder.decode(payload(depositMessage).asSignedTransactionContent())

        assertEquals(
            DecodedTransaction(
                operation = DecodedOperation.Stake,
                amount =
                    DecodedAmount.Units(
                        BigInteger.valueOf(6_000_000_000L),
                        DecodedAsset.ChainNative,
                    ),
                counterparty = DecodedCounterparty.Pool(Tonstakers.POOL_ADDRESS),
                evidence = DecodedEvidence.SignedData,
            ),
            decoded,
        )
    }

    @Test
    fun `the initiator reads the same stake out of its deposit transaction`() {
        val transaction =
            DepositTransaction(
                id = "tx",
                vaultId = "vault",
                srcToken = nativeCoin,
                srcAddress = owner,
                srcTokenValue = TokenValue(BigInteger.valueOf(6_000_000_000L), nativeCoin),
                memo = "",
                dstAddress = Tonstakers.POOL_ADDRESS,
                estimatedFees = TokenValue(BigInteger.valueOf(50_000_000L), nativeCoin),
                estimateFeesFiat = "",
                blockChainSpecific = TON_SPECIFIC,
                operation = OPERATION_TONSTAKERS_STAKE,
                signTon = SignTon(tonMessages = listOf(depositMessage)),
            )

        val decoded = decoder.decode(transaction.asSignedTransactionContent())

        assertEquals(DecodedOperation.Stake, decoded?.operation)
        assertEquals(
            DecodedAmount.Units(BigInteger.valueOf(6_000_000_000L), DecodedAsset.ChainNative),
            decoded?.amount,
        )
        assertEquals(DecodedEvidence.SignedData, decoded?.evidence)
    }

    @Test
    fun `the deposit op addressed anywhere but the pool is not a stake`() {
        val elsewhere = depositMessage.copy(to = jettonWallet)

        assertNull(decoder.decode(payload(elsewhere).asSignedTransactionContent()))
    }

    @Test
    fun `a burn carrying the withdrawal flags reads as an unstake with no stated amount`() {
        val decoded = decoder.decode(payload(burnMessage).asSignedTransactionContent())

        assertEquals(
            DecodedTransaction(
                operation = DecodedOperation.Unstake,
                // The burn names its jetton only by wallet, which the bytes cannot resolve to a
                // ticker, so the figure is left to the surface that can look the wallet up.
                amount = DecodedAmount.Unstated,
                counterparty = DecodedCounterparty.Contract(jettonWallet),
                evidence = DecodedEvidence.SignedData,
            ),
            decoded,
        )
    }

    @Test
    fun `a plain jetton burn without the flags cell is not an unstake`() {
        // TEP-74 burn with no custom payload (maybe-bit clear), from @ton/core.
        val plainBurn =
            burnMessage.copy(
                payload =
                    "te6cckEBAQEANQAAZllfB7wAAAAAAAAAAFAQBMywCAC/g9YIha918dPOrQjXQvBsLDZzIUha+QQmMftsEN758sFLF30="
            )

        assertNull(decoder.decode(payload(plainBurn).asSignedTransactionContent()))
    }

    @Test
    fun `a multi-message batch is a dApp request the reader has no grammar for`() {
        val batch = payload(depositMessage, burnMessage)

        assertNull(decoder.decode(batch.asSignedTransactionContent()))
    }

    @Test
    fun `a nominator comment is still read when no batch is present`() {
        val comment =
            KeysignPayload(
                coin = nativeCoin,
                toAddress = "EQpool",
                toAmount = BigInteger.valueOf(51_000_000_000L),
                blockChainSpecific = TON_SPECIFIC,
                memo = "Deposit",
                vaultPublicKeyECDSA = "",
                vaultLocalPartyID = "",
                libType = null,
                wasmExecuteContractPayload = null,
            )

        val decoded = decoder.decode(comment.asSignedTransactionContent())

        assertEquals(DecodedOperation.Stake, decoded?.operation)
        assertEquals(DecodedEvidence.Memo, decoded?.evidence)
    }

    private fun payload(vararg messages: TonMessage): KeysignPayload =
        KeysignPayload(
            coin = nativeCoin,
            toAddress = messages.first().to,
            toAmount = BigInteger(messages.first().amount),
            blockChainSpecific = TON_SPECIFIC,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
            signTon = SignTon(tonMessages = messages.toList()),
        )

    private companion object {
        val TON_SPECIFIC =
            BlockChainSpecific.Ton(sequenceNumber = 1u, expireAt = 2u, bounceable = true)
    }
}
