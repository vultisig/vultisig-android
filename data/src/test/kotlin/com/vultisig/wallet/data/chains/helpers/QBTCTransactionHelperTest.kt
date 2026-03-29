@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.TransactionType

class QBTCTransactionHelperTest {

    private val helper = QBTCTransactionHelper()

    private val testPubKeyHex = "ab".repeat(1312)

    private val nativeCoin =
        Coin(
            chain = Chain.Qbtc,
            ticker = "QBTC",
            logo = "qbtc",
            address = "qbtc1sender1234567890abcdef",
            decimal = 8,
            hexPublicKey = testPubKeyHex,
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private val tokenCoin =
        Coin(
            chain = Chain.Qbtc,
            ticker = "USDT",
            logo = "usdt",
            address = "qbtc1sender1234567890abcdef",
            decimal = 6,
            hexPublicKey = testPubKeyHex,
            priceProviderID = "",
            contractAddress =
                "ibc/27394FB092D2ECCD56123C74F36E4C1F926001CEADA9CA97EA622B25F41E5EB2",
            isNativeToken = false,
        )

    private fun cosmosSpecific(
        accountNumber: BigInteger = BigInteger("42"),
        sequence: BigInteger = BigInteger("5"),
        gas: BigInteger = BigInteger("7500"),
        transactionType: TransactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
        ibcDenomTrace: CosmosIbcDenomTrace? = null,
    ) =
        BlockChainSpecific.Cosmos(
            accountNumber = accountNumber,
            sequence = sequence,
            gas = gas,
            ibcDenomTraces = ibcDenomTrace,
            transactionType = transactionType,
        )

    private fun payload(
        coin: Coin = nativeCoin,
        toAddress: String = "qbtc1receiver9876543210fedcba",
        toAmount: BigInteger = BigInteger("1000000"),
        memo: String? = null,
        blockChainSpecific: BlockChainSpecific = cosmosSpecific(),
    ) =
        KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific = blockChainSpecific,
            memo = memo,
            vaultPublicKeyECDSA = "ecdsa_pub",
            vaultLocalPartyID = "party1",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `getPreSignedImageHash returns single SHA-256 hash`() {
        val hashes = helper.getPreSignedImageHash(payload())
        assertEquals(1, hashes.size)
        assertEquals(64, hashes[0].length)
    }

    @Test
    fun `getPreSignedImageHash is deterministic`() {
        val p = payload()
        assertEquals(helper.getPreSignedImageHash(p), helper.getPreSignedImageHash(p))
    }

    @Test
    fun `different toAddress produces different hash`() {
        assertNotEquals(
            helper.getPreSignedImageHash(payload(toAddress = "qbtc1addr1")),
            helper.getPreSignedImageHash(payload(toAddress = "qbtc1addr2")),
        )
    }

    @Test
    fun `different amount produces different hash`() {
        assertNotEquals(
            helper.getPreSignedImageHash(payload(toAmount = BigInteger("100"))),
            helper.getPreSignedImageHash(payload(toAmount = BigInteger("200"))),
        )
    }

    @Test
    fun `different account number produces different hash`() {
        assertNotEquals(
            helper.getPreSignedImageHash(
                payload(blockChainSpecific = cosmosSpecific(accountNumber = BigInteger("1")))
            ),
            helper.getPreSignedImageHash(
                payload(blockChainSpecific = cosmosSpecific(accountNumber = BigInteger("2")))
            ),
        )
    }

    @Test
    fun `different sequence produces different hash`() {
        assertNotEquals(
            helper.getPreSignedImageHash(
                payload(blockChainSpecific = cosmosSpecific(sequence = BigInteger("0")))
            ),
            helper.getPreSignedImageHash(
                payload(blockChainSpecific = cosmosSpecific(sequence = BigInteger("1")))
            ),
        )
    }

    @Test
    fun `memo changes the hash`() {
        assertNotEquals(
            helper.getPreSignedImageHash(payload(memo = null)),
            helper.getPreSignedImageHash(payload(memo = "test memo")),
        )
    }

    @Test
    fun `non-native token hash differs from native`() {
        assertNotEquals(
            helper.getPreSignedImageHash(payload(coin = nativeCoin)),
            helper.getPreSignedImageHash(payload(coin = tokenCoin)),
        )
    }

    @Test
    fun `IBC transfer produces valid hash`() {
        val p =
            payload(
                memo = "ibc:channel-0:qbtc1receiver:optional_memo",
                blockChainSpecific =
                    cosmosSpecific(
                        transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER,
                        ibcDenomTrace =
                            CosmosIbcDenomTrace(
                                path = "transfer/channel-0",
                                baseDenom = "qbtc",
                                latestBlock = "1_1234567890000000000",
                            ),
                    ),
            )
        val hashes = helper.getPreSignedImageHash(p)
        assertEquals(1, hashes.size)
        assertEquals(64, hashes[0].length)
    }

    @Test
    fun `IBC transfer hash differs from regular send`() {
        val sendHash = helper.getPreSignedImageHash(payload())
        val ibcHash =
            helper.getPreSignedImageHash(
                payload(
                    memo = "ibc:channel-0:qbtc1receiver:memo",
                    blockChainSpecific =
                        cosmosSpecific(
                            transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER,
                            ibcDenomTrace =
                                CosmosIbcDenomTrace(
                                    path = "transfer/channel-0",
                                    baseDenom = "qbtc",
                                    latestBlock = "1_1234567890000000000",
                                ),
                        ),
                )
            )
        assertNotEquals(sendHash, ibcHash)
    }

    @Test
    fun `vote produces valid hash`() {
        val hashes =
            helper.getPreSignedImageHash(
                payload(
                    memo = "QBTC_VOTE:YES:42",
                    blockChainSpecific =
                        cosmosSpecific(transactionType = TransactionType.TRANSACTION_TYPE_VOTE),
                )
            )
        assertEquals(1, hashes.size)
        assertEquals(64, hashes[0].length)
    }

    @Test
    fun `vote hash differs from regular send`() {
        assertNotEquals(
            helper.getPreSignedImageHash(payload()),
            helper.getPreSignedImageHash(
                payload(
                    memo = "QBTC_VOTE:YES:1",
                    blockChainSpecific =
                        cosmosSpecific(transactionType = TransactionType.TRANSACTION_TYPE_VOTE),
                )
            ),
        )
    }

    @Test
    fun `different vote options produce different hashes`() {
        fun voteHash(option: String) =
            helper
                .getPreSignedImageHash(
                    payload(
                        memo = "QBTC_VOTE:$option:1",
                        blockChainSpecific =
                            cosmosSpecific(transactionType = TransactionType.TRANSACTION_TYPE_VOTE),
                    )
                )[0]

        val allHashes =
            setOf(voteHash("YES"), voteHash("NO"), voteHash("ABSTAIN"), voteHash("NO_WITH_VETO"))
        assertEquals(4, allHashes.size)
    }

    @Test
    fun `different proposal IDs produce different hashes`() {
        fun voteHash(id: Int) =
            helper
                .getPreSignedImageHash(
                    payload(
                        memo = "QBTC_VOTE:YES:$id",
                        blockChainSpecific =
                            cosmosSpecific(transactionType = TransactionType.TRANSACTION_TYPE_VOTE),
                    )
                )[0]

        assertNotEquals(voteHash(1), voteHash(2))
    }

    @Test
    fun `zero amount produces valid hash`() {
        assertEquals(1, helper.getPreSignedImageHash(payload(toAmount = BigInteger.ZERO)).size)
    }

    @Test
    fun `large amount produces valid hash`() {
        assertEquals(
            1,
            helper.getPreSignedImageHash(payload(toAmount = BigInteger("999999999999999999"))).size,
        )
    }

    @Test
    fun `null and empty memo are equivalent`() {
        assertEquals(
            helper.getPreSignedImageHash(payload(memo = null)),
            helper.getPreSignedImageHash(payload(memo = "")),
        )
    }

    @Test
    fun `large sequence and account number produce valid hash`() {
        assertEquals(
            1,
            helper
                .getPreSignedImageHash(
                    payload(
                        blockChainSpecific =
                            cosmosSpecific(
                                accountNumber = BigInteger("999999999"),
                                sequence = BigInteger("999999999"),
                            )
                    )
                )
                .size,
        )
    }

    @Test
    fun `throws for non-Cosmos blockChainSpecific`() {
        assertThrows<IllegalStateException> {
            helper.getPreSignedImageHash(
                KeysignPayload(
                    coin = nativeCoin,
                    toAddress = "qbtc1receiver",
                    toAmount = BigInteger("100"),
                    blockChainSpecific =
                        BlockChainSpecific.THORChain(
                            accountNumber = BigInteger("1"),
                            sequence = BigInteger("0"),
                            fee = BigInteger("2000000"),
                            isDeposit = false,
                            transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                        ),
                    vaultPublicKeyECDSA = "ecdsa_pub",
                    vaultLocalPartyID = "party1",
                    libType = SigningLibType.DKLS,
                    wasmExecuteContractPayload = null,
                )
            )
        }
    }

    @Test
    fun `hash is valid lowercase hex`() {
        assertTrue(helper.getPreSignedImageHash(payload())[0].matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `gas limit is 300000 for large MLDSA signatures`() {
        assertEquals(300_000L, QBTCTransactionHelper.GAS_LIMIT)
    }

    @Test
    fun `unspecified transaction type equals default`() {
        assertEquals(
            helper.getPreSignedImageHash(
                payload(
                    blockChainSpecific =
                        cosmosSpecific(
                            transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED
                        )
                )
            ),
            helper.getPreSignedImageHash(payload()),
        )
    }

    @Test
    fun `all three message types produce unique hashes`() {
        val sendHash = helper.getPreSignedImageHash(payload(memo = "test"))[0]
        val ibcHash =
            helper
                .getPreSignedImageHash(
                    payload(
                        memo = "ibc:channel-0:qbtc1receiver:test",
                        blockChainSpecific =
                            cosmosSpecific(
                                transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER,
                                ibcDenomTrace =
                                    CosmosIbcDenomTrace(
                                        path = "transfer/channel-0",
                                        baseDenom = "qbtc",
                                        latestBlock = "1_1234567890000000000",
                                    ),
                            ),
                    )
                )[0]
        val voteHash =
            helper
                .getPreSignedImageHash(
                    payload(
                        memo = "QBTC_VOTE:YES:1",
                        blockChainSpecific =
                            cosmosSpecific(transactionType = TransactionType.TRANSACTION_TYPE_VOTE),
                    )
                )[0]

        assertEquals(3, setOf(sendHash, ibcHash, voteHash).size)
    }
}
