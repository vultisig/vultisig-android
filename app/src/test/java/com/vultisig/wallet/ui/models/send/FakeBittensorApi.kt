package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.TaostatsExtrinsicData
import java.math.BigInteger

/**
 * Stub [BittensorApi] that only answers `getBalance` — the sole call the send validators make — and
 * counts the lookups so a test can assert the destination check stays off the RPC.
 */
internal class FakeBittensorApi(
    private val balance: BigInteger = BigInteger.ZERO,
    private val balanceError: Exception? = null,
) : BittensorApi {
    var balanceLookups = 0
        private set

    override suspend fun getBalance(address: String): BigInteger {
        balanceLookups++
        balanceError?.let { throw it }
        return balance
    }

    override suspend fun getNonce(address: String): BigInteger = error("not used by these tests")

    override suspend fun getBlockHash(isGenesis: Boolean): String = error("not used by these tests")

    override suspend fun getBlockHashForNumber(blockNumber: BigInteger): String =
        error("not used by these tests")

    override suspend fun getGenesisBlockHash(): String = error("not used by these tests")

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> =
        error("not used by these tests")

    override suspend fun getBlockHeader(): BigInteger = error("not used by these tests")

    override suspend fun broadcastTransaction(tx: String): String? =
        error("not used by these tests")

    override suspend fun getPartialFee(tx: String): BigInteger = error("not used by these tests")

    override suspend fun getTxStatus(txHash: String): TaostatsExtrinsicData? =
        error("not used by these tests")
}
