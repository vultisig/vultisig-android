package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SearchTokenUseCaseImplTest {

    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var searchEvmToken: SearchEvmTokenUseCase
    private lateinit var searchSolToken: SearchSolTokenUseCase
    private lateinit var searchKujiToken: SearchKujiraTokenUseCase
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var useCase: SearchTokenUseCaseImpl

    @BeforeEach
    fun setUp() {
        appCurrencyRepository = mockk()
        searchEvmToken = mockk()
        searchSolToken = mockk()
        searchKujiToken = mockk()
        chainAccountAddressRepository = mockk()

        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)

        useCase =
            SearchTokenUseCaseImpl(
                appCurrencyRepository = appCurrencyRepository,
                searchEvmToken = searchEvmToken,
                searchSolToken = searchSolToken,
                searchKujiToken = searchKujiToken,
                chainAccountAddressRepository = chainAccountAddressRepository,
            )
    }

    @Test
    fun `blank address returns null without touching validator or searchers`() = runTest {
        val result = useCase(Chain.Ethereum.id, "   ")

        assertNull(result)
        verify(exactly = 0) { chainAccountAddressRepository.isValid(any(), any()) }
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
        coVerify(exactly = 0) { searchSolToken(any()) }
        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `empty address returns null without touching validator or searchers`() = runTest {
        val result = useCase(Chain.Ethereum.id, "")

        assertNull(result)
        verify(exactly = 0) { chainAccountAddressRepository.isValid(any(), any()) }
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
    }

    @Test
    fun `invalid EVM address returns null and skips EVM searcher`() = runTest {
        val address = "0xdead"
        every { chainAccountAddressRepository.isValid(Chain.Ethereum, address) } returns false

        val result = useCase(Chain.Ethereum.id, address)

        assertNull(result)
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
    }

    @Test
    fun `solana-formatted address rejected when EVM chain selected`() = runTest {
        val solAddress = "So11111111111111111111111111111111111111112"
        every { chainAccountAddressRepository.isValid(Chain.Ethereum, solAddress) } returns false

        val result = useCase(Chain.Ethereum.id, solAddress)

        assertNull(result)
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
    }

    @Test
    fun `valid EVM address delegates to EVM searcher with trimmed address`() = runTest {
        val padded = "  $EVM_ADDRESS  "
        val price = BigDecimal("1.5")
        every { chainAccountAddressRepository.isValid(Chain.Ethereum, EVM_ADDRESS) } returns true
        coEvery { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) } returns
            CoinAndPrice(sampleEvmCoin(EVM_ADDRESS), price)

        val result = useCase(Chain.Ethereum.id, padded)

        assertEquals(price, result?.fiatValue?.value)
        assertEquals(AppCurrency.USD.ticker, result?.fiatValue?.currency)
        coVerify(exactly = 1) { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) }
    }

    @Test
    fun `EVM searcher null result surfaces as null`() = runTest {
        every { chainAccountAddressRepository.isValid(Chain.Ethereum, EVM_ADDRESS) } returns true
        coEvery { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) } returns null

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertNull(result)
    }

    @Test
    fun `invalid Solana address returns null and skips SOL searcher`() = runTest {
        val address = "not-a-real-address"
        every { chainAccountAddressRepository.isValid(Chain.Solana, address) } returns false

        val result = useCase(Chain.Solana.id, address)

        assertNull(result)
        coVerify(exactly = 0) { searchSolToken(any()) }
    }

    @Test
    fun `valid Solana address delegates to SOL searcher`() = runTest {
        val address = "So11111111111111111111111111111111111111112"
        val price = BigDecimal("20.0")
        every { chainAccountAddressRepository.isValid(Chain.Solana, address) } returns true
        coEvery { searchSolToken(address) } returns CoinAndPrice(sampleSolCoin(address), price)

        val result = useCase(Chain.Solana.id, address)

        assertEquals(price, result?.fiatValue?.value)
        coVerify(exactly = 1) { searchSolToken(address) }
    }

    @Test
    fun `invalid Kujira address returns null and skips Kujira searcher`() = runTest {
        val address = "0xnot-a-bech32"
        every { chainAccountAddressRepository.isValid(Chain.Kujira, address) } returns false

        val result = useCase(Chain.Kujira.id, address)

        assertNull(result)
        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `valid Kujira bech32 address delegates to Kujira searcher`() = runTest {
        val address = "kujira1xyzcontractaddress"
        every { chainAccountAddressRepository.isValid(Chain.Kujira, address) } returns true
        coEvery { searchKujiToken(address) } returns
            CoinAndPrice(sampleKujiCoin(address), BigDecimal.ZERO)

        val result = useCase(Chain.Kujira.id, address)

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        coVerify(exactly = 1) { searchKujiToken(address) }
    }

    @Test
    fun `Kujira factory denom validates against extracted creator address`() = runTest {
        val creator = "kujira1creator"
        val factoryAddress = "factory/$creator/uusdc"
        every { chainAccountAddressRepository.isValid(Chain.Kujira, creator) } returns true
        coEvery { searchKujiToken(factoryAddress) } returns
            CoinAndPrice(sampleKujiCoin(factoryAddress), BigDecimal.ZERO)

        val result = useCase(Chain.Kujira.id, factoryAddress)

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        verify(exactly = 1) { chainAccountAddressRepository.isValid(Chain.Kujira, creator) }
        coVerify(exactly = 1) { searchKujiToken(factoryAddress) }
    }

    @Test
    fun `Kujira factory denom with invalid creator returns null`() = runTest {
        val factoryAddress = "factory/0xbadcreator/uusdc"
        every { chainAccountAddressRepository.isValid(Chain.Kujira, "0xbadcreator") } returns false

        val result = useCase(Chain.Kujira.id, factoryAddress)

        assertNull(result)
        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `blank ticker in searcher response returns null`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(ticker = "   "))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertNull(result)
    }

    @Test
    fun `empty ticker in searcher response returns null`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(ticker = ""))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertNull(result)
    }

    @Test
    fun `negative decimals in searcher response returns null`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(decimal = -1))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertNull(result)
    }

    @Test
    fun `absurdly large decimals in searcher response returns null`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(decimal = 256))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertNull(result)
    }

    @Test
    fun `zero decimals accepted as valid metadata`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(decimal = 0))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertEquals("USDC", result?.coin?.ticker)
        assertEquals(0, result?.coin?.decimal)
    }

    @Test
    fun `thirty decimals accepted as upper bound`() = runTest {
        arrangeEvmResponse(sampleEvmCoin(EVM_ADDRESS).copy(decimal = 30))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertEquals(30, result?.coin?.decimal)
    }

    @Test
    fun `blank Kujira ticker returns null after successful search`() = runTest {
        val address = "kujira1xyzcontractaddress"
        every { chainAccountAddressRepository.isValid(Chain.Kujira, address) } returns true
        coEvery { searchKujiToken(address) } returns
            CoinAndPrice(sampleKujiCoin(address).copy(ticker = ""), BigDecimal.ZERO)

        val result = useCase(Chain.Kujira.id, address)

        assertNull(result)
    }

    @Test
    fun `unsupported chain returns null and skips all searchers`() = runTest {
        val address = "bc1qanyaddress"
        every { chainAccountAddressRepository.isValid(Chain.Bitcoin, address) } returns true

        val result = useCase(Chain.Bitcoin.id, address)

        assertNull(result)
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
        coVerify(exactly = 0) { searchSolToken(any()) }
        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    private fun sampleEvmCoin(address: String): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0x0",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = address,
            isNativeToken = false,
        )

    private fun sampleSolCoin(address: String): Coin =
        Coin(
            chain = Chain.Solana,
            ticker = "WSOL",
            logo = "",
            address = "",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "solana",
            contractAddress = address,
            isNativeToken = false,
        )

    private fun sampleKujiCoin(address: String): Coin =
        Coin(
            chain = Chain.Kujira,
            ticker = "TOKEN",
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = address,
            isNativeToken = false,
        )

    private fun arrangeEvmResponse(coin: Coin) {
        every { chainAccountAddressRepository.isValid(Chain.Ethereum, EVM_ADDRESS) } returns true
        coEvery { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) } returns
            CoinAndPrice(coin, BigDecimal.ONE)
    }

    private companion object {
        const val EVM_ADDRESS = "0x1234567890123456789012345678901234567890"
    }
}
