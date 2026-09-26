package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The deposit dispatch is what sets `KeysignFlowViewModel.isDataLoaded`, which the initiator's
 * peer-discovery screen gates its automatic keysign start on. A deposit whose chain-specific block
 * does not carry the deposit flag — a Tonstakers stake/unstake, which rides `signTon` so the
 * co-signer can render it from the payload — must still resolve from the deposit repository the
 * route already loaded it from, or the initiator never auto-starts and the transaction never
 * reaches history.
 */
internal class BuildKeysignTransactionUiModelDepositTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val depositTransactionRepository: DepositTransactionRepository = mockk()
    private val mapDepositTransactionUiModel: DepositTransactionToUiModelMapper = mockk()
    private val mapTransactionToUiModel: TransactionToUiModelMapper = mockk()
    private val sendHistoryMapper: SendTransactionHistoryDataMapper = mockk()
    private val depositHistoryMapper: DepositTransactionHistoryDataMapper = mockk()

    private val useCase =
        BuildKeysignTransactionUiModelUseCase(
            transactionRepository = transactionRepository,
            depositTransactionRepository = depositTransactionRepository,
            swapTransactionRepository = mockk<SwapTransactionRepository>(relaxed = true),
            mapTransactionToUiModel = mapTransactionToUiModel,
            mapDepositTransactionUiModel = mapDepositTransactionUiModel,
            mapSwapTransactionToUiModel = mockk<SwapTransactionToUiModelMapper>(relaxed = true),
            transactionHistoryDataMapper = sendHistoryMapper,
            depositTransactionHistoryDataMapper = depositHistoryMapper,
            swapTransactionToHistoryDataMapper =
                mockk<SwapTransactionToHistoryDataMapper>(relaxed = true),
        )

    @Test
    fun `a deposit route resolves from the deposit repository even without the specific flag`() =
        runTest {
            val deposit = mockk<DepositTransaction>()
            val uiModel = mockk<DepositTransactionUiModel>()
            coEvery { depositTransactionRepository.getTransaction(TX_ID) } returns deposit
            coEvery { mapDepositTransactionUiModel(deposit) } returns uiModel
            coEvery { depositHistoryMapper(uiModel) } returns mockk<SendTransactionHistoryData>()
            // The send repository does not hold it — this is what returned null before.
            coEvery { transactionRepository.getTransaction(TX_ID) } returns null

            val result =
                useCase(
                    keysignPayload = tonstakersPayload(),
                    txType = Route.Keysign.Keysign.TxType.Deposit,
                    transactionId = TX_ID,
                )

            result?.transactionTypeUiModel.shouldBeInstanceOf<TransactionTypeUiModel.Deposit>()
        }

    @Test
    fun `a plain send is still a send`() = runTest {
        val send = mockk<Transaction>(relaxed = true)
        coEvery { transactionRepository.getTransaction(TX_ID) } returns send
        val sendUiModel = mockk<TransactionDetailsUiModel>()
        coEvery { mapTransactionToUiModel(send) } returns sendUiModel
        coEvery { sendHistoryMapper(sendUiModel) } returns mockk<SendTransactionHistoryData>()

        val result =
            useCase(
                keysignPayload = tonstakersPayload(),
                txType = Route.Keysign.Keysign.TxType.Send,
                transactionId = TX_ID,
            )

        result?.transactionTypeUiModel.shouldBeInstanceOf<TransactionTypeUiModel.Send>()
    }

    private fun tonstakersPayload() =
        KeysignPayload(
            coin = Coins.Ton.TON,
            toAddress = "EQtonstakers",
            toAmount = BigInteger.valueOf(2_000_000_000),
            blockChainSpecific =
                BlockChainSpecific.Ton(
                    sequenceNumber = 1UL,
                    expireAt = 0UL,
                    bounceable = true,
                    // What buildTonstakersStakeTransaction sends: the co-signer routes a signTon
                    // payload through its send builder, so the flag stays false.
                    isDeposit = false,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val TX_ID = "tx-1"
    }
}
