@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.blockchain

import BlockchainSpecific
import Coin
import JsonReader
import KeysignPayload
import SignData
import SolanaSpecific
import TonSpecific
import TransactionData
import TriggerSmartContractPayload
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.data.chains.helpers.BittensorHelper
import com.vultisig.wallet.data.chains.helpers.CardanoHelper
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.chains.helpers.CosmosHelper.Companion.ATOM_DENOM
import com.vultisig.wallet.data.chains.helpers.ERC20Helper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.QBTCTransactionHelper
import com.vultisig.wallet.data.chains.helpers.RippleHelper
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.chains.helpers.TerraHelper
import com.vultisig.wallet.data.chains.helpers.TronHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vultisig.keysign.v1.SignSui
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

    /**
     * One native-send vector per EVM chain not otherwise covered by [sendEVMTest]/[sendBSCTest]/
     * [sendPOLTest]. Every case's payload is identical apart from `coin.chain` (and that chain's
     * real native-asset ticker/logo, which the EVM signing input never reads), so the only thing
     * that can separate their pre-image hashes is the EIP-155 chain ID — see
     * [evmChainMatrixVectorsAreChainSpecific] for the guard that recomputes this directly. Mirrors
     * iOS `ChainHelperTests.testEvmChainMatrixFixture`.
     */
    @Test
    fun sendEvmChainMatrixTest() {
        val transactions: List<TransactionData> = loadTransactionData(EVM_CHAIN_MATRIX_JSON_FILE)

        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val helper = EvmHelper(payload.coin.chain.coinType, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
            val preImageHashes = helper.getPreSignedImageHash(payload)

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    /**
     * Every EVM native send in the corpus must produce a different pre-image hash. The hashes are
     * *recomputed* here from the payloads rather than read back out of the fixtures: since every
     * matrix payload holds every pre-image-feeding field identical and varies only the chain, the
     * only thing that can separate them is the EIP-155 chain ID. A chain that fell back to its
     * WalletCore coin type's default chain ID — the hazard for chains sharing a coin type — would
     * collapse onto another chain's hash while each vector still individually matched its own
     * committed value; only the relationship between them exposes that. Mirrors iOS
     * `testEvmChainMatrixVectorsAreChainSpecific`.
     */
    @Test
    fun evmChainMatrixVectorsAreChainSpecific() {
        val nativeSends =
            (loadTransactionData(EVM_CHAIN_MATRIX_JSON_FILE) + loadTransactionData(EVM_JSON_FILE))
                .filter { it.keysignPayload.coin.isNativeToken }

        val hashesByCase =
            nativeSends.associate { transaction ->
                val payload = transaction.keysignPayload.toInternalKeySignPayload()
                val helper = EvmHelper(payload.coin.chain.coinType, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
                transaction.name to helper.getPreSignedImageHash(payload)
            }

        val allHashes = hashesByCase.values.flatten()
        assertEquals(
            "Every EVM native send should produce exactly one pre-image hash",
            hashesByCase.size,
            allHashes.size,
        )
        assertEquals(
            "Two EVM native sends computed the same pre-image hash. Their payloads differ only " +
                "by chain, so a chain has stopped contributing its own EIP-155 chain ID: $hashesByCase",
            allHashes.size,
            allHashes.toSet().size,
        )
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

    /**
     * Regression for the STON.fi swap that never finished co-signing: every TonConnect message must
     * carry the wallet-level `tonSpecific.bounceable` flag, NOT a per-address value derived from
     * the EQ/UQ friendly-address tag. The initiating device (browser extension / desktop) applies
     * the global flag when it builds the DKLS setup message, so a co-signer that derived
     * bounceability per-address produced a different pre-image hash — and therefore a different md5
     * message-id — for any message sent to a UQ (non-bounceable) address, 404ing on the setup
     * message forever.
     */
    @Test
    fun tonConnectBounceableFollowsWalletFlagNotAddressPrefix() {
        val coin =
            Coin(
                chain = "Ton",
                ticker = "TON",
                address = "UQCmBAnvlatV6yLWm391BEFTWP7jZX5mUFJ4Dc5TJAnvVgYi",
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
                        bounceable = true,
                    )
            )
        // First goes to an EQ (bounceable) address, second to a UQ (non-bounceable) address — the
        // per-address logic would have flagged these true/false; the wallet flag makes both true.
        val destEq = "EQDa4VOnTYlLvDJ0gZjNYm5PXfSmmtL6Vs6A_CZEtXCNICq_"
        val destUq = "UQCmBAnvlatV6yLWm391BEFTWP7jZX5mUFJ4Dc5TJAnvVgYi"

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
                                            TonMessage(to = destEq, amount = "37100000000"),
                                            TonMessage(to = destUq, amount = "1000000"),
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
        assertEquals(true, signingInput.getMessages(0).bounceable)
        assertEquals(true, signingInput.getMessages(1).bounceable)
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

    // The relayed dynamic gas limit (proto `CosmosSpecific.gas_limit`) lands in the signed
    // `AuthInfo.Fee.gas_limit`, which every co-signer hashes into the SignDoc. The pre-image hash
    // is
    // therefore a faithful witness that `buildCosmosFee` honors it: a relayed limit that differs
    // from
    // the static per-chain limit must change the hash, while a relayed limit equal to the static
    // one
    // must reproduce the static-limit hash byte-for-byte (proving the field drives `fee.gas` rather
    // than being ignored). Derives from real COSMOS fixture data so the coin address / pubkey pass
    // WalletCore validation.
    @Test
    fun cosmosRelayedGasLimitHonoredInSignedBytes() {
        val staticLimit = CosmosHelper.DEFAULT_COSMOS_GAS_LIMIT
        val helper = CosmosHelper(CoinType.COSMOS, ATOM_DENOM, staticLimit)
        val basePayload =
            loadTransactionData(COSMOS_JSON_FILE).first().keysignPayload.toInternalKeySignPayload()
        val baseCosmos = basePayload.blockChainSpecific as BlockChainSpecific.Cosmos

        fun hashWith(gasLimit: BigInteger?) =
            helper.getPreSignedImageHash(
                basePayload.copy(blockChainSpecific = baseCosmos.copy(gasLimit = gasLimit))
            )

        val staticHash = hashWith(gasLimit = null)
        val relayedHash = hashWith(gasLimit = BigInteger.valueOf(staticLimit + 123_456))
        val relayedEqualToStaticHash = hashWith(gasLimit = BigInteger.valueOf(staticLimit))

        assertNotEquals(staticHash, relayedHash)
        assertEquals(staticHash, relayedEqualToStaticHash)
    }

    /**
     * One send per Cosmos-family chain not otherwise covered by [sendCosmosTest]/ [sendKUJIRATest],
     * plus one genuine IBC transfer (`transaction_type` 3, routed through [CosmosHelper]'s
     * `TRANSACTION_TYPE_IBC_TRANSFER` branch rather than a plain send whose memo merely looks like
     * IBC routing info). Regression coverage for issue #5421 item 5.
     */
    @Test
    fun sendCosmosChainMatrixTest() {
        val transactions: List<TransactionData> = loadTransactionData(COSMOS_CHAIN_MATRIX_JSON_FILE)

        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val chain = payload.coin.chain
            val helper =
                CosmosHelper(chain.coinType, chain.feeUnit, CosmosHelper.getChainGasLimit(chain))
            val preImageHashes = helper.getPreSignedImageHash(payload)

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    /**
     * QBTC signs with ML-DSA (post-quantum) and its `TxRaw`/`SignDoc`/`AuthInfo` are hand-built
     * with a manual protobuf wire-format encoder in [QBTCTransactionHelper] — no WalletCore
     * `TransactionCompiler` involved at all. Together with [sendBittensorTest], this is the other
     * hand-rolled path issue #5421 flags as highest-risk for cross-platform divergence. Regression
     * coverage for issue #5421 item 6.
     */
    @Test
    fun sendQBTCTest() {
        val transactions: List<TransactionData> = loadTransactionData(QBTC_JSON_FILE)
        val helper = QBTCTransactionHelper()

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

    /**
     * Bittensor bypasses TW Core's `TransactionCompiler` entirely (hand-rolled SCALE-encoded
     * extrinsic — see [BittensorHelper]'s class doc: TW Core doesn't support the CheckMetadataHash
     * signed extension Bittensor requires), so it is exactly the class of hand-rolled signing path
     * issue #5421 identifies as most likely to diverge across platforms. Regression coverage for
     * issue #5421 item 6.
     */
    @Test
    fun sendBittensorTest() {
        val transactions: List<TransactionData> = loadTransactionData(BITTENSOR_JSON_FILE)
        val helper = BittensorHelper(HEX_PUBLIC_KEY_EDDSA)

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

    /**
     * Parity for issue #4881: a dApp-supplied Sui PTB (`signSui`) signed through WalletCore's
     * SignDirect path must produce the same intent-prefixed digest the existing
     * [com.vultisig.wallet.data.chains.helpers.SwapKitSuiSigner] computes by hand
     * (`blake2b_32([0x00,0x00,0x00] || ptb)`). The SignDirect input carries the verbatim base64
     * `TransactionData` BCS bytes — no Pay/PaySui rebuild — and the resulting sighash must match
     * the cross-platform (iOS / extension / SDK) digest so co-signing converges.
     */
    @Test
    fun signSuiSignDirectMatchesIntentPrefixedDigest() {
        // base64 TransactionData BCS bytes. The digest is taken over these opaque bytes under the
        // Sui transaction intent, so the exact contents are irrelevant to the parity assertion.
        val ptbBase64 = "AAACAAgA4fUFAAAAAAAgWqQ5q8s0e0kq0a7s3w2QxJYwq7XmZ1pL0c1d8s2f3g4="
        val suiCoin =
            com.vultisig.wallet.data.models.Coin(
                chain = Chain.Sui,
                ticker = "SUI",
                logo = "sui",
                address = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
                decimal = 9,
                hexPublicKey = HEX_PUBLIC_KEY_EDDSA,
                priceProviderID = "sui",
                contractAddress = "",
                isNativeToken = true,
            )
        val payload =
            com.vultisig.wallet.data.models.payload.KeysignPayload(
                coin = suiCoin,
                toAddress = "",
                toAmount = BigInteger.ZERO,
                blockChainSpecific =
                    com.vultisig.wallet.data.models.payload.BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger.ZERO,
                        gasBudget = BigInteger.ZERO,
                        coins = emptyList(),
                    ),
                vaultPublicKeyECDSA = HEX_PUBLIC_KEY,
                vaultLocalPartyID = "local",
                libType = null,
                wasmExecuteContractPayload = null,
                signSui = SignSui(unsignedTxMsg = ptbBase64),
            )

        val actual = SuiHelper.getPreSignedImageHash(payload)

        // Oracle: Sui signing digest = blake2b_32(intent_prefix(scope=0,version=0,app=0) || ptb),
        // computed independently here so a drift in WalletCore's SignDirect hashing surfaces.
        val ptbBytes = Base64.getDecoder().decode(ptbBase64)
        val intentMessage = byteArrayOf(0x00, 0x00, 0x00) + ptbBytes
        val blake = Blake2bDigest(256)
        blake.update(intentMessage, 0, intentMessage.size)
        val expectedDigest = ByteArray(blake.digestSize).also { blake.doFinal(it, 0) }

        assertEquals(listOf(expectedDigest.toHexString()), actual)
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

    // Terra / Terra Classic bank sends route through TerraHelper (not CosmosHelper), so the relayed
    // gas limit must be honored there too — otherwise an iOS/macOS-initiated send that relays a
    // simulated `gas_limit` produces a SignDoc whose `fee.gas` diverges from what Android signs,
    // and
    // the threshold signature never forms. The fee AMOUNT is `chainSpecific.gas` verbatim (priced
    // at
    // the effective limit up front by the initiator), so the relayed limit moves only `fee.gas`.
    // This pins byte parity: a relayed limit ≠ static changes the pre-image hash; a relayed limit
    // ==
    // static reproduces the static-limit hash byte-for-byte.
    @Test
    fun terraClassicRelayedGasLimitHonoredInSignedBytes() {
        val staticLimit = 300_000L
        val basePayload =
            loadTransactionData(TERRA_JSON_FILE)
                .map { it.keysignPayload.toInternalKeySignPayload() }
                .first { it.coin.chain == Chain.TerraClassic }
        val baseCosmos = basePayload.blockChainSpecific as BlockChainSpecific.Cosmos
        val helper = TerraHelper(basePayload.coin.coinType, "uluna", staticLimit)

        fun hashWith(gasLimit: BigInteger?) =
            helper.getPreSignedImageHash(
                basePayload.copy(blockChainSpecific = baseCosmos.copy(gasLimit = gasLimit))
            )

        val staticHash = hashWith(gasLimit = null)
        val relayedHash = hashWith(gasLimit = BigInteger.valueOf(staticLimit + 123_456))
        val relayedEqualToStaticHash = hashWith(gasLimit = BigInteger.valueOf(staticLimit))

        assertNotEquals(staticHash, relayedHash)
        assertEquals(staticHash, relayedEqualToStaticHash)
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

    /**
     * TCY send + intra-chain TCY -> RUNE swap (`MsgDeposit` with a swap memo), the same
     * non-RUNE-denom deposit shape `thorchain.json` already covers for RUJI/KUJI. Regression for
     * vultisig-windows#4464. Mirrors iOS `ChainHelperTests.testTcyFixture`.
     */
    @Test
    fun sendTCYTest() {
        val transactions: List<TransactionData> = loadTransactionData(TCY_JSON_FILE)
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
        val transactions =
            loadTransactionData(UTXO_JSON_FILE) +
                loadTransactionData(UTXO_DASH_ZCASH_JSON_FILE)
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
        val transactions =
            loadTransactionData(THORCHAIN_SWAP_JSON_FILE) +
                loadTransactionData(THORCHAIN_SWAP_LIMIT_ORDER_JSON_FILE)
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
            val internalPayload = transaction.keysignPayload.toInternalKeySignPayload()
            val cardano = internalPayload.blockChainSpecific as BlockChainSpecific.Cardano

            // The signer forces the transmitted byteFee carried on the payload (Option 1): the body
            // uses the shared fee rather than each device re-deriving its own, so every co-signer
            // produces an identical sighash regardless of WalletCore version.
            val preImageHashes = CardanoHelper.getPreSignedImageHash(internalPayload)

            assertEquals(1, preImageHashes.size)
            assertTrue(
                "Expected 64-char hex hash but got: ${preImageHashes[0]}",
                preImageHashes[0].matches(Regex("[0-9a-f]{64}")),
            )
            if (transaction.expectedImageHash.isNotEmpty()) {
                assertEquals(transaction.expectedImageHash, preImageHashes)
            }

            // For ordinary sends a different transmitted byteFee must change the sighash, proving
            // the fee is forced into the signed body (not silently re-derived and the transmitted
            // value discarded). WalletCore ignores forceFee for max-amount sends (the output is
            // total - fee, so it always derives), so the assertion only holds when not sending max.
            if (!cardano.sendMaxAmount) {
                val tamperedPayload =
                    internalPayload.copy(
                        blockChainSpecific = cardano.copy(byteFee = cardano.byteFee + 1_000)
                    )
                assertNotEquals(
                    preImageHashes,
                    CardanoHelper.getPreSignedImageHash(tamperedPayload),
                )
            }
        }
    }

    @Test
    fun cardanoInitiatorDerivesSizeBasedFee() {
        val transactions: List<TransactionData> = loadTransactionData(CARDANO_JSON_FILE)
        transactions.forEach { transaction ->
            val internalPayload = transaction.keysignPayload.toInternalKeySignPayload()
            val cardano = internalPayload.blockChainSpecific as BlockChainSpecific.Cardano

            // The initiator derives the size-based fee once with no forced fee. The fixture's
            // byteFee is the canonical cross-platform transmitted constant (forced verbatim by the
            // signer), not this derived value, so we assert the derivation yields a real size-based
            // fee: it must cover Cardano's fixed minimum (minFeeB = 155_381 lovelace).
            val derivedFee =
                CardanoHelper.estimateFee(
                    toAmount = internalPayload.toAmount.toLong(),
                    toAddress = internalPayload.toAddress,
                    changeAddress = internalPayload.coin.address,
                    sendMaxAmount = cardano.sendMaxAmount,
                    ttl = cardano.ttl.toLong(),
                    utxos = internalPayload.utxos,
                    memo = internalPayload.memo,
                )

            assertTrue(
                "Derived Cardano fee $derivedFee must cover minFeeB (155381)",
                derivedFee >= 155_381,
            )
        }
    }

    @Test
    fun cardanoMemoChangesSighash() {
        val transactions: List<TransactionData> = loadTransactionData(CARDANO_JSON_FILE)
        transactions.forEach { transaction ->
            val internalPayload = transaction.keysignPayload.toInternalKeySignPayload()
            val cardano = internalPayload.blockChainSpecific as BlockChainSpecific.Cardano

            // WalletCore ignores forceFee for max-amount sends (output = total - fee), so the
            // memo-driven aux hash can't be isolated from the fee there; assert only on ordinary
            // sends, matching cardanoSignerForcesTransmittedByteFee.
            if (cardano.sendMaxAmount) return@forEach

            // Attaching a CIP-20 memo must flow through setAuxiliaryData into the pre-image hash:
            // WalletCore commits blake2b-256(auxData) into body key 7, so the sighash MUST differ
            // from the memo-less payload. Mirrors iOS's CardanoCip20SigningTests.
            val noMemoHash = CardanoHelper.getPreSignedImageHash(internalPayload.copy(memo = null))
            val memoHash =
                CardanoHelper.getPreSignedImageHash(internalPayload.copy(memo = "hello world"))

            assertTrue(
                "Expected 64-char hex hash but got: ${memoHash[0]}",
                memoHash[0].matches(Regex("[0-9a-f]{64}")),
            )
            assertNotEquals(noMemoHash, memoHash)
        }
    }

    /**
     * The golden corpus is shared with vultisig-ios (`VultisigAppTests/TestData`) and the TS core
     * (`packages/core/mpc/keysign/tests/fixtures/mobile`). MPC keysign requires every co-signing
     * platform to derive byte-identical pre-image hashes, so a payload class pinned on two
     * platforms but not the third can drift silently and only surface as a live keysign failure.
     *
     * Fixtures are reached through [FIXTURE_TESTS] rather than the asset directory itself, so
     * dropping a `.json` into `androidTest/assets` without wiring it to a helper would otherwise be
     * invisible. This asserts the declared files and the directory agree in both directions, that
     * each declared file names a test that actually exists to hash it, that every file holds at
     * least one case, and that the corpus totals [EXPECTED_CASE_COUNT] — so a fixture can neither
     * appear untested nor silently stop contributing cases. Mirrors the file-count assertion the TS
     * runner makes over its own fixtures directory (issue #5420).
     */
    @Test
    fun fixtureCorpusMatchesDeclaredFileSet() {
        val assetFiles =
            InstrumentationRegistry.getInstrumentation()
                .context
                .assets
                .list("")
                .orEmpty()
                .filter { it.endsWith(".json") }
                .toSet()
        val declaredFiles = FIXTURE_TESTS.keys

        assertEquals(
            "Fixture files present in androidTest/assets but not declared in FIXTURE_TESTS — " +
                "declare each one against the test that signs it, or the corpus grows untested",
            emptySet<String>(),
            assetFiles - declaredFiles,
        )
        assertEquals(
            "Fixture files declared in FIXTURE_TESTS but absent from androidTest/assets",
            emptySet<String>(),
            declaredFiles - assetFiles,
        )

        // Parsing a fixture is not the same as signing it: without this, a file could be declared,
        // parse cleanly, count toward the total, and still have no test deriving hashes from it.
        val signingTests =
            ChainHelpersTest::class
                .java
                .methods
                .filter { it.isAnnotationPresent(Test::class.java) }
                .map { it.name }
                .toSet()
        assertEquals(
            "Fixture files bound to a test that doesn't exist or isn't annotated @Test — the " +
                "fixture would be parsed but never signed",
            emptyMap<String, String>(),
            FIXTURE_TESTS.filterValues { it !in signingTests },
        )

        val caseCountsByFile = declaredFiles.associateWith { loadTransactionData(it).size }
        val emptyFiles = caseCountsByFile.filterValues { it == 0 }.keys
        assertEquals("Fixture files that parsed to zero cases", emptySet<String>(), emptyFiles)
        assertEquals(
            "Golden fixture case count changed — update EXPECTED_CASE_COUNT deliberately, and " +
                "keep the case in step with vultisig-ios and the TS core",
            EXPECTED_CASE_COUNT,
            caseCountsByFile.values.sum(),
        )
    }

    /**
     * `SolanaSpecific.priorityLimit`/`hasProgramId` and `TriggerSmartContractPayload`'s snake_case
     * aliases exist so a fixture can be copied verbatim from iOS/TS-core without a field silently
     * deserializing to its default, but no golden fixture case currently reaches an asserted hash
     * through those aliases: the one `compute_limit` case in `solana.json` takes the `signSolana`
     * raw-transaction shortcut, and the one `TriggerSmartContractPayload` case in `tron.json` uses
     * the camelCase spelling. Decoding directly here is a cheap way to prove the aliases resolve to
     * the right field without needing new cross-repo golden fixtures.
     */
    @Test
    fun jsonNamesAcceptBothFieldSpellings() {
        val solanaSpecific =
            json.decodeFromString<SolanaSpecific>(
                """
                {
                  "recent_block_hash": "hash",
                  "priority_fee": "1",
                  "program_id": true,
                  "compute_limit": "100000"
                }
                """
                    .trimIndent()
            )
        assertEquals(true, solanaSpecific.hasProgramId)
        assertEquals("100000", solanaSpecific.priorityLimit)

        val triggerSmartContractPayload =
            json.decodeFromString<TriggerSmartContractPayload>(
                """
                {
                  "owner_address": "owner",
                  "contract_address": "contract",
                  "call_value": "1",
                  "call_token_value": "2",
                  "data": "deadbeef"
                }
                """
                    .trimIndent()
            )
        assertEquals("owner", triggerSmartContractPayload.ownerAddress)
        assertEquals("contract", triggerSmartContractPayload.contractAddress)
        assertEquals("1", triggerSmartContractPayload.callValue)
        assertEquals("2", triggerSmartContractPayload.callDataValue)
    }

    private fun loadTransactionData(jsonFile: String): List<TransactionData> {
        val callerTest =
            Thread.currentThread()
                .stackTrace
                .first {
                    it.className == ChainHelpersTest::class.java.name &&
                        it.methodName != "loadTransactionData"
                }
                .methodName
        loadedFilesByTestMethod.getOrPut(callerTest) { mutableSetOf() }.add(jsonFile)

        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data =
            JsonReader.readJsonFromAsset(appContext, jsonFile)
                ?: error("Failed can't load payload $jsonFile")
        return json.decodeFromString(data)
    }

    private companion object {
        /** file -> names of tests observed calling [loadTransactionData] with that file. */
        private val loadedFilesByTestMethod = mutableMapOf<String, MutableSet<String>>()

        private const val BSC_JSON_FILE = "bsc.json"
        private const val EVM_JSON_FILE = "evm.json"
        private const val EVM_CHAIN_MATRIX_JSON_FILE = "evm-chain-matrix.json"
        private const val TCY_JSON_FILE = "tcy.json"
        private const val COSMOS_JSON_FILE = "cosmos.json"
        private const val COSMOS_CHAIN_MATRIX_JSON_FILE = "cosmos-chain-matrix.json"
        private const val XRP_JSON_FILE = "xrp.json"
        private const val TON_JSON_FILE = "ton.json"
        private const val SOLANA_JSON_FILE = "solana.json"
        private const val THORCHAIN_JSON_FILE = "thorchain.json"
        private const val MAYACHAIN_JSON_FILE = "maya.json"
        private const val TERRA_JSON_FILE = "terra.json"
        private const val UTXO_JSON_FILE = "utxo.json"
        private const val UTXO_DASH_ZCASH_JSON_FILE = "utxo-dash-zcash.json"
        private const val POL_JSON_FILE = "pol.json"
        private const val DOT_JSON_FILE = "dot.json"
        private const val BITTENSOR_JSON_FILE = "bittensor.json"
        private const val QBTC_JSON_FILE = "qbtc.json"
        private const val SUI_JSON_FILE = "sui.json"
        private const val TRON_JSON_FILE = "tron.json"
        private const val KUJIRA_JSON_FILE = "kujira.json"

        private const val CARDANO_JSON_FILE = "cardano.json"

        private const val THORCHAIN_SWAP_JSON_FILE = "thorchainswap.json"
        private const val THORCHAIN_SWAP_LIMIT_ORDER_JSON_FILE =
            "thorchainswap-limit-order.json"
        private const val MAYA_SWAP_JSON_FILE = "mayaswap.json"
        private const val LIFI_SWAP_JSON_FILE = "lifiswap.json"

        private const val ARB_SWAP_JSON_FILE = "arb.json"

        /**
         * Every fixture file in `androidTest/assets`, bound to the test that hashes its cases.
         * Declaring a file is not enough on its own: [fixtureCorpusMatchesDeclaredFileSet] also
         * requires the named test to exist, so a fixture can't sit in the corpus parsed but never
         * signed.
         */
        private val FIXTURE_TESTS =
            mapOf(
                BSC_JSON_FILE to "sendBSCTest",
                EVM_JSON_FILE to "sendEVMTest",
                EVM_CHAIN_MATRIX_JSON_FILE to "sendEvmChainMatrixTest",
                TCY_JSON_FILE to "sendTCYTest",
                COSMOS_JSON_FILE to "sendCosmosTest",
                COSMOS_CHAIN_MATRIX_JSON_FILE to "sendCosmosChainMatrixTest",
                XRP_JSON_FILE to "sendXRPTest",
                TON_JSON_FILE to "sendTONTest",
                SOLANA_JSON_FILE to "sendSolana",
                THORCHAIN_JSON_FILE to "sendTHORchain",
                MAYACHAIN_JSON_FILE to "sendMayaChainTest",
                TERRA_JSON_FILE to "sendTerra",
                UTXO_JSON_FILE to "sendUTXO",
                UTXO_DASH_ZCASH_JSON_FILE to "sendUTXO",
                POL_JSON_FILE to "sendPOLTest",
                DOT_JSON_FILE to "sendPolkadot",
                BITTENSOR_JSON_FILE to "sendBittensorTest",
                QBTC_JSON_FILE to "sendQBTCTest",
                SUI_JSON_FILE to "sendSUI",
                TRON_JSON_FILE to "sendTronTest",
                KUJIRA_JSON_FILE to "sendKUJIRATest",
                CARDANO_JSON_FILE to "sendCardano",
                THORCHAIN_SWAP_JSON_FILE to "sendThorchainSwapTest",
                THORCHAIN_SWAP_LIMIT_ORDER_JSON_FILE to "sendThorchainSwapTest",
                MAYA_SWAP_JSON_FILE to "sendMayaChainSwapTest",
                LIFI_SWAP_JSON_FILE to "oneInchLifiSwapTest",
                ARB_SWAP_JSON_FILE to "oneInchArbitrumSwapTest",
            )

        // +5: cosmos-chain-matrix.json (issue #5421 item 5, Osmosis/Dydx/Noble/Akash + one IBC
        // transfer). +2: utxo-dash-zcash.json (item 4). +1: bittensor.json (item 6).
        // +1: qbtc.json (item 6). +1: thorchainswap-limit-order.json (item 7).
        // The standalone fixture files are shared byte-for-byte with the Swift and TS corpora,
        // preserving the cross-signer agreement required by #5421 and vultisig-sdk#1585.
        private const val EXPECTED_CASE_COUNT = 87

        private const val HEX_PUBLIC_KEY =
            "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b"
        private const val HEX_PUBLIC_KEY_EDDSA =
            "75be85178816db3bc71a4f3e64e5c89866d8b7daae827ba9cf4ecd1ed9e645d5"
        private const val HEX_CHAIN_CODE =
            "c9b189a8232b872b8d9ccd867d0db316dd10f56e729c310fe072adf5fd204ae7"

        /**
         * [fixtureCorpusMatchesDeclaredFileSet] only checks that a [FIXTURE_TESTS] value names a
         * real `@Test` method — not that the method's body actually loads that key's file. A
         * mis-bound entry (e.g. pointing a file at an unrelated existing test) would otherwise stay
         * green while that file is never signed. [loadTransactionData] records its caller in
         * [loadedFilesByTestMethod], and this runs after every test in the class so every binding
         * has had a chance to be proven true.
         *
         * Only flags a binding whose test actually ran this session but never touched the file — a
         * bound test absent from [loadedFilesByTestMethod] (e.g. because only one test method was
         * selected to run) is silently skipped rather than failed, so running a single test in
         * isolation still works.
         */
        @JvmStatic
        @AfterClass
        fun verifyFixtureBindingsWereActuallyLoaded() {
            val unproven =
                FIXTURE_TESTS.filter { (file, test) ->
                    val filesLoadedByTest = loadedFilesByTestMethod[test]
                    filesLoadedByTest != null && file !in filesLoadedByTest
                }
            assertEquals(
                "FIXTURE_TESTS binds a file to a test whose body never loaded it — the binding " +
                    "has drifted from what the test actually signs",
                emptyMap<String, String>(),
                unproven,
            )
        }
    }
}
