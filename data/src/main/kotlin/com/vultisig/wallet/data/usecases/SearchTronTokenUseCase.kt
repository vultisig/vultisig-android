package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

internal interface SearchTronTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchTronTokenUseCaseImpl
@Inject
constructor(private val tronApi: TronApi, private val tokenPriceRepository: TokenPriceRepository) :
    SearchTronTokenUseCase {

    override suspend operator fun invoke(contractAddress: String): CoinAndPrice? {
        curatedCoin(contractAddress)?.let { coin ->
            return CoinAndPrice(
                coin,
                tokenPriceRepository.getPriceByPriceProviderId(coin.priceProviderID),
            )
        }

        val symbol =
            tronApi.readContractConstant(contractAddress, "symbol()")?.let(::decodeAbiString)
                ?: return null
        val decimals =
            tronApi.readContractConstant(contractAddress, "decimals()")?.let(::decodeUint8)
                ?: return null

        val coin =
            Coin(
                chain = Chain.Tron,
                ticker = symbol,
                logo = "",
                address = "",
                decimal = decimals,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = contractAddress,
                isNativeToken = false,
            )
        return CoinAndPrice(coin, BigDecimal.ZERO)
    }

    /** Standard ABI dynamic-`string` return: 32B offset, 32B length, then the UTF-8 bytes. */
    private fun decodeAbiString(hex: String): String? {
        val clean = hex.stripHexPrefix()
        if (clean.length < 128) return null
        val length = BigInteger(clean.substring(64, 128), 16).toInt()
        if (length <= 0 || clean.length < 128 + length * 2) return null
        return clean
            .substring(128, 128 + length * 2)
            .hexToByteArrayOrNull()
            ?.toString(Charsets.UTF_8)
            ?.trim { it == '\u0000' }
            ?.takeIf { it.isNotBlank() }
    }

    // Null for any word wider than the uint8 decimals() declares: toInt() would keep its low 32
    // bits, so a full-width word read as -1 and 2^32 + 6 as a plausible 6.
    private fun decodeUint8(hex: String): Int? =
        hex.stripHexPrefix()
            .takeIf { it.length >= 64 }
            ?.toBigIntegerOrNull(16)
            ?.takeIf { it.bitLength() <= UByte.SIZE_BITS }
            ?.toInt()

    private fun curatedCoin(contractAddress: String): Coin? =
        Coins.coins[Chain.Tron]?.firstOrNull {
            !it.isNativeToken && it.contractAddress.equals(contractAddress, ignoreCase = true)
        }
}
