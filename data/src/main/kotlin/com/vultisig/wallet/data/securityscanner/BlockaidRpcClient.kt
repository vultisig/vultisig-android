package com.vultisig.wallet.data.securityscanner


import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BlockaidRpcClient @Inject constructor(
    private val httpClient: HttpClient,
) : BlockaidRpcClientContract {

    override suspend fun scanBitcoinTransaction(address: String, serializedTransaction: String) {
        val bitcoinRequest = buildBitcoinScanRequest(address, serializedTransaction)

        httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/bitcoin/transaction-raw/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(bitcoinRequest)
        }
    }

    override suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String
    ) {
        val evmRequest = buildEthereumScanRequest(chain, from, to, data, amount)

        httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/evm/json-rpc/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(evmRequest)
        }
    }

    override suspend fun scanSolanaTransaction(address: String, serializedMessage: String) {
        val solanaRequest = buildSolanaScanRequest(address, serializedMessage)

        httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/solana/address/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(solanaRequest)
        }
    }

    override suspend fun scanSuiTransaction(address: String, serializedTransaction: String) {
        val suiRequest = buildSuiScanRequest(address, serializedTransaction)

        httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/sui/transaction/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(suiRequest)
        }
    }

    private fun buildBitcoinScanRequest(
        address: String,
        serializedTransaction: String
    ): BitcoinScanTransactionRequest {
        return BitcoinScanTransactionRequest(
            chain = Chain.Bitcoin.toName(),
            metadata = CommonMetadata(url = VULTISIG_DOMAIN),
            options = listOf("validation"),
            accountAddress = address,
            transaction = serializedTransaction,
        )
    }

    private fun buildEthereumScanRequest(
        chain: Chain,
        from: String,
        to: String,
        data: String,
        amount: String
    ): EthereumScanTransactionRequest {
        return EthereumScanTransactionRequest(
            chain = chain.toName(),
            metadata = EthereumScanTransactionRequest.Metadata(
                domain = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = from,
            simulatedWithEstimatedGas = false,
            data = EthereumScanTransactionRequest.Data(
                from = from,
                to = to,
                data = data,
                value = amount,
            ),
        )
    }

    private fun buildSolanaScanRequest(
        address: String,
        serializedMessage: String
    ): SolanaScanTransactionRequest {
        return SolanaScanTransactionRequest(
            chain = Chain.Solana.toName(),
            metadata = CommonMetadata(
                url = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = address,
            encoding = "base64", // TODO: Confirm if this is the correct encoding
            transactions = listOf(serializedMessage),
        )
    }

    private fun buildSuiScanRequest(
        address: String,
        serializedTransaction: String
    ): SuiScanTransactionRequest {
        return SuiScanTransactionRequest(
            chain = Chain.Sui.toName(),
            metadata = CommonMetadata(
                url = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = address,
            transaction = serializedTransaction,
        )
    }

    private companion object {
        private const val BLOCKAID_BASE_URL = "https://api.blockaid.io/v0"
        private const val VULTISIG_DOMAIN = "vultisig.com"

        private fun Chain.toName(): String {
            return when (this) {
                Chain.Arbitrum -> "arbitrum"
                Chain.Avalanche -> "avalanche"
                Chain.Base -> "base"
                Chain.Blast -> "blast"
                Chain.BscChain -> "bsc"
                Chain.Bitcoin -> "bitcoin"
                Chain.Ethereum -> "ethereum"
                Chain.Optimism -> "optimism"
                Chain.Polygon -> "polygon"
                Chain.Sui -> "sui"
                Chain.Solana -> "solana"
                else -> error("Chain: ${this.name} not supported by Blockaid")
            }
        }
    }
}