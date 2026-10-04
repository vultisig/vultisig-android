package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidAddressScanResponseJson
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidEvmSimulationResponseJson
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSolanaSimulationResponseJson
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidTransactionScanResponseJson
import com.vultisig.wallet.data.securityscanner.blockaid.EthereumScanTransactionRequestJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * The co-signer screens the recipient it decodes from a SwapKit ERC-20 deposit's signed calldata,
 * as vultisig-sdk does: only a Benign Blockaid verdict signs.
 *
 * `swapkit/v3-real-usdt-sol-swap.json` is the live `/v3/swap` reply (20 USDT -> SOL, NEAR provider)
 * captured 2026-10-03T14:59Z through api.vultisig.com/swapkit for vultisig-ios, copied verbatim
 * from its `VultisigAppTests/Swap/SwapKit/__fixtures__`; the curl sits in that suite's
 * `SwapKitErc20DepositTests.swift` header. The verdict shapes follow the live `POST
 * api.vultisig.com/blockaid/v0/evm/address/scan` reply for its targetAddress (2026-10-04):
 * `{"result_type":"Benign","features":["This address has no history on this chain", …]}`.
 */
internal class SwapKitDepositRecipientScreenTest {

    private val response: JsonObject =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResourceAsStream("/swapkit/v3-real-usdt-sol-swap.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            .jsonObject

    private val targetAddress = response.string("targetAddress").lowercase()

    @Test
    fun `signs a deposit whose decoded recipient Blockaid calls Benign`() = runTest {
        val blockaid = FakeBlockaid(verdict = "Benign")

        SwapKitDepositRecipientScreen(blockaid)(depositPayload())

        blockaid.scanned shouldBe listOf(Chain.Ethereum to targetAddress)
    }

    @Test
    fun `refuses a deposit whose recipient gets a Warning verdict`() = runTest {
        shouldThrow<IllegalStateException> {
            SwapKitDepositRecipientScreen(FakeBlockaid(verdict = "Warning"))(depositPayload())
        }
    }

    @Test
    fun `refuses a deposit whose recipient cannot be screened`() = runTest {
        shouldThrow<IllegalStateException> {
            SwapKitDepositRecipientScreen(FakeBlockaid(failure = IOException("blockaid down")))(
                depositPayload()
            )
        }
    }

    private fun depositPayload(): KeysignPayload {
        val tx = response.getValue("tx").jsonObject
        val usdt = coin(Chain.Ethereum, "USDT", 6, contract = tx.string("to"))
        val swap =
            EVMSwapPayloadJson(
                fromCoin = usdt,
                toCoin = coin(Chain.Solana, "SOL", 9, contract = ""),
                fromAmount =
                    BigDecimal(response.string("sellAmount")).movePointRight(6).toBigIntegerExact(),
                toAmountDecimal = BigDecimal(response.string("expectedBuyAmount")),
                quote =
                    EVMSwapQuoteJson(
                        dstAmount = "0",
                        tx =
                            OneInchSwapTxJson(
                                from = tx.string("from"),
                                to = tx.string("to"),
                                gas = tx.hex("gas").toLong(),
                                data = tx.string("data"),
                                value = tx.hex("value").toString(),
                                gasPrice = tx.hex("gasPrice").toString(),
                            ),
                    ),
                provider = "SwapKit",
            )
        return KeysignPayload(
            coin = usdt,
            toAddress = targetAddress,
            toAmount = swap.fromAmount,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ONE,
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(210_000),
                ),
            swapPayload = SwapPayload.EVM(swap),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    private fun coin(chain: Chain, ticker: String, decimals: Int, contract: String) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = decimals,
            hexPublicKey = "pub",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = contract.isEmpty(),
        )

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.hex(key: String) = BigInteger(string(key).removePrefix("0x"), 16)

    private class FakeBlockaid(
        private val verdict: String = "Benign",
        private val failure: Exception? = null,
    ) : BlockaidRpcClientContract {
        val scanned = mutableListOf<Pair<Chain, String>>()

        override suspend fun scanEVMAddress(
            chain: Chain,
            address: String,
        ): BlockaidAddressScanResponseJson {
            scanned += chain to address
            failure?.let { throw it }
            return BlockaidAddressScanResponseJson(resultType = verdict, features = emptyList())
        }

        override suspend fun scanBitcoinTransaction(
            address: String,
            serializedTransaction: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanEVMTransaction(
            chain: Chain,
            from: String,
            to: String,
            amount: String,
            data: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanEVMTransactionBulk(
            chain: Chain,
            transactions: List<EthereumScanTransactionRequestJson.DataJson>,
        ): List<BlockaidTransactionScanResponseJson> = error("not used")

        override suspend fun scanSolanaTransaction(
            address: String,
            serializedMessage: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanSuiTransaction(
            address: String,
            serializedTransaction: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun simulateEvmTransaction(
            chain: Chain,
            from: String,
            to: String,
            amount: String,
            data: String,
        ): BlockaidEvmSimulationResponseJson = error("not used")

        override suspend fun simulateSolanaTransaction(
            address: String,
            rawTransactionsBase58: List<String>,
        ): BlockaidSolanaSimulationResponseJson = error("not used")
    }
}
