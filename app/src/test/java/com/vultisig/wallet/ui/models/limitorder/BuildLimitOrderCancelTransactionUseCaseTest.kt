package com.vultisig.wallet.ui.models.limitorder

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class BuildLimitOrderCancelTransactionUseCaseTest {

    private val thorChainApi: ThorChainApi = mockk()
    private val accountsRepository: AccountsRepository = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()
    private val depositGasFeeHelper: DepositGasFeeHelper = mockk()

    private val useCase =
        BuildLimitOrderCancelTransactionUseCase(
            thorChainApi = thorChainApi,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            depositGasFeeHelper = depositGasFeeHelper,
        )

    private val btc = Coins.Bitcoin.BTC.copy(address = BTC_ADDRESS)

    @Test
    fun `a UTXO cancel shows the planned fee, not the per-byte rate`() = runTest {
        val pricedFee = slot<TokenValue>()
        givenBitcoinCancel(pricedFee)

        val tx = useCase.build(VAULT_ID, order())

        assertEquals(BigInteger.valueOf(PLAN_FEE), tx.estimatedFees.value)
        assertEquals(BigInteger.valueOf(PLAN_FEE), pricedFee.captured.value)
    }

    private fun givenBitcoinCancel(pricedFee: io.mockk.CapturingSlot<TokenValue>) {
        val account =
            Account(
                token = btc,
                tokenValue = TokenValue(BigInteger.valueOf(1_000_000), btc),
                fiatValue = null,
                price = null,
            )
        every { accountsRepository.loadAddress(VAULT_ID, Chain.Bitcoin) } returns
            flowOf(
                Address(chain = Chain.Bitcoin, address = BTC_ADDRESS, accounts = listOf(account))
            )
        coEvery { accountsRepository.loadAccount(VAULT_ID, btc) } returns account
        coEvery {
            depositGasFeeHelper.calculateGasFee(VAULT_ID, Chain.Bitcoin, btc, BTC_ADDRESS)
        } returns TokenValue(BigInteger.valueOf(BYTE_FEE), btc)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(
                THORChainInboundAddress(
                    chain = "BTC",
                    address = BTC_INBOUND,
                    halted = false,
                    dustThreshold = "10000",
                )
            )
        coEvery {
            blockChainSpecificRepository.getSpecific(
                chain = Chain.Bitcoin,
                address = BTC_ADDRESS,
                token = btc,
                gasFee = any(),
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = false,
                dstAddress = BTC_INBOUND,
                tokenAmountValue = any(),
                memo = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.UTXO(
                    byteFee = BigInteger.valueOf(BYTE_FEE),
                    sendMaxAmount = false,
                )
            )
        coEvery {
            depositGasFeeHelper.getBitcoinTransactionPlan(any(), any(), any(), any(), any(), any())
        } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(PLAN_FEE)
                .setError(SigningError.OK)
                .addUtxos(Bitcoin.UnspentTransaction.getDefaultInstance())
                .build()
        coEvery {
            depositGasFeeHelper.getFeesFiatValue(Chain.Bitcoin, any(), capture(pricedFee), btc)
        } answers
            {
                EstimatedGasFee(
                    formattedTokenValue = "",
                    formattedFiatValue = "$1.00",
                    tokenValue = pricedFee.captured,
                    fiatValue = mockk(relaxed = true),
                )
            }
    }

    private fun order() =
        PendingLimitOrderEntity(
            inboundTxHash = "HASH",
            vaultId = VAULT_ID,
            sourceAsset = "BTC.BTC",
            sourceAmount = "100000",
            targetAsset = "THOR.RUNE",
            destAddr = "thor1dest",
            targetPrice = "100000",
            expiryBlocks = 14_400,
            createdAt = 0L,
            status = LimitOrderStatus.Pending.raw,
            sourceChain = Chain.Bitcoin.raw,
            sourceDecimals = 8,
            sourceAddress = BTC_ADDRESS,
            sourceAmount1e8 = "100000",
            tradeTarget = "4000000",
            sourceAssetFull = "BTC.BTC",
            targetAssetFull = "THOR.RUNE",
            expiryObservedAt = 1L,
        )

    private companion object {
        const val VAULT_ID = "vault"
        const val BTC_ADDRESS = "bc1qself"
        const val BTC_INBOUND = "bc1qinbound"
        const val BYTE_FEE = 10L
        const val PLAN_FEE = 2_840L
    }
}
