package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.MayaLiquidityProviderJson
import java.math.BigInteger
import javax.inject.Inject

/** Whether MayaChain will accept a paired add naming a given CACAO and asset address. */
sealed interface MayaLpPairing {
    /** A paired add is credited (or held pending) against this record. */
    data object Pairable : MayaLpPairing

    /**
     * The vault holds a live CACAO-only position: mayanode records no asset address on it and only
     * fills one in on a zero-unit record, so any add naming an asset address is refunded. Only a
     * single-sided CACAO add (`+:POOL`) is accepted.
     */
    data object SingleSidedPosition : MayaLpPairing

    /** The record is keyed to addresses other than the ones this add would name. */
    data object AddressMismatch : MayaLpPairing
}

/**
 * Checks the vault's record on a MayaChain pool against the addresses a paired add would name,
 * mirroring mayanode's `addLiquidity` address rules.
 *
 * A zero-unit record with nothing pending takes whatever addresses the add names. A pending half
 * has already fixed both addresses, and a live position keeps the asset address it was created
 * with — none if it was created CACAO-only — and refunds an add naming any other.
 */
interface CheckMayaLpPairingUseCase {
    /**
     * @throws Exception when the record cannot be read; a caller about to move funds must not
     *   guess.
     */
    suspend operator fun invoke(
        pool: String,
        cacaoAddress: String,
        assetAddress: String,
    ): MayaLpPairing
}

internal class CheckMayaLpPairingUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : CheckMayaLpPairingUseCase {

    override suspend fun invoke(
        pool: String,
        cacaoAddress: String,
        assetAddress: String,
    ): MayaLpPairing {
        val record =
            mayaChainApi.getLiquidityProvider(pool, cacaoAddress) ?: return MayaLpPairing.Pairable
        return record.pairingFor(cacaoAddress, assetAddress)
    }
}

internal fun MayaLiquidityProviderJson.pairingFor(
    cacaoAddress: String,
    assetAddress: String,
): MayaLpPairing {
    val hasUnits = (units.toBigIntegerOrNull() ?: BigInteger.ZERO).signum() > 0
    val recordedCacao = this.cacaoAddress.orEmpty()
    val recordedAsset = this.assetAddress.orEmpty()

    if (!hasUnits) {
        // A fresh record takes the addresses the add names.
        if (pendingTxId.isNullOrBlank()) return MayaLpPairing.Pairable
        // A pending half fixed both addresses when it arrived.
        return if (
            recordedCacao.equals(cacaoAddress, ignoreCase = true) &&
                recordedAsset.equals(assetAddress, ignoreCase = true)
        ) {
            MayaLpPairing.Pairable
        } else {
            MayaLpPairing.AddressMismatch
        }
    }

    return when {
        recordedAsset.isEmpty() -> MayaLpPairing.SingleSidedPosition
        recordedAsset.equals(assetAddress, ignoreCase = true) -> MayaLpPairing.Pairable
        else -> MayaLpPairing.AddressMismatch
    }
}
