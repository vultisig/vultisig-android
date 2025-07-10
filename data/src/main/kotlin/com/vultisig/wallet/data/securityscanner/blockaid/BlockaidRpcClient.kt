package com.vultisig.wallet.data.securityscanner.blockaid


import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

class BlockaidRpcClient(
    private val httpClient: HttpClient,
) : BlockaidRpcClientContract {

    override suspend fun scanBitcoinTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponseJson {
        val bitcoinRequest = buildBitcoinScanRequest(address, serializedTransaction)

        return httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/bitcoin/transaction-raw/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(bitcoinRequest)
        }.bodyOrThrow<BlockaidTransactionScanResponseJson>()
    }

    override suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String
    ): BlockaidTransactionScanResponseJson {
        val evmRequest = buildEthereumScanRequest(chain, from, to, data, amount)

        return httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/evm/transaction/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(evmRequest)
        }.bodyOrThrow<BlockaidTransactionScanResponseJson>()
    }

    override suspend fun scanSolanaTransaction(
        address: String,
        serializedMessage: String
    ): BlockaidTransactionScanResponseJson {
        val solanaRequest = buildSolanaScanRequest(address, serializedMessage)

        return httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/solana/message/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(solanaRequest)
        }.bodyOrThrow<BlockaidTransactionScanResponseJson>()
    }

    override suspend fun scanSuiTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponseJson {
        val suiRequest = buildSuiScanRequest(address, serializedTransaction)

        return httpClient.post(BLOCKAID_BASE_URL) {
            url {
                appendPathSegments("/sui/transaction/scan")
            }
            contentType(ContentType.Application.Json)
            setBody(suiRequest)
        }.bodyOrThrow<BlockaidTransactionScanResponseJson>()
    }

    private fun buildBitcoinScanRequest(
        address: String,
        serializedTransaction: String
    ): BitcoinScanTransactionRequestJson {
        return BitcoinScanTransactionRequestJson(
            chain = Chain.Bitcoin.toName(),
            metadata = CommonMetadataJson(url = VULTISIG_DOMAIN),
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
    ): EthereumScanTransactionRequestJson {
        return EthereumScanTransactionRequestJson(
            chain = chain.toName(),
            metadata = EthereumScanTransactionRequestJson.MetadataJson(
                domain = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = from,
            simulatedWithEstimatedGas = false,
            data = EthereumScanTransactionRequestJson.DataJson(
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
    ): SolanaScanTransactionRequestJson {
        return SolanaScanTransactionRequestJson(
            chain = SOLANA_CHAIN,
            metadata = CommonMetadataJson(
                url = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = address,
            encoding = SOLANA_ENCODING,
            transactions = listOf(serializedMessage),
            method = SOLANA_SIGN_AND_SEND
        )
    }

    private fun buildSuiScanRequest(
        address: String,
        serializedTransaction: String
    ): SuiScanTransactionRequestJson {
        return SuiScanTransactionRequestJson(
            chain = Chain.Sui.toName(),
            metadata = CommonMetadataJson(
                url = VULTISIG_DOMAIN,
            ),
            options = listOf("validation"),
            accountAddress = address,
            transaction = serializedTransaction,
        )
    }

    private companion object {
        private const val BLOCKAID_BASE_URL = "https://api.vultisig.com/blockaid/v0"
        private const val VULTISIG_DOMAIN = "vultisig.com"

        private const val SOLANA_SIGN_AND_SEND = "signAndSendTransaction"
        private const val SOLANA_ENCODING = "base58"
        private const val SOLANA_CHAIN = "mainnet"

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