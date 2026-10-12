package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonStatusResult
import com.vultisig.wallet.data.api.chains.ton.TransactionJson
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class RecordPaidNetworkFeeUseCaseTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk {
        every { createEvmApi(Chain.Ethereum) } returns evmApi
    }
    private val tonApi: TonApi = mockk()
    private val historyRepository: TransactionHistoryRepository = mockk(relaxed = true)
    private val useCase =
        RecordPaidNetworkFeeUseCaseImpl(
            FetchPaidNetworkFeeUseCaseImpl(evmApiFactory, tonApi),
            historyRepository,
        )

    /**
     * A swap receipt with 1,032,950 gas used of a 1,954,299 limit. The ceiling the row was recorded
     * with is the limit at the max fee, so the gas actually used must replace it.
     */
    @Test
    fun `stores gasUsed times effectiveGasPrice of the transaction's own receipt`() = runTest {
        receipt(gasUsed = 1_032_950, effectiveGasPrice = 875_487_000)

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 1) {
            historyRepository.recordPaidNetworkFee(
                "Ethereum",
                TX_HASH,
                BigInteger.valueOf(1_032_950L * 875_487_000L),
            )
        }
    }

    @Test
    fun `a receipt without effectiveGasPrice writes nothing`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns
            EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x1", gasUsed = "0x5208"))

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0x-1", "5208", "0x", "-0x5208", "0x52g8"])
    fun `a malformed gasUsed quantity writes nothing`(gasUsed: String) = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns
            EvmRpcResponseJson(
                id = 1,
                result =
                    EvmTxStatusJson(status = "0x1", gasUsed = gasUsed, effectiveGasPrice = "0x1"),
            )

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @Test
    fun `no receipt yet writes nothing`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns null

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    /**
     * The STON stake from the issue, as `/v3/transactionsByMessage` returned it: the row was
     * recorded with the fixed 0.05 TON reservation, the chain charged 0.000483935 TON.
     */
    @Test
    fun `stores the TON wallet transaction's total_fees`() = runTest {
        coEvery { tonApi.getTsStatus(TON_MSG_HASH) } returns
            Json { ignoreUnknownKeys = true }
                .decodeFromString<TonStatusResult>(TON_TRANSACTIONS_BY_MESSAGE)

        useCase(Chain.Ton, TON_MSG_HASH)

        coVerify(exactly = 1) {
            historyRepository.recordPaidNetworkFee("Ton", TON_MSG_HASH, BigInteger("483935"))
        }
    }

    @Test
    fun `a TON message not indexed yet writes nothing`() = runTest {
        coEvery { tonApi.getTsStatus(TON_MSG_HASH) } returns TonStatusResult()

        useCase(Chain.Ton, TON_MSG_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "-1", "0.0005", "4839e2"])
    fun `a malformed TON total_fees writes nothing`(totalFees: String) = runTest {
        coEvery { tonApi.getTsStatus(TON_MSG_HASH) } returns
            TonStatusResult(listOf(TransactionJson(totalFees = totalFees)))

        useCase(Chain.Ton, TON_MSG_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @Test
    fun `a chain without a readable paid fee reads nothing`() = runTest {
        useCase(Chain.Bitcoin, TX_HASH)

        coVerify(exactly = 0) { evmApiFactory.createEvmApi(any()) }
        coVerify(exactly = 0) { tonApi.getTsStatus(any()) }
        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    private fun receipt(gasUsed: Long, effectiveGasPrice: Long) {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns
            EvmRpcResponseJson(
                id = 1,
                result =
                    EvmTxStatusJson(
                        status = "0x1",
                        gasUsed = "0x" + gasUsed.toString(16),
                        effectiveGasPrice = "0x" + effectiveGasPrice.toString(16),
                    ),
            )
    }

    private companion object {
        const val TX_HASH = "0x4de28cc72aa6021f67f9bba3a07fa59620de15ed474377e19102686f9f623bae"
        const val TON_MSG_HASH = "5245f57f61788555d07204028fdb2dfa756c13feaff9a237d5cdcdec7c9debd6"

        /** Trimmed from the live response; unused fields dropped. */
        const val TON_TRANSACTIONS_BY_MESSAGE =
            """
            {
              "transactions": [
                {
                  "account": "0:A60409EF95AB55EB22D69B7F7504415358FEE3657E665052780DCE532409EF56",
                  "hash": "gnZlyFCWZetygBa+e2jve4+egnLXM97aSHtgfg5zZMk=",
                  "lt": "105537821000004",
                  "now": 1790232798,
                  "total_fees": "483935",
                  "description": {
                    "aborted": false,
                    "destroyed": false,
                    "compute_ph": { "success": true, "exit_code": 0 },
                    "action": { "success": true, "result_code": 0, "skipped_actions": 0 }
                  },
                  "in_msg": { "hash": "UkX1f2F4hVXQcgQCj9st+nVsE/6v+aI31c3N7Hyd69Y=", "source": null }
                }
              ]
            }
            """
    }
}
