package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.crypto.ton.TonBocSerializer
import com.vultisig.wallet.data.crypto.ton.TonCellBuilder
import com.vultisig.wallet.data.crypto.ton.tonFriendlyToRaw
import java.math.BigInteger

/**
 * Mechanics for Tonstakers liquid staking (tonapi implementation `liquidTF`), verified against the
 * `ton-blockchain/liquid-staking-contract` source and the official `tonstakers-sdk`.
 *
 * Unlike a nominator pool, nothing here is a text comment: a stake is a TON transfer to the pool
 * carrying the `pool::deposit` body cell and mints the liquid **tsTON** jetton in return; an
 * unstake burns tsTON at the user's own jetton wallet, which the pool answers with TON at once when
 * it has liquidity and otherwise at the end of the validation round through a withdrawal NFT.
 *
 * Two contract facts the mechanism depends on and that a plain reading of the op codes misses:
 * - The pool takes `DEPOSIT_FEE` (1 TON) off every deposit's value before it stakes the rest and
 *   refunds what the mint chain does not spend, so the value sent must exceed the stake by that
 *   fee.
 * - The pool reads two flag bits (`wait_till_round_end`, `fill_or_kill`) out of the burn's
 *   `custom_payload`. Omitting the payload makes that read underflow, the pool catches it and mints
 *   the tsTON straight back: a burn without the flags cell is a silent no-op that only costs gas.
 */
object Tonstakers {

    /** Pool contract (tonapi interface `tonstake_pool`), bounceable user-friendly form. */
    const val POOL_ADDRESS = "EQCkWxfyhAkim3g2DjKQQg8T5P4g-Q1-K_jErGcDJZ4i-vqR"

    /** tsTON jetton master; equals `Coins.Ton.TSTON.contractAddress`. */
    const val TSTON_MASTER_ADDRESS = "EQC98_qAmNEptUtPc7W6xdHh_ZHrBUFpw5Ft_IzNU20QAJav"

    private val POOL_ADDRESS_RAW: String = checkNotNull(tonFriendlyToRaw(POOL_ADDRESS))

    /** `pool::deposit` — `deposit#47d54391 query_id:uint64 = InternalMsgBody`. */
    const val OP_POOL_DEPOSIT = 0x47d54391L

    /**
     * TEP-74 `burn#595f07bc query_id:uint64 amount:(VarUInteger 16) response_destination:MsgAddress
     * custom_payload:(Maybe ^Cell) = InternalMsgBody`, sent to the user's own tsTON jetton wallet.
     */
    const val OP_JETTON_BURN = 0x595f07bcL

    /**
     * `DEPOSIT_FEE` in `pool.func`: the pool reserves this much of a deposit's value for its mint
     * chain and stakes `msg_value − DEPOSIT_FEE`. The unspent part comes back as excesses, but the
     * transfer has to carry it. 1 TON.
     */
    val DEPOSIT_FEE: BigInteger = BigInteger.valueOf(1_000_000_000L)

    /**
     * Value attached to a burn so it still clears the pool's `WITHDRAWAL_FEE` (0.5 TON) after the
     * jetton-wallet and minter hops each take their gas. The `tonstakers-sdk` `UNSTAKE_FEE_RES`,
     * 1.05 TON; the unspent part is refunded.
     */
    val UNSTAKE_ATTACHED_VALUE: BigInteger = BigInteger.valueOf(1_050_000_000L)

    /** Minimum deposit the pool advertises (`min_stake`, tonapi), before [DEPOSIT_FEE]. 1 TON. */
    val MIN_STAKE: BigInteger = BigInteger.valueOf(1_000_000_000L)

    /**
     * Value a stake transfer must carry for the pool to stake [stakeAmount]: the stake plus the fee
     * the pool takes off the top.
     */
    fun depositValue(stakeAmount: BigInteger): BigInteger = stakeAmount + DEPOSIT_FEE

    /**
     * Smallest value a stake transfer may carry: the pool minimum ([minStake], defaulting to
     * [MIN_STAKE]) plus [DEPOSIT_FEE].
     */
    fun minimumDepositValue(minStake: BigInteger? = null): BigInteger =
        depositValue(minStake?.takeIf { it.signum() > 0 } ?: MIN_STAKE)

    /**
     * The `pool::deposit` body with `query_id = 0`, as a base64 BOC for a transfer's custom
     * payload. The pool only reads the header (op + query id), so no partner code is appended.
     */
    fun depositBody(): String =
        TonBocSerializer.toBase64(
            TonCellBuilder().storeUInt(OP_POOL_DEPOSIT, 32).storeUInt(0, 64).build()
        )

    /**
     * The tsTON burn body withdrawing [amount] base units, as a base64 BOC for a transfer to the
     * user's tsTON jetton wallet. [responseAddress] receives the excesses and is the user's own
     * wallet, in user-friendly or raw form.
     *
     * The custom payload carries the pool's two withdrawal flags. Both are left clear, which asks
     * for an immediate payout when the pool has liquidity and a round-end payout otherwise — the
     * `tonstakers-sdk` default. Returns `null` when [responseAddress] is not a TON address.
     */
    fun burnBody(
        amount: BigInteger,
        responseAddress: String,
        waitTillRoundEnd: Boolean = false,
        fillOrKill: Boolean = false,
    ): String? {
        val response = tonFriendlyToRaw(responseAddress) ?: return null
        val flags = TonCellBuilder().storeBit(waitTillRoundEnd).storeBit(fillOrKill).build()
        val body =
            TonCellBuilder()
                .storeUInt(OP_JETTON_BURN, 32)
                .storeUInt(0, 64)
                .storeCoins(amount)
                .storeAddress(response)
                .storeMaybeRef(flags)
                .build()
        return TonBocSerializer.toBase64(body)
    }

    /**
     * Whether [address] is the Tonstakers pool, whichever spelling it arrives in. Pure JVM so the
     * signed-transaction decoder can gate a `pool::deposit` on its destination without WalletCore.
     */
    fun isPool(address: String): Boolean = tonFriendlyToRaw(address) == POOL_ADDRESS_RAW
}
