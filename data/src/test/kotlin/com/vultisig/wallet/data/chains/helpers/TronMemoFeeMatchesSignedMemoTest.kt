package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParameterJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.tron.TronFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Tron

/**
 * Estimating and signing must agree on whether a Tron transfer carries a memo: the chain burns the
 * memo fee only for a non-empty `raw_data.data`, so [TronFeeService] may price it exactly when
 * [TronHelper] writes one. Issue #5481 broke that — the freeze/unfreeze routing signal Vultisig
 * parks in the memo field was priced as a memo the signed contract never carries.
 */
class TronMemoFeeMatchesSignedMemoTest {

    private val tronApi: TronApi = mockk(relaxed = true)
    private val feeService = TronFeeService(tronApi)
    private val helper = TronHelper(CoinType.TRON, vaultHexPublicKey = "", vaultHexChainCode = "")

    @BeforeEach
    fun stubApi() {
        coEvery { tronApi.getChainParameters() } returns
            TronChainParametersJson(
                listOf(
                    TronChainParameterJson("getTransactionFee", 1000L),
                    TronChainParameterJson("getCreateAccountFee", 100000L),
                    TronChainParameterJson("getCreateNewAccountFeeInSystemContract", 1000000L),
                    TronChainParameterJson("getMemoFee", 1000000L),
                )
            )
        // Free bandwidth covers the whole transfer and the destination is already activated, so a
        // non-zero fee can only be the memo fee.
        coEvery { tronApi.getAccountResource(any()) } returns
            TronAccountResourceJson(freeNetLimit = 5_000L)
        coEvery { tronApi.getAccount(any()) } returns TronAccountJson(address = RECIPIENT)
    }

    @Test
    fun `the memo fee is priced exactly when the signed transaction carries a memo`() = runTest {
        listOf(
                Case("no memo", to = RECIPIENT, memo = null),
                Case("empty memo", to = RECIPIENT, memo = ""),
                Case("user memo", to = RECIPIENT, memo = "thanks for lunch"),
                Case("freeze bandwidth", to = SENDER, memo = "FREEZE:BANDWIDTH"),
                Case("freeze energy", to = SENDER, memo = "FREEZE:ENERGY"),
                Case("unfreeze bandwidth", to = SENDER, memo = "UNFREEZE:BANDWIDTH"),
                Case("unfreeze energy", to = SENDER, memo = "UNFREEZE:ENERGY"),
                // Near-misses of the routing signal: the helper builds a plain transfer and writes
                // the memo, so the fee must keep charging for it.
                Case("lowercase lookalike", to = SENDER, memo = "freeze:bandwidth"),
                Case("no resource", to = SENDER, memo = "FREEZE:"),
                Case("trailing space", to = SENDER, memo = "FREEZE:BANDWIDTH "),
                Case("unknown resource", to = SENDER, memo = "FREEZE:TRON_POWER"),
            )
            .forEach { case ->
                val signed =
                    Tron.SigningInput.parseFrom(helper.getPreSignedInputData(payload(case)))
                        .transaction
                        .memo
                val fee = feeService.calculateFees(transfer(case)).amount

                assertEquals(
                    signed.isNotEmpty(),
                    fee > BigInteger.ZERO,
                    "${case.name}: signed memo=\"$signed\" but fee=$fee",
                )
            }
    }

    @Test
    fun `a staking memo aimed at another address is rejected before it can be signed`() {
        // The one input the table above cannot cover: no transaction exists to compare a fee
        // against, which is why the shared predicate keeps the self-address clause.
        assertThrows<IllegalStateException> {
            helper.getPreSignedInputData(
                payload(Case("foreign destination", to = RECIPIENT, memo = "FREEZE:BANDWIDTH"))
            )
        }
    }

    private fun payload(case: Case) =
        KeysignPayload(
            coin = trx,
            toAddress = case.to,
            toAmount = AMOUNT,
            memo = case.memo,
            blockChainSpecific =
                BlockChainSpecific.Tron(
                    timestamp = 1_700_000_000_000uL,
                    expiration = 1_700_000_600_000uL,
                    blockHeaderTimestamp = 1_700_000_000_000uL,
                    blockHeaderNumber = 60_000_000uL,
                    blockHeaderVersion = 30uL,
                    blockHeaderTxTrieRoot = "00".repeat(32),
                    blockHeaderParentHash = "11".repeat(32),
                    blockHeaderWitnessAddress = "22".repeat(21),
                    gasFeeEstimation = 800_000uL,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
        )

    private fun transfer(case: Case) =
        Transfer(
            coin = trx,
            vault = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain"),
            amount = AMOUNT,
            to = case.to,
            memo = case.memo,
        )

    private data class Case(val name: String, val to: String, val memo: String?)

    private val trx =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = SENDER,
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private companion object {
        const val SENDER = "TSenderAddressBase58"
        const val RECIPIENT = "TRecipientAddressBase58"
        val AMOUNT: BigInteger = BigInteger.valueOf(1_000_000L)
    }
}
