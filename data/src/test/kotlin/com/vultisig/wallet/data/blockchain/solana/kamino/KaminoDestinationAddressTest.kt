package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Which address the verify screen names as the destination.
 *
 * Routing lives in the relayed transaction bytes, so nothing here moves money — but this is what
 * the approving user is told the money is going to, and a withdraw pays their own account. The
 * vault is the *source* of a withdrawal; naming it as the destination states the reverse.
 *
 * The preparer is mocked so these run without WalletCore or a captured response: the point under
 * test is the branch, not the bytes.
 */
class KaminoDestinationAddressTest {

    private val vault = KaminoVaultRegistry.STEAKHOUSE_USDC
    private val signer = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

    private val coin =
        Coin(
            chain = Chain.Solana,
            ticker = "USDC",
            logo = "usdc",
            address = signer,
            decimal = vault.tokenDecimals,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = KaminoVaultRegistry.USDC_MINT,
            isNativeToken = false,
        )

    @Test
    fun `a deposit is destined for the vault`() {
        assertEquals(vault.address, kaminoDestinationAddress(vault, KaminoAction.DEPOSIT, signer))
    }

    @Test
    fun `a withdraw is destined for the signer, because the vault is where it comes from`() {
        assertEquals(signer, kaminoDestinationAddress(vault, KaminoAction.WITHDRAW, signer))
        assertNotEquals(
            vault.address,
            kaminoDestinationAddress(vault, KaminoAction.WITHDRAW, signer),
        )
    }

    @Test
    fun `the payload carries the branched destination, not the vault for both directions`() =
        runTest {
            val preparer = mockk<KaminoTransactionPreparer>()
            coEvery { preparer.prepare(any(), any(), any(), any(), any()) } returns "raw-tx"
            val build = BuildKaminoKeysignPayloadUseCase(preparer)

            suspend fun payloadFor(action: KaminoAction) =
                build(
                    vault = vault,
                    action = action,
                    apiAmount = "1",
                    tokenAmount = BigDecimal.ONE,
                    coin = coin,
                    blockChainSpecific =
                        BlockChainSpecific.Solana(
                            recentBlockHash = "",
                            priorityFee = BigInteger.valueOf(1_000_000),
                            priorityLimit = BigInteger.valueOf(100_000),
                        ),
                    vaultPublicKeyECDSA = "",
                    vaultLocalPartyID = "",
                    libType = SigningLibType.DKLS,
                )

            assertEquals(vault.address, payloadFor(KaminoAction.DEPOSIT).toAddress)
            assertEquals(signer, payloadFor(KaminoAction.WITHDRAW).toAddress)
        }
}
