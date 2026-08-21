package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoPriorityFee
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRelayedIntent
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRentReserve
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ThorchainMemoParser
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapperImpl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSolana

/**
 * What a co-signing device shows for a Kamino Earn transaction (issue #5644): the same operation,
 * vault and network fee the initiating device shows, rather than a plain send priced as a transfer.
 */
internal class JoinDepositKaminoTest {

    private val vault = KaminoVaultRegistry.ALLEZ_SOL

    private val solCoin =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "sol",
            address = SIGNER,
            decimal = 9,
            hexPublicKey = "hex",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    private val gasFeeToEstimatedFee = mockk<GasFeeToEstimatedFeeUseCase>()
    private val tokenRepository = mockk<TokenRepository>()
    private val vaultRepository = mockk<VaultRepository>()
    private val rentReserve = mockk<KaminoRentReserve>()
    private val feeResolver = mockk<JoinKeysignFeeResolver>()
    private val mapDepositTransactionToUiModel = mockk<DepositTransactionToUiModelMapper>()
    private val mapDepositTransactionHistoryData = mockk<DepositTransactionHistoryDataMapper>()

    private val captured = slot<DepositTransaction>()

    private val builder =
        JoinDepositUiModelBuilder(
            tokenRepository = tokenRepository,
            vaultRepository = vaultRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            mapDepositTransactionToUiModel = mapDepositTransactionToUiModel,
            mapDepositTransactionHistoryData = mapDepositTransactionHistoryData,
            thorchainMemoParser = mockk<ThorchainMemoParser>(relaxed = true),
            addressBookRepository = mockk<AddressBookRepository>(relaxed = true),
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            feeResolver = feeResolver,
            rentReserve = rentReserve,
            mapTokenValueToDecimalUiString = TokenValueToDecimalUiStringMapperImpl(),
        )

    private suspend fun build(intent: KaminoRelayedIntent, toAddress: String): DepositTransaction {
        buildResult(intent, toAddress)
        return captured.captured
    }

    private suspend fun buildResult(
        intent: KaminoRelayedIntent?,
        toAddress: String,
    ): JoinKeysignVerifyResult {
        val storedVault = mockk<Vault>(relaxed = true)
        coEvery { vaultRepository.get(VAULT_ID) } returns storedVault
        coEvery { vaultRepository.getAll() } returns emptyList()
        coEvery { tokenRepository.getNativeToken(any()) } returns solCoin
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedFiatValue = "$0.90",
                formattedTokenValue = "0.0117 SOL",
                tokenValue = TokenValue(BigInteger.ONE, solCoin),
                fiatValue = mockk(relaxed = true),
            )
        coEvery { mapDepositTransactionToUiModel(capture(captured)) } returns
            DepositTransactionUiModel()
        coEvery { mapDepositTransactionHistoryData(any()) } returns mockk(relaxed = true)

        return builder.build(payload(toAddress), VAULT_ID, intent)
    }

    @Test
    fun `a deposit is labelled as one, names its vault and carries its bytes`() = runTest {
        coEvery { rentReserve(vault, SIGNER, KaminoAction.DEPOSIT) } returns RENT

        val deposit = build(intent(KaminoAction.DEPOSIT), toAddress = vault.address)

        deposit.operation shouldBe "KaminoDeposit"
        deposit.validatorName shouldBe "Allez SOL"
        deposit.dstAddress shouldBe vault.address
        // The amount the initiating device shows: the deposit principal, not the whole native-SOL
        // debit a simulation of these bytes reports.
        deposit.srcTokenValue.value shouldBe AMOUNT
        deposit.signSolana?.rawTransactions shouldBe listOf(RAW)
    }

    @Test
    fun `a deposit is priced off the relayed compute budget and its account rent`() = runTest {
        coEvery { rentReserve(vault, SIGNER, KaminoAction.DEPOSIT) } returns RENT

        val deposit = build(intent(KaminoAction.DEPOSIT), toAddress = vault.address)

        // 1,000,000 base + (250,000 µlamports × 350,000 units / 1e6) + rent — the initiating
        // device's own figure, not a transfer-shaped re-estimate.
        deposit.estimatedFees.value shouldBe BigInteger.valueOf(1_087_500) + RENT
        deposit.estimatedFees.unit shouldBe "SOL"
        coVerify(exactly = 0) {
            feeResolver.resolveJoinKeysignNetworkFee(any(), any(), any(), any())
        }
    }

    @Test
    fun `a withdraw is labelled as one and spends no rent`() = runTest {
        val deposit = build(intent(KaminoAction.WITHDRAW), toAddress = SIGNER)

        deposit.operation shouldBe "KaminoWithdraw"
        // 1,000,000 base + (250,000 × 400,000 / 1e6). A withdraw creates no account of its own.
        deposit.estimatedFees.value shouldBe BigInteger.valueOf(1_100_000)
        coVerify(exactly = 0) { rentReserve(any(), any(), any()) }
    }

    @Test
    fun `a withdraw names the share count its instruction carries`() = runTest {
        // The amount above it is a token projection at a rate that never crossed the wire, and no
        // Blockaid badge qualifies it — so the screen says which figure did come from the bytes.
        val result = buildResult(intent(KaminoAction.WITHDRAW), toAddress = SIGNER)

        // 500,000 at the vault's six share decimals, which are not the token's nine.
        result.transactionTypeUiModel
            .let { it as TransactionTypeUiModel.Deposit }
            .depositTransactionUiModel
            .unverifiedWithdrawShares shouldBe "0.5"
    }

    @Test
    fun `a deposit's amount was checked against the bytes, so nothing is disclaimed`() = runTest {
        coEvery { rentReserve(vault, SIGNER, KaminoAction.DEPOSIT) } returns RENT

        val result = buildResult(intent(KaminoAction.DEPOSIT), toAddress = vault.address)

        result.transactionTypeUiModel
            .let { it as TransactionTypeUiModel.Deposit }
            .depositTransactionUiModel
            .unverifiedWithdrawShares shouldBe null
    }

    @Test
    fun `a Solana payload that is not a recognised Kamino transaction is refused`() = runTest {
        // Bytes nobody could read are still a send. Rendering them as a deposit would put a
        // deposit's framing — an operation, a vault name — around a transaction this device cannot
        // describe, which is the shape issue #5644 reported in the first place.
        shouldThrow<IllegalArgumentException> { buildResult(intent = null, toAddress = SIGNER) }
    }

    private fun intent(action: KaminoAction) =
        KaminoRelayedIntent(
            vault = vault,
            action = action,
            amount = if (action == KaminoAction.DEPOSIT) AMOUNT else BigInteger.valueOf(500_000),
            priorityFee =
                KaminoPriorityFee(
                    limit =
                        BigInteger.valueOf(
                            if (action == KaminoAction.DEPOSIT) 350_000 else 400_000
                        ),
                    price = BigInteger.valueOf(250_000),
                ),
        )

    private fun payload(toAddress: String) =
        KeysignPayload(
            coin = solCoin,
            toAddress = toAddress,
            toAmount = AMOUNT,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "hash",
                    priorityFee = BigInteger.valueOf(250_000),
                    priorityLimit = BigInteger.valueOf(350_000),
                ),
            vaultPublicKeyECDSA = "pubkey",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
            signSolana = SignSolana(rawTransactions = listOf(RAW)),
        )

    private companion object {
        const val VAULT_ID = "vault-id"
        const val SIGNER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        const val RAW = "relayed-transaction"

        val AMOUNT: BigInteger = BigInteger.valueOf(429_219_030)
        val RENT: BigInteger = BigInteger.valueOf(10_377_640)
    }
}
