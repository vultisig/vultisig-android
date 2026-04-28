package com.vultisig.wallet.data.blockchain

import BlockchainSpecific
import Coin
import JsonReader
import KeysignPayload
import SignData
import TonSpecific
import TransactionData
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.data.chains.helpers.CardanoHelper
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.chains.helpers.CosmosHelper.Companion.ATOM_DENOM
import com.vultisig.wallet.data.chains.helpers.ERC20Helper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.RippleHelper
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.chains.helpers.TerraHelper
import com.vultisig.wallet.data.chains.helpers.TronHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage
import wallet.core.jni.CoinType
import wallet.core.jni.proto.TheOpenNetwork

class ChainHelpersTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun sendBSCTest() {
        val transactions: List<TransactionData> = loadTransactionData(BSC_JSON_FILE)

        val helper = EvmHelper(CoinType.SMARTCHAIN, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        val erc20Helper = ERC20Helper(CoinType.SMARTCHAIN, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                if (transaction.keysignPayload.coin.isNativeToken) {
                    helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                } else {
                    erc20Helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendEVMTest() {
        val transactions: List<TransactionData> = loadTransactionData(EVM_JSON_FILE)

        val helper = EvmHelper(CoinType.ETHEREUM, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        val erc20Helper = ERC20Helper(CoinType.ETHEREUM, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                if (transaction.keysignPayload.coin.isNativeToken) {
                    helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                } else {
                    erc20Helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendPOLTest() {
        val transactions: List<TransactionData> = loadTransactionData(POL_JSON_FILE)

        val helper = EvmHelper(CoinType.POLYGON, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        val erc20Helper = ERC20Helper(CoinType.POLYGON, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                if (transaction.keysignPayload.coin.isNativeToken) {
                    helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                } else {
                    erc20Helper.getPreSignedImageHash(
                        transaction.keysignPayload.toInternalKeySignPayload()
                    )
                }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendXRPTest() {
        val transactions: List<TransactionData> = loadTransactionData(XRP_JSON_FILE)

        transactions.forEach { transaction ->
            val preImageHashes =
                RippleHelper.getPreSignedImageHash(
                    transaction.keysignPayload.toInternalKeySignPayload()
                )
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTONTest() {
        val transactions: List<TransactionData> = loadTransactionData(TON_JSON_FILE)

        transactions.forEach { transaction ->
            val preImageHashes =
                TonHelper.getPreSignedImageHash(
                    transaction.keysignPayload.toInternalKeySignPayload()
                )
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    /**
     * Regression: verifies that per-message `payload` and `stateInit` fields are threaded into the
     * WalletCore SigningInput, and that multi-message signing emits one Transfer per TonMessage in
     * order. Mirrors iOS `TonSendTransactionTests.testTonConnectThreadsStateInitAndCustomPayload`.
     */
    @Test
    fun tonConnectPayloadAndStateInitAreThreadedIntoSigningInput() {
        val coin =
            Coin(
                chain = "Ton",
                ticker = "TON",
                address = "UQCc9iCgP_b5RMJcFE5XD8zStfjtNHLhDWfUqC5m1SjSer95",
                decimals = 9,
                priceProviderId = "the-open-network",
                isNativeToken = true,
                hexPublicKey = HEX_PUBLIC_KEY_EDDSA,
                logo = "ton",
            )
        val blockchainSpecific =
            BlockchainSpecific(
                tonSpecific =
                    TonSpecific(
                        sendMaxAmount = false,
                        sequenceNumber = 0L,
                        expireAt = 1753579977L,
                        bounceable = false,
                    )
            )
        val destA = "UQDmLe6ticcY_uLZsfurdYONshNuCn8IS81KcJ8p6M6ISMcB"
        val destB = "UQCc9iCgP_b5RMJcFE5XD8zStfjtNHLhDWfUqC5m1SjSer95"
        val customPayload = "te6cckEBAQEAAgAAABGw7yzH"
        val stateInit = "te6cckEBAQEAAgAAAEysuc0="

        val payload =
            KeysignPayload(
                    coin = coin,
                    toAddress = "",
                    toAmount = "0",
                    blockchainSpecific = blockchainSpecific,
                    signData =
                        SignData(
                            signTon =
                                SignTon(
                                    tonMessages =
                                        listOf(
                                            TonMessage(
                                                to = destA,
                                                amount = "10000000",
                                                payload = customPayload,
                                                stateInit = stateInit,
                                            ),
                                            TonMessage(to = destB, amount = "20000000"),
                                        )
                                )
                        ),
                    vaultPublicKeyEcdsa = HEX_PUBLIC_KEY,
                    libType = "DKLS",
                )
                .toInternalKeySignPayload()

        val inputData = TonHelper.getPreSignedInputData(payload)
        val signingInput = TheOpenNetwork.SigningInput.parseFrom(inputData)

        assertEquals(2, signingInput.messagesCount)
        assertEquals(stateInit, signingInput.getMessages(0).stateInit)
        assertEquals(customPayload, signingInput.getMessages(0).customPayload)
        assertEquals("", signingInput.getMessages(1).stateInit)
        assertEquals("", signingInput.getMessages(1).customPayload)
    }

    @Test
    fun sendCosmosTest() {
        val transactions: List<TransactionData> = loadTransactionData(COSMOS_JSON_FILE)
        val helper = CosmosHelper(CoinType.COSMOS, ATOM_DENOM)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendSolana() {
        val transactions: List<TransactionData> = loadTransactionData(SOLANA_JSON_FILE)
        val helper = SolanaHelper(HEX_PUBLIC_KEY_EDDSA)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendPolkadot() {
        val transactions: List<TransactionData> = loadTransactionData(DOT_JSON_FILE)
        val helper = PolkadotHelper(HEX_PUBLIC_KEY)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendSUI() {
        val transactions: List<TransactionData> = loadTransactionData(SUI_JSON_FILE)

        transactions.forEach { transaction ->
            val preImageHashes =
                SuiHelper.getPreSignedImageHash(
                    transaction.keysignPayload.toInternalKeySignPayload()
                )

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTerra() {
        val transactions: List<TransactionData> = loadTransactionData(TERRA_JSON_FILE)

        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val coin = payload.coin.coinType
            val helper = TerraHelper(coin, "uluna", 300000L)

            val preImageHashes = helper.getPreSignedImageHash(payload)

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTHORchain() {
        val transactions: List<TransactionData> = loadTransactionData(THORCHAIN_JSON_FILE)
        val helper = ThorChainHelper.thor(HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTronTest() {
        val transactions: List<TransactionData> = loadTransactionData(TRON_JSON_FILE)
        val helper = TronHelper(CoinType.TRON, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendMayaChainTest() {
        val transactions: List<TransactionData> = loadTransactionData(MAYACHAIN_JSON_FILE)

        val helper = ThorChainHelper.maya(HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendUTXO() {
        val transactions: List<TransactionData> = loadTransactionData(UTXO_JSON_FILE)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val coin = payload.coin.coinType

            val helper = UtxoHelper(coin, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendKUJIRATest() {
        val transactions: List<TransactionData> = loadTransactionData(KUJIRA_JSON_FILE)
        val helper =
            CosmosHelper(
                coinType = CoinType.KUJIRA,
                denom = Chain.Kujira.feeUnit,
                gasLimit = CosmosHelper.getChainGasLimit(Chain.Kujira),
            )

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendThorchainSwapTest() {
        val transactions: List<TransactionData> = loadTransactionData(THORCHAIN_SWAP_JSON_FILE)
        val swapHelper = THORChainSwaps(HEX_PUBLIC_KEY, HEX_CHAIN_CODE, HEX_PUBLIC_KEY_EDDSA)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val swapPayload = payload.swapPayload as SwapPayload.ThorChain
            var nonceIncrement = BigInteger.ZERO
            var preImageHashes = listOf<String>()
            payload.approvePayload?.let {
                val approveImageHashes = swapHelper.getPreSignedApproveImageHash(it, payload)
                nonceIncrement = nonceIncrement.add(BigInteger.ONE)
                preImageHashes = approveImageHashes
            }
            swapPayload.let {
                print(transaction.name)
                val swapHashes =
                    swapHelper.getPreSignedImageHash(swapPayload.data, payload, nonceIncrement)
                preImageHashes = preImageHashes + swapHashes
            }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendMayaChainSwapTest() {
        val transactions: List<TransactionData> = loadTransactionData(MAYA_SWAP_JSON_FILE)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val preImageHashes =
                SigningHelper.getKeysignMessages(
                    payload,
                    Vault(
                        id = "test-vault",
                        name = "Test Vault",
                        pubKeyECDSA = HEX_PUBLIC_KEY,
                        pubKeyEDDSA = HEX_PUBLIC_KEY,
                        hexChainCode = HEX_CHAIN_CODE,
                    ),
                )
            assertEquals(preImageHashes, transaction.expectedImageHash.sorted())
        }
    }

    @Test
    fun oneInchLifiSwapTest() {
        val transactions: List<TransactionData> = loadTransactionData(LIFI_SWAP_JSON_FILE)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val preImageHashes =
                SigningHelper.getKeysignMessages(
                    payload,
                    Vault(
                        id = "test-vault",
                        name = "Test Vault",
                        pubKeyECDSA = HEX_PUBLIC_KEY,
                        pubKeyEDDSA = HEX_PUBLIC_KEY,
                        hexChainCode = HEX_CHAIN_CODE,
                    ),
                )
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun oneInchArbitrumSwapTest() {
        val transactions: List<TransactionData> = loadTransactionData(ARB_SWAP_JSON_FILE)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val preImageHashes =
                SigningHelper.getKeysignMessages(
                    payload,
                    Vault(
                        id = "test-vault",
                        name = "Test Vault",
                        pubKeyECDSA = HEX_PUBLIC_KEY,
                        pubKeyEDDSA = HEX_PUBLIC_KEY,
                        hexChainCode = HEX_CHAIN_CODE,
                    ),
                )
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendCardano() {
        val transactions: List<TransactionData> = loadTransactionData(CARDANO_JSON_FILE)

        transactions.forEach { transaction ->
            val derivedAddress =
                CardanoUtils.createEnterpriseAddress(transaction.keysignPayload.coin.hexPublicKey)
            val internalPayload = transaction.keysignPayload.toInternalKeySignPayload()
            val payload =
                internalPayload.copy(
                    coin = internalPayload.coin.copy(address = derivedAddress),
                    toAddress = derivedAddress,
                )
            val preImageHashes = CardanoHelper.getPreSignedImageHash(payload)

            assertEquals(1, preImageHashes.size)
            assertTrue(
                "Expected 64-char hex hash but got: ${preImageHashes[0]}",
                preImageHashes[0].matches(Regex("[0-9a-f]{64}")),
            )
            if (transaction.expectedImageHash.isNotEmpty()) {
                assertEquals(transaction.expectedImageHash, preImageHashes)
            }
        }
    }

    private fun loadTransactionData(jsonFile: String): List<TransactionData> {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data =
            JsonReader.readJsonFromAsset(appContext, jsonFile)
                ?: error("Failed can't load payload $jsonFile")
        return json.decodeFromString(data)
    }

    private companion object {
        private const val BSC_JSON_FILE = "bsc.json"
        private const val EVM_JSON_FILE = "evm.json"
        private const val COSMOS_JSON_FILE = "cosmos.json"
        private const val XRP_JSON_FILE = "xrp.json"
        private const val TON_JSON_FILE = "ton.json"
        private const val SOLANA_JSON_FILE = "solana.json"
        private const val THORCHAIN_JSON_FILE = "thorchain.json"
        private const val MAYACHAIN_JSON_FILE = "maya.json"
        private const val TERRA_JSON_FILE = "terra.json"
        private const val UTXO_JSON_FILE = "utxo.json"
        private const val POL_JSON_FILE = "pol.json"
        private const val DOT_JSON_FILE = "dot.json"
        private const val SUI_JSON_FILE = "sui.json"
        private const val TRON_JSON_FILE = "tron.json"
        private const val KUJIRA_JSON_FILE = "kujira.json"

        private const val CARDANO_JSON_FILE = "cardano.json"

        private const val THORCHAIN_SWAP_JSON_FILE = "thorchainswap.json"
        private const val MAYA_SWAP_JSON_FILE = "mayaswap.json"
        private const val LIFI_SWAP_JSON_FILE = "lifiswap.json"

        private const val ARB_SWAP_JSON_FILE = "arb.json"

        private const val HEX_PUBLIC_KEY =
            "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b"
        private const val HEX_PUBLIC_KEY_EDDSA =
            "75be85178816db3bc71a4f3e64e5c89866d8b7daae827ba9cf4ecd1ed9e645d5"
        private const val HEX_CHAIN_CODE =
            "c9b189a8232b872b8d9ccd867d0db316dd10f56e729c310fe072adf5fd204ae7"
    }
}
