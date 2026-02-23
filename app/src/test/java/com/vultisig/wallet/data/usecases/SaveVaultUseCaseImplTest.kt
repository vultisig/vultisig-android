@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefaultChainsRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class SaveVaultUseCaseImplTest {

    private lateinit var vaultRepository: VaultRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var defaultChainsRepository: DefaultChainsRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository

    private lateinit var useCase: SaveVaultUseCaseImpl

    private val vaultId = "test-vault-id"

    private fun nativeCoin(chain: Chain) = Coin(
        chain = chain,
        ticker = chain.raw,
        logo = "",
        address = "",
        decimal = 8,
        hexPublicKey = "",
        priceProviderID = "",
        contractAddress = "",
        isNativeToken = true,
    )

    @BeforeEach
    fun setUp() {
        vaultRepository = mockk(relaxed = true)
        tokenRepository = mockk()
        defaultChainsRepository = mockk()
        chainAccountAddressRepository = mockk()

        // Default: nativeTokens returns coins for all chains we test with
        val nativeTokens = listOf(
            Chain.ThorChain, Chain.Bitcoin, Chain.Ethereum, Chain.Solana
        ).map { nativeCoin(it) }
        every { tokenRepository.nativeTokens } returns flowOf(nativeTokens)

        // Default: getAddress returns a dummy pair
        coEvery {
            chainAccountAddressRepository.getAddress(any<Coin>(), any())
        } returns Pair("address", "derivedPubKey")

        useCase = SaveVaultUseCaseImpl(
            vaultRepository = vaultRepository,
            tokenRepository = tokenRepository,
            defaultChainsRepository = defaultChainsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
        )
    }

    private fun keyImportVault(
        chainPublicKeys: List<ChainPublicKey> = emptyList(),
    ) = Vault(
        id = vaultId,
        name = "Test Vault",
        libType = SigningLibType.KeyImport,
        chainPublicKeys = chainPublicKeys,
    )

    private fun dklsVault() = Vault(
        id = vaultId,
        name = "Test Vault",
        libType = SigningLibType.DKLS,
    )

    @Test
    fun `KeyImport vault adds only user-selected chains`() = runTest {
        val vault = keyImportVault(
            chainPublicKeys = listOf(
                ChainPublicKey(chain = "Ethereum", publicKey = "pk1", isEddsa = false),
                ChainPublicKey(chain = "Bitcoin", publicKey = "pk2", isEddsa = false),
            )
        )
        coEvery { vaultRepository.getByEcdsa(any()) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault

        useCase(vault, false)

        // Should add Ethereum and Bitcoin, but NOT THORChain
        coVerify(exactly = 2) { vaultRepository.addTokenToVault(vaultId, any()) }
        coVerify(exactly = 1) {
            chainAccountAddressRepository.getAddress(
                match<Coin> { it.chain == Chain.Ethereum }, any()
            )
        }
        coVerify(exactly = 1) {
            chainAccountAddressRepository.getAddress(
                match<Coin> { it.chain == Chain.Bitcoin }, any()
            )
        }
        coVerify(exactly = 0) {
            chainAccountAddressRepository.getAddress(
                match<Coin> { it.chain == Chain.ThorChain }, any()
            )
        }
    }

    @Test
    fun `KeyImport vault includes THORChain when explicitly selected`() = runTest {
        val vault = keyImportVault(
            chainPublicKeys = listOf(
                ChainPublicKey(chain = "THORChain", publicKey = "pk1", isEddsa = false),
                ChainPublicKey(chain = "Ethereum", publicKey = "pk2", isEddsa = false),
            )
        )
        coEvery { vaultRepository.getByEcdsa(any()) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault

        useCase(vault, false)

        coVerify(exactly = 2) { vaultRepository.addTokenToVault(vaultId, any()) }
        coVerify(exactly = 1) {
            chainAccountAddressRepository.getAddress(
                match<Coin> { it.chain == Chain.ThorChain }, any()
            )
        }
    }

    @Test
    fun `KeyImport vault with empty chainPublicKeys adds no chains`() = runTest {
        val vault = keyImportVault(chainPublicKeys = emptyList())
        coEvery { vaultRepository.getByEcdsa(any()) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault

        useCase(vault, false)

        coVerify(exactly = 0) { vaultRepository.addTokenToVault(any(), any()) }
    }

    @Test
    fun `DKLS vault uses defaultChainsRepository for chain selection`() = runTest {
        val vault = dklsVault()
        val defaultChains = listOf(Chain.ThorChain, Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        coEvery { vaultRepository.getByEcdsa(any()) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault
        every { defaultChainsRepository.selectedDefaultChains } returns flowOf(defaultChains)

        useCase(vault, false)

        coVerify(exactly = defaultChains.size) { vaultRepository.addTokenToVault(vaultId, any()) }
    }

    @Test
    fun `duplicate vault throws DuplicateVaultException`() = runTest {
        val vault = keyImportVault()
        coEvery { vaultRepository.getByEcdsa(any()) } returns vault

        assertFailsWith<DuplicateVaultException> {
            useCase(vault, false)
        }
    }
}
