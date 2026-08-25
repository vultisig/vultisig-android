package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SwapPayload
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun testRegistryStartsEmpty() {
        assertTrue(decoder.getDecoders().isEmpty())
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

    // Stub implementation for testing
    private data class StubContent(
        private val stubHasOpaqueSignedContent: Boolean = false,
        val swapPayload: SwapPayload? = null,
    ) : SignedTransactionContent {
        override val chain: Chain = Chain.Ton
        override val isNativeCoin = true
        override val rawToAddress = "destination"
        override val rawAmount = SignedAmount.Committed(42.toBigInteger())
        override val signedData: ByteArray? = null
        override val rawMemo: String? = "memo"
        override val rawTransactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED
        override val rawWasmPayload = null
        override val rawSwap: SwapPayload? = swapPayload
        override val rawApprove = null
        override val stakingIntent = null
        override val cosmosStakingIntent = null
        override val hasOpaqueSignedContent: Boolean = stubHasOpaqueSignedContent
    }
}
