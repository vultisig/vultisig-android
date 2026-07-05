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
import org.junit.jupiter.api.Test

internal class SearchTokenUseCaseImplTest {

    private val addressRepository: ChainAccountAddressRepository = mockk()
    private val searchEvmToken: SearchEvmTokenUseCase = mockk()
    private val searchSolToken: SearchSolTokenUseCase = mockk()
    private val searchKujiToken: SearchKujiraTokenUseCase = mockk()
    private val searchTerraToken: SearchTerraTokenUseCase = mockk()
    private val appCurrencyRepository: AppCurrencyRepository = mockk {
        every { currency } returns flowOf(AppCurrency.USD)
    }

    private val useCase =
        SearchTokenUseCaseImpl(
            appCurrencyRepository = appCurrencyRepository,
            searchEvmToken = searchEvmToken,
            searchSolToken = searchSolToken,
            searchKujiToken = searchKujiToken,
            searchTerraToken = searchTerraToken,
            chainAccountAddressRepository = addressRepository,
        )

    @Test
    fun `blank input returns null and calls nothing`() = runTest {
        assertNull(useCase(Chain.Ethereum.id, "   "))

        verify(exactly = 0) { addressRepository.isValid(any(), any()) }
        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
        coVerify(exactly = 0) { searchSolToken(any()) }
        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `empty input returns null and calls nothing`() = runTest {
        assertNull(useCase(Chain.Ethereum.id, ""))

        verify(exactly = 0) { addressRepository.isValid(any(), any()) }
    }

    @Test
    fun `invalid EVM address returns null and skips EVM searcher`() = runTest {
        stubValid(Chain.Ethereum, "0xdead", valid = false)

        assertNull(useCase(Chain.Ethereum.id, "0xdead"))

        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
    }

    @Test
    fun `solana formatted address rejected when EVM chain selected`() = runTest {
        val solAddress = "So11111111111111111111111111111111111111112"
        stubValid(Chain.Ethereum, solAddress, valid = false)

        assertNull(useCase(Chain.Ethereum.id, solAddress))

        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
    }

    @Test
    fun `valid EVM address is trimmed and delegated to EVM searcher`() = runTest {
        givenEvmResponse(evmCoin(), price = BigDecimal("1.5"))

        val result = useCase(Chain.Ethereum.id, "  $EVM_ADDRESS  ")

        assertEquals(BigDecimal("1.5"), result?.fiatValue?.value)
        assertEquals(AppCurrency.USD.ticker, result?.fiatValue?.currency)
        coVerify(exactly = 1) { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) }
    }

    @Test
    fun `EVM searcher returning null surfaces as null`() = runTest {
        stubValid(Chain.Ethereum, EVM_ADDRESS, valid = true)
        coEvery { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) } returns null

        assertNull(useCase(Chain.Ethereum.id, EVM_ADDRESS))
    }

    @Test
    fun `invalid Solana address returns null and skips SOL searcher`() = runTest {
        stubValid(Chain.Solana, "not-a-real-address", valid = false)

        assertNull(useCase(Chain.Solana.id, "not-a-real-address"))

        coVerify(exactly = 0) { searchSolToken(any()) }
    }

    @Test
    fun `valid Solana address delegated to SOL searcher`() = runTest {
        val address = "So11111111111111111111111111111111111111112"
        stubValid(Chain.Solana, address, valid = true)
        coEvery { searchSolToken(address) } returns
            CoinAndPrice(solCoin(contract = address), BigDecimal("20.0"))

        val result = useCase(Chain.Solana.id, address)

        assertEquals(BigDecimal("20.0"), result?.fiatValue?.value)
        coVerify(exactly = 1) { searchSolToken(address) }
    }

    @Test
    fun `invalid Kujira address returns null and skips Kujira searcher`() = runTest {
        stubValid(Chain.Kujira, "0xnot-a-bech32", valid = false)

        assertNull(useCase(Chain.Kujira.id, "0xnot-a-bech32"))

        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `valid Kujira bech32 address delegated to Kujira searcher`() = runTest {
        val address = "kujira1xyzcontractaddress"
        stubValid(Chain.Kujira, address, valid = true)
        coEvery { searchKujiToken(address) } returns
            CoinAndPrice(kujiraCoin(contract = address), BigDecimal.ZERO)

        val result = useCase(Chain.Kujira.id, address)

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        coVerify(exactly = 1) { searchKujiToken(address) }
    }

    @Test
    fun `Kujira factory denom validates against extracted creator address`() = runTest {
        val creator = "kujira1creator"
        val factoryAddress = "factory/$creator/uusdc"
        stubValid(Chain.Kujira, creator, valid = true)
        coEvery { searchKujiToken(factoryAddress) } returns
            CoinAndPrice(kujiraCoin(contract = factoryAddress), BigDecimal.ZERO)

        val result = useCase(Chain.Kujira.id, factoryAddress)

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        verify(exactly = 1) { addressRepository.isValid(Chain.Kujira, creator) }
        coVerify(exactly = 1) { searchKujiToken(factoryAddress) }
    }

    @Test
    fun `Kujira factory denom with invalid creator returns null`() = runTest {
        stubValid(Chain.Kujira, "0xbadcreator", valid = false)

        assertNull(useCase(Chain.Kujira.id, "factory/0xbadcreator/uusdc"))

        coVerify(exactly = 0) { searchKujiToken(any()) }
    }

    @Test
    fun `valid Terra contract address delegated to Terra searcher without WalletCore`() = runTest {
        coEvery { searchTerraToken(Chain.Terra, TERRA_CONTRACT) } returns
            CoinAndPrice(terraCoin(contract = TERRA_CONTRACT), BigDecimal.ZERO)

        val result = useCase(Chain.Terra.id, "  $TERRA_CONTRACT  ")

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        coVerify(exactly = 1) { searchTerraToken(Chain.Terra, TERRA_CONTRACT) }
        verify(exactly = 0) { addressRepository.isValid(any(), any()) }
    }

    @Test
    fun `Terra Classic wallet-shaped contract address delegated to Terra searcher`() = runTest {
        val address = "terra1xj49zyqrwpv5k928jwfpfy2ha668nwdgkwlrg3"
        coEvery { searchTerraToken(Chain.TerraClassic, address) } returns
            CoinAndPrice(terraCoin(chain = Chain.TerraClassic, contract = address), BigDecimal.ZERO)

        val result = useCase(Chain.TerraClassic.id, address)

        assertEquals(BigDecimal.ZERO, result?.fiatValue?.value)
        coVerify(exactly = 1) { searchTerraToken(Chain.TerraClassic, address) }
    }

    @Test
    fun `Terra ibc denom rejected without hitting the searcher`() = runTest {
        assertNull(
            useCase(
                Chain.Terra.id,
                "ibc/8D8A7F7253615E5F76CB6252A1E1BD921D5EDB7BBAAF8913FB1C77FF125D9995",
            )
        )

        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    @Test
    fun `Terra factory denom rejected without hitting the searcher`() = runTest {
        assertNull(useCase(Chain.Terra.id, "factory/terra1creator/utoken"))

        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    @Test
    fun `Terra address with uppercase characters rejected`() = runTest {
        assertNull(useCase(Chain.Terra.id, "terra1NSUQSK6KH58ULCZATWEV87TTQ2Z6R3PUSULG9R24M"))

        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    @Test
    fun `Terra prefix with too-short payload rejected`() = runTest {
        assertNull(useCase(Chain.Terra.id, "terra1short"))

        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    @Test
    fun `EVM-shaped address rejected on Terra`() = runTest {
        assertNull(useCase(Chain.Terra.id, EVM_ADDRESS))

        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    @Test
    fun `blank ticker in response returns null`() = runTest {
        givenEvmResponse(evmCoin(ticker = "   "))

        assertNull(useCase(Chain.Ethereum.id, EVM_ADDRESS))
    }

    @Test
    fun `empty ticker in response returns null`() = runTest {
        givenEvmResponse(evmCoin(ticker = ""))

        assertNull(useCase(Chain.Ethereum.id, EVM_ADDRESS))
    }

    @Test
    fun `negative decimals in response returns null`() = runTest {
        givenEvmResponse(evmCoin(decimal = -1))

        assertNull(useCase(Chain.Ethereum.id, EVM_ADDRESS))
    }

    @Test
    fun `absurdly large decimals in response returns null`() = runTest {
        givenEvmResponse(evmCoin(decimal = 256))

        assertNull(useCase(Chain.Ethereum.id, EVM_ADDRESS))
    }

    @Test
    fun `zero decimals accepted as valid metadata`() = runTest {
        givenEvmResponse(evmCoin(decimal = 0))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertEquals("USDC", result?.coin?.ticker)
        assertEquals(0, result?.coin?.decimal)
    }

    @Test
    fun `thirty decimals accepted as upper bound`() = runTest {
        givenEvmResponse(evmCoin(decimal = 30))

        val result = useCase(Chain.Ethereum.id, EVM_ADDRESS)

        assertEquals(30, result?.coin?.decimal)
    }

    @Test
    fun `blank Kujira ticker returns null after successful search`() = runTest {
        val address = "kujira1xyzcontractaddress"
        stubValid(Chain.Kujira, address, valid = true)
        coEvery { searchKujiToken(address) } returns
            CoinAndPrice(kujiraCoin(ticker = "", contract = address), BigDecimal.ZERO)

        assertNull(useCase(Chain.Kujira.id, address))
    }

    @Test
    fun `unsupported chain returns null even for valid format`() = runTest {
        stubValid(Chain.Bitcoin, "bc1qanyaddress", valid = true)

        assertNull(useCase(Chain.Bitcoin.id, "bc1qanyaddress"))

        coVerify(exactly = 0) { searchEvmToken(any(), any()) }
        coVerify(exactly = 0) { searchSolToken(any()) }
        coVerify(exactly = 0) { searchKujiToken(any()) }
        coVerify(exactly = 0) { searchTerraToken(any(), any()) }
    }

    private fun stubValid(chain: Chain, address: String, valid: Boolean) {
        every { addressRepository.isValid(chain, address) } returns valid
    }

    private fun givenEvmResponse(coin: Coin, price: BigDecimal = BigDecimal.ONE) {
        stubValid(Chain.Ethereum, EVM_ADDRESS, valid = true)
        coEvery { searchEvmToken(Chain.Ethereum.id, EVM_ADDRESS) } returns CoinAndPrice(coin, price)
    }

    private fun evmCoin(
        ticker: String = "USDC",
        decimal: Int = 6,
        contract: String = EVM_ADDRESS,
    ): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = ticker,
            logo = "",
            address = "0x0",
            decimal = decimal,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = contract,
            isNativeToken = false,
        )

    private fun solCoin(contract: String): Coin =
        Coin(
            chain = Chain.Solana,
            ticker = "WSOL",
            logo = "",
            address = "",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "solana",
            contractAddress = contract,
            isNativeToken = false,
        )

    private fun kujiraCoin(ticker: String = "TOKEN", contract: String): Coin =
        Coin(
            chain = Chain.Kujira,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = false,
        )

    private fun terraCoin(chain: Chain = Chain.Terra, contract: String): Coin =
        Coin(
            chain = chain,
            ticker = "STK",
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = false,
        )

    private companion object {
        const val EVM_ADDRESS = "0x1234567890123456789012345678901234567890"
        const val TERRA_CONTRACT =
            "terra1nsuqsk6kh58ulczatwev87ttq2z6r3pusulg9r24mfj2fvtzd4uq3exn26"
    }
}
