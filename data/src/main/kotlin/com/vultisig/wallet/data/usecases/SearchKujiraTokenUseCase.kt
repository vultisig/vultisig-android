package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import javax.inject.Inject

internal interface SearchKujiraTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchKujiraTokenUseCaseImpl @Inject constructor(
    private val httpClient: HttpClient,
) : SearchKujiraTokenUseCase {

    private val findKujiraUrl = "https://kujira-rest.publicnode.com/cosmwasm/wasm/v1/contract/"

    override suspend operator fun invoke(contractAddress: String): CoinAndPrice? {
        val searchedToken: ContractData? = try {
            searchToken(contractAddress)
        } catch (e: Exception) {
            null
        }
        searchedToken ?: return null
        return CoinAndPrice(searchedToken.toCoin(), BigDecimal.ZERO)
    }

    private suspend fun searchToken(contractAddress: String): ContractData {
       var normalizedAddress= if (contractAddress.contains("factory/")) {
            contractAddress.split("/")[1]
        } else {
            contractAddress
        }
        val body = httpClient.get("$findKujiraUrl/$normalizedAddress")
        return body.body()
    }


    private fun ContractData.toCoin() = Coin(
        chain = Chain.Kujira,
        ticker = contractInfo.label,
        logo = "",
        address = "",
        hexPublicKey = "",
        decimal = 6,
        priceProviderID = "",
        contractAddress = address,
        isNativeToken = false
    )
}

@Serializable
private data class ContractInfo(
    @SerialName("label")
    val label: String,
)

@Serializable
private data class ContractData(
    @SerialName("contract_info")
    val contractInfo: ContractInfo,
    @SerialName("address")
    val address: String,
)