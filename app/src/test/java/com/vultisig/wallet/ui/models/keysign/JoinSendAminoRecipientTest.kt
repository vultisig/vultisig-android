package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.Message
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapperImpl
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.transaction.CosmosAmountUiModel
import com.vultisig.wallet.ui.models.transaction.CosmosTransfersUiModel
import com.vultisig.wallet.ui.models.transaction.TransactionDetailsUiModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CosmosMsg
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignSolana
import vultisig.keysign.v1.SignSui
import vultisig.keysign.v1.TransactionType

/**
 * The co-signer's join review for a Cosmos `signAmino` request takes its To and amount from the
 * signed amino `msgs`, never from the initiator's unsigned `toAddress` / `toAmount` display copy.
 */
internal class JoinSendAminoRecipientTest {

    private val atom = Coins.GaiaChain.ATOM.copy(address = SENDER)

    private val tokenRepository = mockk<TokenRepository>()
    private val vaultRepository = mockk<VaultRepository>()
    private val gasFeeToEstimatedFee = mockk<GasFeeToEstimatedFeeUseCase>()
    private val mapTransactionToUiModel = mockk<TransactionToUiModelMapper>()
    private val feeResolver = mockk<JoinKeysignFeeResolver>()
    private val convertTokenValueToFiat = mockk<ConvertTokenValueToFiatUseCase>()
    private val chainAccountAddressRepository = mockk<ChainAccountAddressRepository>()
    private val tokenMetadataResolver = mockk<TokenMetadataResolver>()
    private val presentation = slot<Transaction>()
    private var functionInfo: FunctionInfo? = null
    private var decodedSignDirect: CosmosMessage? = null

    private val builder =
        JoinSendUiModelBuilder(
            tokenRepository = tokenRepository,
            vaultRepository = vaultRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            convertTokenValueToFiat = convertTokenValueToFiat,
            mapTransactionToUiModel = mapTransactionToUiModel,
            mapTransactionHistoryData = SendTransactionHistoryDataMapperImpl(),
            addressBookRepository = mockk(relaxed = true),
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenMetadataResolver = tokenMetadataResolver,
            contractAbiRepository = mockk(relaxed = true),
            parseCosmosMessage = ParseCosmosMessageUseCase { requireNotNull(decodedSignDirect) },
            feeResolver = feeResolver,
            json = Json,
        )

    @Test
    fun `a single amino send shows the signed recipient and amount`() = runTest {
        val result = build(amino(send(RECIPIENT_B, "1000000000")), RECIPIENT_B, "1000000000")

        result.transaction.dstAddress shouldBe RECIPIENT_B
        result.transaction.tokenValue.value shouldBe BigInteger("1000000000")
        result.isUnverified() shouldBe false
    }

    @Test
    fun `a doctored toAddress and toAmount are ignored for the signed amino send`() = runTest {
        // The initiator shows 1 ATOM to A while the signed message pays 1000 ATOM to B.
        val result =
            build(
                amino(send(RECIPIENT_B, "1000000000")),
                toAddress = RECIPIENT_A,
                toAmount = "1000000",
            )

        result.transaction.dstAddress shouldBe RECIPIENT_B
        result.transaction.tokenValue.value shouldBe BigInteger("1000000000")
        result.isUnverified() shouldBe false
    }

    @Test
    fun `an unreadable amino request shows no recipient or amount and is flagged`() = runTest {
        val result =
            build(
                amino(send(RECIPIENT_A, "1"), send(RECIPIENT_B, "2")),
                toAddress = RECIPIENT_A,
                toAmount = "1000000",
            )

        result.transaction.dstAddress shouldBe ""
        result.transaction.tokenValue.value shouldBe BigInteger.ZERO
        result.isUnverified() shouldBe true
    }

    @Test
    fun `a two-recipient amino request lists both recipients and the total`() = runTest {
        val result =
            build(
                amino(send(RECIPIENT_A, "1000000"), send(RECIPIENT_B, "1000000000")),
                toAddress = "",
                toAmount = "1001000000",
            )

        result.transfers() shouldBe
            CosmosTransfersUiModel(
                recipients = listOf(RECIPIENT_A, RECIPIENT_B),
                amounts = listOf(CosmosAmountUiModel("1001 ATOM", isKnownDenom = true)),
                memos = emptyList(),
                isPartial = false,
            )
    }

    @Test
    fun `a send next to a contract call is listed and flagged as partial`() = runTest {
        val contractCall =
            CosmosMsg(
                type = "wasm/MsgExecuteContract",
                value =
                    """{"sender":"$SENDER","contract":"$CONTRACT","msg":{"swap":{}},""" +
                        """"funds":[{"denom":"uatom","amount":"2000000"}]}""",
            )

        val result =
            build(amino(send(RECIPIENT_A, "1000000"), contractCall), toAddress = "", toAmount = "0")

        result.transfers() shouldBe
            CosmosTransfersUiModel(
                recipients = listOf(RECIPIENT_A, CONTRACT),
                amounts = listOf(CosmosAmountUiModel("3 ATOM", isKnownDenom = true)),
                memos = emptyList(),
                isPartial = true,
            )
    }

    @Test
    fun `a single readable send keeps the To and amount rows and no transfer list`() = runTest {
        build(amino(send(RECIPIENT_B, "1000000000")), RECIPIENT_B, "1000000000")
            .transfers() shouldBe null
    }

    @Test
    fun `with both sign docs the amino one the signer uses is shown`() = runTest {
        // CosmosHelper signs signAmino ahead of signDirect, so a signDirect body paying A must not
        // stand in for the amino message that pays B.
        decodedSignDirect = signDoc(directSend(RECIPIENT_A, "1000000"))

        val result =
            build(
                amino(send(RECIPIENT_B, "1000000000")),
                toAddress = RECIPIENT_A,
                toAmount = "1000000",
                signDirect = SignDirectProto(bodyBytes = "body", authInfoBytes = "auth"),
            )

        result.transaction.dstAddress shouldBe RECIPIENT_B
        result.transaction.tokenValue.value shouldBe BigInteger("1000000000")
    }

    @Test
    fun `native send uses the vault sender and local units`() = runTest {
        val native = Coins.Ethereum.ETH
        val result =
            build(
                plainPayload(native.copy(address = "placeholder", decimal = 9, ticker = "OLD")),
                native,
            )

        result.transaction.srcAddress shouldBe SENDER
        result.transaction.tokenValue shouldBe TokenValue(BigInteger("123456"), native)
        result.simulationPayload.coin.address shouldBe SENDER
    }

    @Test
    fun `decoded EVM call keeps calldata for both scans`() = runTest {
        val native = Coins.Ethereum.ETH
        functionInfo = FunctionInfo("ping()", "[]", "Ping")
        val result = build(plainPayload(native).copy(memo = "abc!"), native)

        result.transaction.memo shouldBe "0x61626321"
        result.simulationPayload.memo shouldBe result.transaction.memo
        presentation.captured.memo shouldBe null
    }

    @Test
    fun `token send uses catalogue units for its actual contract`() = runTest {
        val usdc = Coins.Ethereum.USDC
        val result =
            build(plainPayload(usdc.copy(ticker = "OLD", decimal = 18)), Coins.Ethereum.ETH)

        result.transaction.tokenValue shouldBe TokenValue(BigInteger("123456"), usdc)
        result.transaction.token.contractAddress shouldBe usdc.contractAddress
    }

    @Test
    fun `unavailable token metadata leaves the amount in raw units`() = runTest {
        val token =
            Coins.Ethereum.USDC.copy(contractAddress = "0x0000000000000000000000000000000000000001")
        val result = build(plainPayload(token), Coins.Ethereum.ETH)

        result.transaction.tokenValue.decimals shouldBe 0
        result.details().unverifiedAssetAmount shouldBe "123456 ${token.contractAddress}"
        result.details().requiresUnverifiedCoSignConsent shouldBe true
    }

    @Test
    fun `opaque Solana and Sui requests require separate review consent`() = runTest {
        // These bytes are a placeholder. The review does not try to sign them.
        val solana = Coins.Solana.SOL
        val sui = Coins.Sui.SUI
        val solanaPayload =
            plainPayload(solana).copy(
                blockChainSpecific =
                    BlockChainSpecific.Solana(
                        recentBlockHash = "",
                        priorityFee = BigInteger.ZERO,
                        priorityLimit = BigInteger.ZERO,
                    ),
                signSolana = SignSolana(rawTransactions = listOf("dGVzdA==")),
            )
        val suiPayload =
            plainPayload(sui).copy(
                blockChainSpecific =
                    BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger.ZERO,
                        gasBudget = BigInteger.ZERO,
                        coins = emptyList(),
                    ),
                signSui = SignSui(unsignedTxMsg = "dGVzdA=="),
            )

        for ((payload, native) in listOf(solanaPayload to solana, suiPayload to sui)) {
            val result = build(payload, native)
            result.transaction.dstAddress shouldBe ""
            result.transaction.tokenValue.value shouldBe BigInteger.ZERO
            result.details().heroContent shouldBe HeroContent.Unverified
            result.details().requiresUnverifiedCoSignConsent shouldBe true
        }
    }

    private fun plainPayload(coin: Coin) =
        KeysignPayload(
            coin = coin,
            toAddress = "0x0000000000000000000000000000000000000002",
            toAmount = BigInteger("123456"),
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ONE,
                    priorityFeeWei = BigInteger.ZERO,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger("21000"),
                ),
            vaultPublicKeyECDSA = "ecdsa",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private suspend fun build(
        signAmino: SignAmino,
        toAddress: String,
        toAmount: String,
        signDirect: SignDirectProto? = null,
    ): JoinSendUiModelResult {
        val payload =
            KeysignPayload(
                coin = atom,
                toAddress = toAddress,
                toAmount = BigInteger(toAmount),
                blockChainSpecific =
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger.ONE,
                        sequence = BigInteger.ZERO,
                        gas = BigInteger("7500"),
                        ibcDenomTraces = null,
                        transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                    ),
                vaultPublicKeyECDSA = "ecdsa",
                vaultLocalPartyID = "party",
                libType = null,
                wasmExecuteContractPayload = null,
                signAmino = signAmino,
                signDirect = signDirect,
            )

        return build(payload, Coins.GaiaChain.ATOM)
    }

    private suspend fun build(payload: KeysignPayload, nativeCoin: Coin): JoinSendUiModelResult {
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "Main")
        coEvery { vaultRepository.getAll() } returns emptyList()
        coEvery { chainAccountAddressRepository.getAddress(any<Chain>(), any()) } returns
            (SENDER to "local-key")
        coEvery { tokenRepository.getNativeToken(any()) } returns nativeCoin
        coEvery { tokenMetadataResolver.resolve(any(), any()) } returns null
        coEvery { feeResolver.resolveJoinKeysignNetworkFee(any(), any(), any(), any()) } returns
            TokenValue(BigInteger("7500"), nativeCoin)
        coEvery { feeResolver.getTransactionFunctionInfo(any(), any()) } returns functionInfo
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedFiatValue = "$0.01",
                formattedTokenValue = "0.0075 ${nativeCoin.ticker}",
                tokenValue = TokenValue(BigInteger("7500"), nativeCoin),
                fiatValue = mockk(relaxed = true),
            )
        coEvery { mapTransactionToUiModel(capture(presentation)) } returns TransactionDetailsUiModel()
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)

        return requireNotNull(
            builder.build(
                payload = payload,
                srcVaultName = "Main",
                vaultId = VAULT_ID,
                currency = AppCurrency.USD,
            )
        )
    }

    private fun JoinSendUiModelResult.transfers(): CosmosTransfersUiModel? {
        val send = result.transactionTypeUiModel.shouldBeInstanceOf<TransactionTypeUiModel.Send>()
        return send.tx.cosmosTransfers
    }

    private fun JoinSendUiModelResult.isUnverified(): Boolean {
        return details().isUnverifiedCosmosSign
    }

    private fun JoinSendUiModelResult.details() =
        result.transactionTypeUiModel.shouldBeInstanceOf<TransactionTypeUiModel.Send>().tx

    private fun amino(vararg msgs: CosmosMsg) = SignAmino(msgs = msgs.toList())

    private fun send(recipient: String, amount: String) =
        CosmosMsg(
            type = "cosmos-sdk/MsgSend",
            value =
                """{"from_address":"$SENDER","to_address":"$recipient",""" +
                    """"amount":[{"denom":"uatom","amount":"$amount"}]}""",
        )

    private fun directSend(recipient: String, amount: String) =
        Message(
            typeUrl = "/cosmos.bank.v1beta1.MsgSend",
            value =
                buildJsonObject {
                    put("fromAddress", SENDER)
                    put("toAddress", recipient)
                    putJsonArray("amount") {
                        addJsonObject {
                            put("denom", "uatom")
                            put("amount", amount)
                        }
                    }
                },
        )

    private fun signDoc(vararg messages: Message) =
        CosmosMessage(
            chainId = "cosmoshub-4",
            accountNumber = "1",
            sequence = "0",
            memo = "",
            messages = messages.toList(),
            authInfoFee = Fee(amount = emptyList(), gasLimit = "200000"),
        )

    private companion object {
        const val VAULT_ID = "vault"
        const val SENDER = "cosmos1sender0000000000000000000000000000000"
        const val RECIPIENT_A = "cosmos1recipienta000000000000000000000000000"
        const val CONTRACT = "cosmos1contract00000000000000000000000000000"
        const val RECIPIENT_B = "cosmos1recipientb000000000000000000000000000"
    }
}
