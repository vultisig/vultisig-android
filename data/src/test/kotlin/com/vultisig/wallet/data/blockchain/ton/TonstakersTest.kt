package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.crypto.ton.TonBocParser
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class TonstakersTest {

    private val ownerFriendly = "EQBfwesEQte6-OnnVoRroXg2Fhs5kKQtfIITGP22CG98-SSR"
    private val ownerRaw = "0:5fc1eb0442d7baf8e9e756846ba17836161b3990a42d7c821318fdb6086f7cf9"

    @Test
    fun `deposit body is the canonical pool deposit constant`() {
        val body = Base64.getDecoder().decode(Tonstakers.depositBody())

        // The constant from the ton-blockchain research (`op 0x47d54391 + query_id 0`), which
        // carries no CRC; ours is the same cell with the trailer `@ton/core` adds by default.
        assertEquals(
            "b5ee9c7241010101000e00001847d543910000000000000000e9c18654",
            body.toHexString(),
        )
        assertTrue(body.toHexString().startsWith("b5ee9c72", ignoreCase = true))

        val decoded = TonMessageBodyDecoder.decode(Tonstakers.depositBody())
        assertEquals(TonMessageBodyIntent.LiquidStakingDeposit(BigInteger.ZERO), decoded)
    }

    @Test
    fun `burn body matches the tonstakers-sdk unstake payload`() {
        val body = Tonstakers.burnBody(BigInteger.valueOf(4_300_000_000L), ownerFriendly)

        assertEquals(
            "te6cckEBAgEAOQABZllfB7wAAAAAAAAAAFAQBMywCAC/g9YIha918dPOrQjXQvBsLDZzIUha+QQmMftsEN758wEAASBjtrI+",
            body,
        )
    }

    @Test
    fun `burn body decodes back to the right op, amount, response and flags`() {
        val body = checkNotNull(Tonstakers.burnBody(BigInteger.valueOf(4_300_000_000L), ownerRaw))

        val decoded = assertIs<TonMessageBodyIntent.JettonBurn>(TonMessageBodyDecoder.decode(body))
        assertEquals(BigInteger.ZERO, decoded.queryId)
        assertEquals(BigInteger.valueOf(4_300_000_000L), decoded.amount)
        assertEquals(ownerRaw, decoded.responseDestination)
        assertEquals(
            TonMessageBodyIntent.LiquidStakingWithdrawal(
                waitTillRoundEnd = false,
                fillOrKill = false,
            ),
            decoded.liquidStakingWithdrawal,
        )

        // The pool reads exactly two bits out of the custom payload, so the cell must hold exactly
        // two — a longer or shorter cell would misread as the wrong flags or underflow.
        val slice = TonBocParser.parse(body).beginParse()
        slice.loadUInt(32)
        slice.loadUInt(64)
        slice.loadCoins()
        slice.loadAddress()
        val flags = checkNotNull(slice.loadMaybeRef()).beginParse()
        assertEquals(2, flags.remainingBits)
        assertEquals(0, flags.remainingRefs)
    }

    @Test
    fun `burn body carries the requested flags`() {
        val body =
            checkNotNull(
                Tonstakers.burnBody(
                    BigInteger.ONE,
                    ownerFriendly,
                    waitTillRoundEnd = true,
                    fillOrKill = true,
                )
            )

        val decoded = assertIs<TonMessageBodyIntent.JettonBurn>(TonMessageBodyDecoder.decode(body))
        assertEquals(
            TonMessageBodyIntent.LiquidStakingWithdrawal(
                waitTillRoundEnd = true,
                fillOrKill = true,
            ),
            decoded.liquidStakingWithdrawal,
        )
    }

    @Test
    fun `burn body refuses a response address that is not a TON address`() {
        assertNull(Tonstakers.burnBody(BigInteger.ONE, "not-an-address"))
    }

    @Test
    fun `deposit value adds the pool fee on top of the stake`() {
        assertEquals(
            BigInteger.valueOf(6_000_000_000L),
            Tonstakers.depositValue(BigInteger.valueOf(5_000_000_000L)),
        )
        assertEquals(BigInteger.valueOf(2_000_000_000L), Tonstakers.minimumDepositValue())
        assertEquals(BigInteger.valueOf(2_000_000_000L), Tonstakers.minimumDepositValue(null))
        assertEquals(
            BigInteger.valueOf(2_000_000_000L),
            Tonstakers.minimumDepositValue(BigInteger.ZERO),
        )
        assertEquals(
            BigInteger.valueOf(3_500_000_000L),
            Tonstakers.minimumDepositValue(BigInteger.valueOf(2_500_000_000L)),
        )
    }

    @Test
    fun `recognises the pool in every spelling`() {
        assertTrue(Tonstakers.isPool(Tonstakers.POOL_ADDRESS))
        assertTrue(Tonstakers.isPool("UQCkWxfyhAkim3g2DjKQQg8T5P4g-Q1-K_jErGcDJZ4i-qdU"))
        assertTrue(
            Tonstakers.isPool("0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa")
        )
        assertTrue(
            Tonstakers.isPool("0:A45B17F28409229B78360E3290420F13E4FE20F90D7E2BF8C4AC6703259E22FA")
        )
        assertFalse(Tonstakers.isPool(ownerFriendly))
        assertFalse(Tonstakers.isPool(""))
    }

    @Test
    fun `tsTON master is the curated tsTON coin`() {
        assertEquals(Coins.Ton.TSTON.contractAddress, Tonstakers.TSTON_MASTER_ADDRESS)
    }
}
