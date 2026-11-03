package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import java.math.BigInteger
import javax.inject.Inject

/**
 * Whether THORChain will accept a RUNE-side add naming a given asset address, or — with no asset
 * address — whether a live position takes a single-sided RUNE add.
 */
sealed interface ThorChainLpPairing {
    /** A paired add is credited (or held pending) against this record. */
    data object Pairable : ThorChainLpPairing

    /**
     * The vault holds a live RUNE-only position: the record has no asset address, and thornode
     * refunds a live record's add naming any address other than the recorded one. Only a
     * single-sided RUNE add (`+:POOL`) is accepted.
     */
    data object SingleSidedPosition : ThorChainLpPairing

    /**
     * The vault holds a live position paired with another asset address. thornode refunds an add
     * naming this asset address, and although it credits a single-sided RUNE add (`+:POOL`), a
     * symmetric withdrawal then pays half of that RUNE's value to the recorded address — which
     * anyone can set by naming the vault's RUNE address on an asset add that later commits alone.
     * Neither add is safe to send.
     */
    data object PairedWithOtherAddress : ThorChainLpPairing

    /** A pending half fixed an asset address other than the one this add would name. */
    data object AddressMismatch : ThorChainLpPairing
}

/**
 * Checks the vault's record on a THORChain pool against the asset address a RUNE-side add would
 * name, mirroring thornode's `addLiquidity` address rules.
 *
 * thornode compares the asset address only when the add names one, and then case-insensitively. A
 * zero-unit record with nothing pending takes whatever the add names; a pending half and a live
 * position both refund a named address other than the recorded one — an empty recorded address
 * included.
 */
interface CheckThorChainLpPairingUseCase {
    /**
     * @param assetAddress the asset address the add would name, or `null` when the vault has none.
     *   A live record then reads as [ThorChainLpPairing.SingleSidedPosition], which takes a
     *   `+:POOL` add, or [ThorChainLpPairing.PairedWithOtherAddress].
     * @throws Exception when the record cannot be read; a caller about to move funds must not
     *   guess.
     */
    suspend operator fun invoke(
        pool: String,
        runeAddress: String,
        assetAddress: String?,
    ): ThorChainLpPairing
}

internal class CheckThorChainLpPairingUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : CheckThorChainLpPairingUseCase {

    override suspend fun invoke(
        pool: String,
        runeAddress: String,
        assetAddress: String?,
    ): ThorChainLpPairing {
        val record =
            thorChainApi.getLiquidityProvider(pool, runeAddress)
                ?: return ThorChainLpPairing.Pairable
        return record.pairingFor(assetAddress)
    }
}

internal fun ThorChainLiquidityProviderJson.pairingFor(assetAddress: String?): ThorChainLpPairing {
    val hasUnits = (units.toBigIntegerOrNull() ?: BigInteger.ZERO).signum() > 0
    val recordedAsset = this.assetAddress.orEmpty()

    if (!hasUnits) {
        if (!hasPendingHalf() || assetAddress == null) return ThorChainLpPairing.Pairable
        return if (recordedAsset.equals(assetAddress, ignoreCase = true)) {
            ThorChainLpPairing.Pairable
        } else {
            ThorChainLpPairing.AddressMismatch
        }
    }

    return when {
        recordedAsset.isEmpty() -> ThorChainLpPairing.SingleSidedPosition
        recordedAsset.equals(assetAddress, ignoreCase = true) -> ThorChainLpPairing.Pairable
        else -> ThorChainLpPairing.PairedWithOtherAddress
    }
}

private fun ThorChainLiquidityProviderJson.hasPendingHalf(): Boolean =
    !pendingTxId.isNullOrBlank() ||
        (pendingRune.toBigIntegerOrNull()?.signum() ?: 0) > 0 ||
        (pendingAsset.toBigIntegerOrNull()?.signum() ?: 0) > 0
