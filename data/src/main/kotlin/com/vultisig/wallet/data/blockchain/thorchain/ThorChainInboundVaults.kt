package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.swap.limit.thorchainMemoAssetChainPrefix
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a signed destination is somewhere THORChain actually receives. */
interface InboundVaultCorroborating {

    /** Checks a signed destination and its chain against THORChain's inbound set. */
    fun corroborates(destination: String, chain: Chain, isNative: Boolean): Boolean
}

/**
 * The inbound vaults already known, without asking the network.
 *
 * ⚠️ **Exists so a signing screen can corroborate a destination without a fetch.** An LP deposit
 * leaves Bitcoin, so nothing about the chain it is on says THORChain — the only corroboration is
 * that its destination *is* a THORChain inbound vault, and that answer comes from THORChain rather
 * than from whoever composed the payload. A decoder reads synchronously and must never reach the
 * network, so the answer has to already be here or not be given.
 *
 * Answers null on a cold or stale snapshot rather than blocking, and a null refuses the reading:
 * exactly what happened before any of this existed, which makes a cold snapshot a missed
 * improvement rather than a wrong answer.
 *
 * Deliberately separate from the responses [com.vultisig.wallet.data.api.ThorChainApi] hands its
 * own callers. Those drive a fail-closed halt gate and are fetched with `no-store` precisely so a
 * halt can never be read from a cache; this snapshot is written from the same responses but is only
 * ever asked whether an address was a vault, which a halt does not change.
 */
@Singleton
class ThorChainInboundVaultSnapshot @Inject constructor() {

    private data class Held(val addresses: List<THORChainInboundAddress>, val recordedAt: Long)

    private val held = AtomicReference<Held?>(null)

    /** Records the addresses a live fetch returned. An empty answer is not worth holding. */
    fun record(addresses: List<THORChainInboundAddress>) {
        if (addresses.isEmpty()) return
        held.set(Held(addresses, System.currentTimeMillis()))
    }

    /** The recorded addresses when they are younger than [maxAgeMillis], else null. */
    fun current(maxAgeMillis: Long = MAX_AGE_MILLIS): List<THORChainInboundAddress>? {
        val snapshot = held.get() ?: return null
        val age = System.currentTimeMillis() - snapshot.recordedAt
        return snapshot.addresses.takeIf { age in 0..maxAgeMillis }
    }

    private companion object {
        /** Matches the iOS `cachedInboundAddresses` window. */
        const val MAX_AGE_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * Corroborates a signed destination against the inbound vaults THORChain last published.
 *
 * Mirrors the iOS `ThorchainInboundVaults`.
 */
@Singleton
class ThorChainInboundVaults
@Inject
constructor(private val snapshot: ThorChainInboundVaultSnapshot) : InboundVaultCorroborating {

    override fun corroborates(destination: String, chain: Chain, isNative: Boolean): Boolean {
        if (destination.isEmpty()) return false
        val known = snapshot.current() ?: return false

        // Only this transaction's chain can corroborate its route, and only a chain THORChain
        // routes at all has an inbound to be corroborated against. The prefix table is the same one
        // the memo builders encode routes with, so the two directions cannot disagree.
        val prefix = thorchainMemoAssetChainPrefix[chain] ?: return false
        val candidates = known.filter { it.chain.trim().equals(prefix, ignoreCase = true) }
        if (candidates.isEmpty()) return false

        return candidates.any { entry ->
            // Native deposits go to the vault; tokens go to the router.
            val expected = if (isNative) entry.address else entry.router.orEmpty()
            expected.isNotEmpty() && sameAddress(expected, destination, chain)
        }
    }

    /** EVM addresses are case-insensitive; other address families are not. */
    private fun sameAddress(expected: String, destination: String, chain: Chain): Boolean =
        if (chain.standard == TokenStandard.EVM) expected.equals(destination, ignoreCase = true)
        else expected == destination
}

/**
 * Binds the corroborator the THORChain reader asks. The interface is the seam: a decoder that
 * decides provenance from network-sourced state must be drivable without the network.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ThorChainInboundVaultsModule {

    @Binds @Singleton fun bindInboundVaults(impl: ThorChainInboundVaults): InboundVaultCorroborating
}
