package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class ResolveQbtcClaimCoinsUseCaseTest {

    private val tokenRepository = mockk<TokenRepository>()
    private val chainAccountAddressRepository = mockk<ChainAccountAddressRepository>()

    private val useCase =
        ResolveQbtcClaimCoinsUseCase(tokenRepository, chainAccountAddressRepository)

    private fun coin(chain: Chain, address: String, pubKey: String = "pub") =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = address,
            decimal = 8,
            hexPublicKey = pubKey,
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    /** Already-enabled accounts are returned as-is, without deriving anything. */
    @Test
    fun `returns enabled coins as-is`() = runTest {
        val vault =
            Vault(
                id = "v",
                name = "v",
                coins = listOf(coin(Chain.Bitcoin, "btcAddr"), coin(Chain.Qbtc, "qbtcAddr")),
            )

        val result = useCase(vault)

        result.btc.address shouldBe "btcAddr"
        result.qbtc.address shouldBe "qbtcAddr"
    }

    /** A missing account is derived in-memory from the native template and the vault's keys. */
    @Test
    fun `derives missing accounts`() = runTest {
        val vault = Vault(id = "v", name = "v", coins = emptyList())

        coEvery { tokenRepository.getNativeToken(Chain.Bitcoin.id) } returns
            coin(Chain.Bitcoin, address = "", pubKey = "")
        coEvery { tokenRepository.getNativeToken(Chain.Qbtc.id) } returns
            coin(Chain.Qbtc, address = "", pubKey = "")
        coEvery {
            chainAccountAddressRepository.getAddress(coin(Chain.Bitcoin, "", ""), vault)
        } returns ("derivedBtc" to "btcPub")
        coEvery {
            chainAccountAddressRepository.getAddress(coin(Chain.Qbtc, "", ""), vault)
        } returns ("derivedQbtc" to "qbtcPub")

        val result = useCase(vault)

        result.btc.address shouldBe "derivedBtc"
        result.btc.hexPublicKey shouldBe "btcPub"
        result.qbtc.address shouldBe "derivedQbtc"
        result.qbtc.hexPublicKey shouldBe "qbtcPub"
    }

    /** Derivation failure surfaces as MissingQbtcClaimAccountException. */
    @Test
    fun `throws when derivation fails`() = runTest {
        val vault = Vault(id = "v", name = "v", coins = emptyList())

        coEvery { tokenRepository.getNativeToken(Chain.Bitcoin.id) } returns
            coin(Chain.Bitcoin, address = "", pubKey = "")
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), vault) } throws
            IllegalStateException("no key material")

        shouldThrow<MissingQbtcClaimAccountException> { useCase(vault) }
    }

    /** A repository/template failure propagates as itself, not as a missing-account error. */
    @Test
    fun `propagates template fetch failures`() = runTest {
        val vault = Vault(id = "v", name = "v", coins = emptyList())

        coEvery { tokenRepository.getNativeToken(Chain.Bitcoin.id) } throws
            IllegalStateException("token repo down")

        shouldThrow<IllegalStateException> { useCase(vault) }
    }
}
