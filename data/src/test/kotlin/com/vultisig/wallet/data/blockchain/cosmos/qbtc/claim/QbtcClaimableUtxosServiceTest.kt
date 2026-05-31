package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.api.models.BlockChairAddress
import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairUtxoInfo
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QbtcClaimableUtxosServiceTest {

    @Test
    fun `maps blockchair utxos and drops malformed rows`() = runTest {
        val info =
            BlockChairInfo(
                address = BlockChairAddress(balance = 0, unspentOutputCount = 0),
                utxos =
                    listOf(
                        BlockChairUtxoInfo(
                            "aa".repeat(32),
                            index = 3,
                            value = 100_000,
                            blockId = 1_000_142,
                        ),
                        BlockChairUtxoInfo(
                            "bb".repeat(32),
                            index = 0,
                            value = 300_000,
                            blockId = 0,
                        ), // unconfirmed
                        BlockChairUtxoInfo(
                            "",
                            index = 1,
                            value = 50_000,
                            blockId = 5,
                        ), // empty txid
                        BlockChairUtxoInfo(
                            "cc".repeat(32),
                            index = -1,
                            value = 50_000,
                            blockId = 5,
                        ), // bad index
                        BlockChairUtxoInfo(
                            "dd".repeat(32),
                            index = 2,
                            value = -1,
                            blockId = 5,
                        ), // bad value
                    ),
            )
        val service = QbtcClaimableUtxosServiceImpl(FakeBlockChairApi(info))

        val result = service.fetchClaimableCandidates("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")

        assertEquals(
            listOf(
                ClaimableUtxo(
                    txid = "aa".repeat(32),
                    vout = 3,
                    amount = 100_000,
                    blockHeight = 1_000_142,
                ),
                ClaimableUtxo(
                    txid = "bb".repeat(32),
                    vout = 0,
                    amount = 300_000,
                    blockHeight = null,
                ),
            ),
            result,
        )
    }

    @Test
    fun `returns empty when blockchair has no data`() = runTest {
        val result =
            QbtcClaimableUtxosServiceImpl(FakeBlockChairApi(null)).fetchClaimableCandidates("addr")
        assertEquals(emptyList<ClaimableUtxo>(), result)
    }

    private class FakeBlockChairApi(private val info: BlockChairInfo?) : BlockChairApi {
        override suspend fun getAddressInfo(chain: Chain, address: String): BlockChairInfo? = info

        override suspend fun getBlockChairStats(chain: Chain): BigInteger = BigInteger.ZERO

        override suspend fun broadcastTransaction(chain: Chain, signedTransaction: String): String =
            ""

        override suspend fun getTsStatus(
            chain: Chain,
            txHash: String,
        ): BlockChainStatusDeserialized? = null
    }
}
