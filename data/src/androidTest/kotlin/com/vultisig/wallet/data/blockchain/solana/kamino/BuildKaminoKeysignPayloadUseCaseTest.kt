package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import wallet.core.jni.SolanaTransaction

/**
 * The payload has to record the compute budget the bytes actually carry.
 *
 * iOS refuses to co-sign otherwise. `KaminoVerifyPresentation.priorityFeeAgrees` compares the
 * relayed `BlockChainSpecific.Solana(priorityFee, priorityLimit)` against the ComputeBudget pair it
 * decodes out of the transaction and requires **equality**, showing "the network fee inside this
 * transaction is not the one shown above. Do not sign it." on any difference.
 *
 * The generic Solana values these payloads are built from describe a plain transfer — a
 * 100,000-unit limit and the raw network sample — so relaying them unchanged both blocked every
 * iPhone co-signer and priced the verify screen off a limit no Kamino transaction uses.
 */
class BuildKaminoKeysignPayloadUseCaseTest {

    private val vault = KaminoVaultRegistry.STEAKHOUSE_USDC

    /** A sample above the ceiling, which is what `getMedianPriorityFee` routinely returns. */
    private val networkSample = BigInteger.valueOf(31_342_800)

    /** The app-wide Solana compute limit, which is not the limit a Kamino transaction reserves. */
    private val genericLimit = BigInteger.valueOf(100_000)

    private val coin =
        Coin(
            chain = Chain.Solana,
            ticker = "USDC",
            logo = "usdc",
            address = KaminoFixtures.WALLET,
            decimal = vault.tokenDecimals,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = KaminoVaultRegistry.USDC_MINT,
            isNativeToken = false,
        )

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    private fun build(action: KaminoAction = KaminoAction.DEPOSIT) = runBlocking {
        val api =
            KaminoFixtureApi(
                if (action == KaminoAction.WITHDRAW) KaminoFixtures.WITHDRAW
                else KaminoFixtures.DEPOSIT
            )
        BuildKaminoKeysignPayloadUseCase(KaminoTransactionPreparer(api))
            .invoke(
                vault = vault,
                action = action,
                apiAmount = "1",
                tokenAmount = BigDecimal.ONE,
                coin = coin,
                blockChainSpecific =
                    BlockChainSpecific.Solana(
                        recentBlockHash = "",
                        priorityFee = networkSample,
                        priorityLimit = genericLimit,
                    ),
                vaultPublicKeyECDSA = "",
                vaultLocalPartyID = "",
                libType = SigningLibType.DKLS,
            )
    }

    @Test
    fun the_recorded_compute_budget_is_the_one_inside_the_transaction() {
        val payload = build()
        val raw = payload.signSolana!!.rawTransactions.single()
        val specific = payload.blockChainSpecific as BlockChainSpecific.Solana

        // Exactly the comparison iOS makes before it will join.
        assertEquals(
            "recorded limit must equal the SetComputeUnitLimit in the bytes",
            SolanaTransaction.getComputeUnitLimit(raw),
            specific.priorityLimit.toString(),
        )
        assertEquals(
            "recorded price must equal the SetComputeUnitPrice in the bytes",
            SolanaTransaction.getComputeUnitPrice(raw),
            specific.priorityFee.toString(),
        )
    }

    @Test
    fun the_generic_solana_values_are_replaced_rather_than_relayed() {
        val specific = build().blockChainSpecific as BlockChainSpecific.Solana

        // Both differ from what came in, which is the whole bug: relaying either unchanged is what
        // an iPhone reads as a fee that is not the one shown.
        assertEquals(
            KaminoComputeBudget.unitLimitFor(vault, KaminoAction.DEPOSIT),
            specific.priorityLimit,
        )
        assertNotEquals(genericLimit, specific.priorityLimit)

        assertEquals(KaminoComputeBudget.MAX_UNIT_PRICE, specific.priorityFee)
        assertNotEquals(networkSample, specific.priorityFee)
    }

    @Test
    fun the_recorded_fee_is_what_the_user_will_actually_be_charged() {
        val specific = build().blockChainSpecific as BlockChainSpecific.Solana

        // price x limit / 1e6. Against the generic pair the verify screen showed 3,134,280
        // lamports of priority fee for a transaction that reserves 320,000 — off by an order of
        // magnitude, in the direction that overstates the cost.
        val lamports =
            specific.priorityFee
                .multiply(specific.priorityLimit)
                .divide(BigInteger.valueOf(1_000_000))
        assertEquals(BigInteger.valueOf(320_000), lamports)
        assertEquals(
            KaminoComputeBudget.priorityFeeLamports(vault, KaminoAction.DEPOSIT, networkSample),
            lamports,
        )
    }
}
