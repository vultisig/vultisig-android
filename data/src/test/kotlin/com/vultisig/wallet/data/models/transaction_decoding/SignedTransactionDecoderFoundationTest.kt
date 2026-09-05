package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

class SignedTransactionDecoderFoundationTest {

    private lateinit var decoder: SignedTransactionDecoder

    @BeforeEach
    fun setUp() {
        decoder = SignedTransactionDecoder()
        decoder.clear()
    }

    @Test
    fun testUnregisteredContentIsUnreadable() {
        val decoded = decoder.decode(StubContent())

        assertEquals(DecodedTransaction.unreadable, decoded)
        assertEquals(DecodedOperation.Unknown, decoded.operation)
        assertEquals(DecodedAmount.Unstated, decoded.amount)
        assertEquals(DecodedEvidence.Unread, decoded.evidence)
    }

    @Test
    fun testOpaqueSignedContentWithholdsEverySidecar() {
        val content = StubContent(stubHasOpaqueSignedContent = true)

        assertNull(content.corroborated)
    }

    @Test
    fun testCorroboratedContentKeepsCommittedFieldsTogether() {
        val content = StubContent(stubHasOpaqueSignedContent = false)
        val corroborated = content.corroborated

        assertEquals("destination", corroborated?.toAddress)
        assertEquals(SignedAmount.Committed(42.toBigInteger()), corroborated?.amount)
        assertEquals("memo", corroborated?.memo(MemoPrecedence.MemoIsInertWhenRoutedEarlier))
    }

    @Test
    fun testEvidenceStrengthFollowsSignedProvenance() {
        assertTrue(DecodedEvidence.SignedData.isNoWeaker(than = DecodedEvidence.Memo))
        assertTrue(DecodedEvidence.Memo.isNoWeaker(than = DecodedEvidence.StructuredPayload))
        assertTrue(!DecodedEvidence.Unread.isNoWeaker(than = DecodedEvidence.SignedData))
    }

    // MARK: - Decoder Registry Contract Tests

    @Test
    fun testDecoderRegistrationAndRetrieval() {
        val decoder1 = FakeDecoder("first", setOf(Chain.Bitcoin))
        val decoder2 = FakeDecoder("second", setOf(Chain.Ethereum))

        decoder.register(decoder1)
        decoder.register(decoder2)

        val decoders = decoder.getDecoders()
        assertEquals(2, decoders.size)
        assertEquals(decoder1, decoders[0])
        assertEquals(decoder2, decoders[1])
    }

    @Test
    fun testInsertionOrderIsPreserved() {
        val decoderA = FakeDecoder("A", setOf(Chain.Bitcoin))
        val decoderB = FakeDecoder("B", setOf(Chain.Ethereum))
        val decoderC = FakeDecoder("C", setOf(Chain.Solana))

        decoder.register(decoderA)
        decoder.register(decoderB)
        decoder.register(decoderC)

        val decoders = decoder.getDecoders()
        assertEquals(listOf(decoderA, decoderB, decoderC), decoders)
    }

    @Test
    fun testHandlesChainFiltering() {
        val bitcoinDecoder = FakeDecoder("btc", setOf(Chain.Bitcoin))
        val ethereumDecoder = FakeDecoder("eth", setOf(Chain.Ethereum))
        val universalDecoder = FakeDecoder("universal", null, shouldSucceed = true)

        decoder.register(bitcoinDecoder)
        decoder.register(ethereumDecoder)
        decoder.register(universalDecoder)

        val bitcoinContent = StubContent(contentChain = Chain.Bitcoin)
        val result = decoder.decode(bitcoinContent)

        // bitcoinDecoder should match and return first result
        assertEquals(DecodedOperation.Transfer, result.operation)
        assertEquals("btc", result.counterparty?.let { (it as DecodedCounterparty.Node).value })
    }

    @Test
    fun testFirstSuccessfulDecoderWins() {
        val failingDecoder = FakeDecoder("failing", setOf(Chain.Bitcoin), shouldSucceed = false)
        val succeedingDecoder =
            FakeDecoder("succeeding", setOf(Chain.Bitcoin), shouldSucceed = true)
        val thirdDecoder = FakeDecoder("third", setOf(Chain.Bitcoin), shouldSucceed = true)

        decoder.register(failingDecoder)
        decoder.register(succeedingDecoder)
        decoder.register(thirdDecoder)

        val content = StubContent(contentChain = Chain.Bitcoin)
        val result = decoder.decode(content)

        // succeedingDecoder should be selected, not thirdDecoder
        assertEquals(DecodedOperation.Transfer, result.operation)
        assertEquals(
            "succeeding",
            result.counterparty?.let { (it as DecodedCounterparty.Node).value },
        )
    }

    @Test
    fun testUnregisterRemovesDecoder() {
        val decoder1 = FakeDecoder("first", setOf(Chain.Bitcoin))
        val decoder2 = FakeDecoder("second", setOf(Chain.Ethereum))

        decoder.register(decoder1)
        decoder.register(decoder2)
        assertEquals(2, decoder.getDecoders().size)

        decoder.unregister(decoder1)
        assertEquals(1, decoder.getDecoders().size)
        assertEquals(decoder2, decoder.getDecoders()[0])
    }

    @Test
    fun testClearRemovesAllDecoders() {
        val decoder1 = FakeDecoder("first", setOf(Chain.Bitcoin))
        val decoder2 = FakeDecoder("second", setOf(Chain.Ethereum))
        val decoder3 = FakeDecoder("third", setOf(Chain.Solana))

        decoder.register(decoder1)
        decoder.register(decoder2)
        decoder.register(decoder3)
        assertEquals(3, decoder.getDecoders().size)

        decoder.clear()
        assertTrue(decoder.getDecoders().isEmpty())
    }

    @Test
    fun testDecoderSelectionRespectsPrecedence() {
        val firstRegistered =
            FakeDecoder("first", setOf(Chain.Bitcoin), operation = DecodedOperation.Transfer)
        val secondRegistered =
            FakeDecoder("second", setOf(Chain.Bitcoin), operation = DecodedOperation.Swap)

        decoder.register(firstRegistered)
        decoder.register(secondRegistered)

        val content = StubContent(contentChain = Chain.Bitcoin)
        val result = decoder.decode(content)

        // firstRegistered should be selected due to registration order
        assertEquals(DecodedOperation.Transfer, result.operation)
    }

    @Test
    fun testNullHandlesDecoderAcceptsAnyChain() {
        val universalDecoder = FakeDecoder("universal", null, shouldSucceed = true)
        val chainSpecificDecoder = FakeDecoder("specific", setOf(Chain.Bitcoin))

        decoder.register(chainSpecificDecoder)
        decoder.register(universalDecoder)

        // For Ethereum, only universalDecoder should match
        val ethereumContent = StubContent(contentChain = Chain.Ethereum)
        val result = decoder.decode(ethereumContent)
        assertEquals(
            "universal",
            result.counterparty?.let { (it as DecodedCounterparty.Node).value },
        )
    }

    @Test
    fun testThrowingDecoderSkippedInFavorOfNextDecoder() {
        val throwingDecoder = ThrowingDecoder(setOf(Chain.Bitcoin))
        val succeedingDecoder = FakeDecoder("succeeding", setOf(Chain.Bitcoin))

        decoder.register(throwingDecoder)
        decoder.register(succeedingDecoder)

        val content = StubContent(contentChain = Chain.Bitcoin)
        val result = decoder.decode(content)

        // Throwing decoder should be skipped, succeeding decoder should provide result
        assertEquals(DecodedOperation.Transfer, result.operation)
        assertEquals(
            "succeeding",
            result.counterparty?.let { (it as DecodedCounterparty.Node).value },
        )
    }

    @Test
    fun testAllThrowingDecodersFallbackToUnreadable() {
        val throwingDecoder1 = ThrowingDecoder(setOf(Chain.Bitcoin))
        val throwingDecoder2 = ThrowingDecoder(null) // Universal handler that throws

        decoder.register(throwingDecoder1)
        decoder.register(throwingDecoder2)

        val content = StubContent(contentChain = Chain.Bitcoin)
        val result = decoder.decode(content)

        // All decoders threw; should return unreadable
        assertEquals(DecodedTransaction.unreadable, result)
        assertEquals(DecodedOperation.Unknown, result.operation)
        assertEquals(DecodedEvidence.Unread, result.evidence)
    }

    @Test
    fun testThrowingDecoderDoesNotBlockOtherChains() {
        val throwingDecoder = ThrowingDecoder(setOf(Chain.Bitcoin))
        val ethereumDecoder = FakeDecoder("ethereum", setOf(Chain.Ethereum))

        decoder.register(throwingDecoder)
        decoder.register(ethereumDecoder)

        val ethereumContent = StubContent(contentChain = Chain.Ethereum)
        val result = decoder.decode(ethereumContent)

        // Bitcoin decoder shouldn't match Ethereum, Ethereum decoder should succeed
        assertEquals(DecodedOperation.Transfer, result.operation)
        assertEquals(
            "ethereum",
            result.counterparty?.let { (it as DecodedCounterparty.Node).value },
        )
    }

    @Test
    fun testCancellationExceptionIsRethrown() {
        val cancellingDecoder = CancellingDecoder(setOf(Chain.Bitcoin))
        val succeedingDecoder = FakeDecoder("succeeding", setOf(Chain.Bitcoin))

        decoder.register(cancellingDecoder)
        decoder.register(succeedingDecoder)

        val content = StubContent(contentChain = Chain.Bitcoin)

        // CancellationException should be rethrown, not swallowed
        assertFailsWith<CancellationException> { decoder.decode(content) }
    }

    // Stub implementation for testing
    private data class StubContent(
        private val stubHasOpaqueSignedContent: Boolean = false,
        val swapPayload: SwapPayload? = null,
        private val contentChain: Chain = Chain.Ton,
    ) : SignedTransactionContent {
        override val chain: Chain = contentChain
        override val isNativeCoin = true
        override val signerAddress = "signer"
        override val rawToAddress = "destination"
        override val rawAmount = SignedAmount.Committed(42.toBigInteger())
        override val signedData: OpaqueSignedContent? = null
        override val rawMemo: String? = "memo"
        override val rawTransactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED
        override val rawWasmPayload = null
        override val rawSwap: SwapPayload? = swapPayload
        override val rawApprove = null
        override val stakingIntent = null
        override val cosmosStakingIntent = null
        override val hasOpaqueSignedContent: Boolean = stubHasOpaqueSignedContent
    }

    /** Fake decoder for testing registry behavior with distinct results. */
    private data class FakeDecoder(
        private val id: String,
        override val handles: Set<Chain>?,
        private val shouldSucceed: Boolean = true,
        private val operation: DecodedOperation = DecodedOperation.Transfer,
    ) : TransactionContentDecoder {

        override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
            return if (shouldSucceed) {
                DecodedTransaction(
                    operation = operation,
                    amount =
                        DecodedAmount.Units(
                            value = BigInteger.ONE,
                            asset = DecodedAsset.TransactionCoin,
                        ),
                    counterparty = DecodedCounterparty.Node(id),
                    evidence = DecodedEvidence.Memo,
                )
            } else {
                null
            }
        }
    }

    /** Decoder that throws an exception, used to test error handling. */
    private data class ThrowingDecoder(override val handles: Set<Chain>?) :
        TransactionContentDecoder {

        override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
            throw IllegalArgumentException("Malformed content")
        }
    }

    /** Decoder that throws CancellationException to test cancellation propagation. */
    private data class CancellingDecoder(override val handles: Set<Chain>?) :
        TransactionContentDecoder {

        override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
            throw CancellationException("Decoding cancelled")
        }
    }
}
