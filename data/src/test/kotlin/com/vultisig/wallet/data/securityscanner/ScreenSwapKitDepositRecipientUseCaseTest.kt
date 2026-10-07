package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
 * The co-signer screens the recipient it decodes from a SwapKit ERC-20 deposit's signed calldata
 * and refuses only a confirmed Malicious verdict; anything else is an advisory for the review.
 *
 * `swapkit/v3-real-usdt-sol-swap.json` is the live `/v3/swap` reply (20 USDT -> SOL, NEAR provider)
 * captured 2026-10-03T14:59Z through api.vultisig.com/swapkit for vultisig-ios, copied verbatim
 * from its `VultisigAppTests/Swap/SwapKit/__fixtures__`; the curl sits in that suite's
 * `SwapKitErc20DepositTests.swift` header.
 */
internal class ScreenSwapKitDepositRecipientUseCaseTest {

    private val response: JsonObject =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResourceAsStream("/swapkit/v3-real-usdt-sol-swap.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            .jsonObject

    private val targetAddress = response.string("targetAddress").lowercase()

    @Test
    fun `screens the decoded recipient as a transfer to it and passes a Benign verdict`() =
        runTest {
            val scanner = FakeScanner(riskLevel = SecurityRiskLevel.NONE)

            val screen = ScreenSwapKitDepositRecipientUseCase(scanner)(depositPayload())

            screen.shouldBeInstanceOf<SwapKitDepositScreen.Scanned>()
            scanner.scanned.map { Triple(it.chain, it.type, it.to) } shouldBe
                listOf(Triple(Chain.Ethereum, SecurityTransactionType.COIN_TRANSFER, targetAddress))
        }

    @Test
    fun `passes a Warning verdict on as an advisory`() = runTest {
        val screen =
            ScreenSwapKitDepositRecipientUseCase(FakeScanner(riskLevel = SecurityRiskLevel.MEDIUM))(
                depositPayload()
            )

        screen.shouldBeInstanceOf<SwapKitDepositScreen.Scanned>().result.riskLevel shouldBe
            SecurityRiskLevel.MEDIUM
    }

    @Test
    fun `reports a recipient that cannot be screened as not scanned`() = runTest {
        val screen =
            ScreenSwapKitDepositRecipientUseCase(
                FakeScanner(failure = IOException("blockaid down"))
            )(depositPayload())

        screen.shouldBeInstanceOf<SwapKitDepositScreen.NotScanned>()
    }

    @Test
    fun `refuses a recipient with a Malicious verdict`() = runTest {
        shouldThrow<SwapKitDepositRecipientException> {
            ScreenSwapKitDepositRecipientUseCase(
                FakeScanner(riskLevel = SecurityRiskLevel.CRITICAL)
            )(depositPayload())
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

    private class FakeScanner(
        private val riskLevel: SecurityRiskLevel = SecurityRiskLevel.NONE,
        private val failure: Exception? = null,
    ) : SecurityScannerContract {
        val scanned = mutableListOf<SecurityScannerTransaction>()

        override suspend fun scanTransaction(
            transaction: SecurityScannerTransaction
        ): SecurityScannerResult {
            scanned += transaction
            failure?.let { throw it }
            return SecurityScannerResult(
                provider = BLOCKAID_PROVIDER,
                isSecure = riskLevel == SecurityRiskLevel.NONE,
                riskLevel = riskLevel,
                warnings = emptyList(),
                description = null,
                recommendations = "",
            )
        }

        override suspend fun isSecurityServiceEnabled(): Boolean = error("not used")

        override suspend fun createSecurityScannerTransaction(
            transaction: Transaction
        ): SecurityScannerTransaction = error("not used")

        override suspend fun createSecurityScannerTransaction(
            transaction: SwapTransaction
        ): SecurityScannerTransaction = error("not used")

        override fun createRecipientSecurityScannerTransaction(
            transaction: SwapTransaction
        ): SecurityScannerTransaction = error("not used")

        override fun getDisabledProviders(): List<String> = error("not used")

        override fun getEnabledProviders(): List<String> = error("not used")

        override fun disableProviders(providersToDisable: List<String>) = error("not used")

        override fun enableProviders(providersToEnable: List<String>) = error("not used")

        override fun getSupportedChainsByFeature(): List<SecurityScannerSupport> = error("not used")
    }
}
