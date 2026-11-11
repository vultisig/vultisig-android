package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.Message
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapperImpl
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.transaction.TransactionDetailsUiModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
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
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenMetadataResolver = mockk(relaxed = true),
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

    private suspend fun build(
        signAmino: SignAmino,
        toAddress: String,
        toAmount: String,
        signDirect: SignDirectProto? = null,
    ): JoinSendUiModelResult {
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "Main")
        coEvery { vaultRepository.getAll() } returns emptyList()
        coEvery { tokenRepository.getNativeToken(any()) } returns Coins.GaiaChain.ATOM
        coEvery { feeResolver.resolveJoinKeysignNetworkFee(any(), any(), any(), any()) } returns
            TokenValue(BigInteger("7500"), Coins.GaiaChain.ATOM)
        coEvery { feeResolver.getTransactionFunctionInfo(any(), any()) } returns null
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedFiatValue = "$0.01",
                formattedTokenValue = "0.0075 ATOM",
                tokenValue = TokenValue(BigInteger("7500"), Coins.GaiaChain.ATOM),
                fiatValue = mockk(relaxed = true),
            )
        coEvery { mapTransactionToUiModel(any()) } returns TransactionDetailsUiModel()
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)

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

        return requireNotNull(
            builder.build(
                payload = payload,
                srcVaultName = "Main",
                vaultId = VAULT_ID,
                currency = AppCurrency.USD,
            )
        )
    }

    private fun JoinSendUiModelResult.isUnverified(): Boolean {
        val send = result.transactionTypeUiModel.shouldBeInstanceOf<TransactionTypeUiModel.Send>()
        return send.tx.isUnverifiedCosmosSign
    }

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
        const val RECIPIENT_B = "cosmos1recipientb000000000000000000000000000"
    }
}
