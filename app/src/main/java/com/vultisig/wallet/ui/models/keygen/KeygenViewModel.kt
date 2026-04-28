@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.keygen.DKLSKeygen
import com.vultisig.wallet.data.keygen.KeygenRouting
import com.vultisig.wallet.data.keygen.MldsaKeygen
import com.vultisig.wallet.data.keygen.SchnorrKeygen
import com.vultisig.wallet.data.keygen.shouldUseNewKeygenExecution
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ChainImportSetting
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.KeyImportData
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.TempVaultDto
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.DeriveChainKeyUseCase
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.components.errors.ErrorUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.or
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss

internal sealed interface KeygenState {
    data object CreatingInstance : KeygenState

    data object KeygenECDSA : KeygenState

    data object KeygenEdDSA : KeygenState

    data object KeygenMLDSA : KeygenState

    data object KeygenChains : KeygenState

    data object ReshareECDSA : KeygenState

    data object ReshareEdDSA : KeygenState

    data object Success : KeygenState

    data class Error(val title: UiText?, val message: UiText) : KeygenState
}

internal data class KeygenUiModel(
    val progress: Float = 0f,
    val isSuccess: Boolean = false,
    val steps: List<KeygenStepUiModel> = emptyList(),
    val keygenState: KeygenState? = null,
    val error: ErrorUiModel? = null,
    val action: TssAction = TssAction.KEYGEN,
)

internal data class KeygenStepUiModel(val title: UiText, val isLoading: Boolean)

@HiltViewModel
internal class KeygenViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val temporaryVaultRepository: TemporaryVaultRepository,
    private val vaultRepository: VaultRepository,
    private val keyImportRepository: KeyImportRepository,
    private val extractMasterKeys: ExtractMasterKeysUseCase,
    private val deriveChainKey: DeriveChainKeyUseCase,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagRepository: FeatureFlagRepository,
    private val referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {
    private data class ChainKeygenResult(
        val chainName: String,
        val pubKey: String,
        val keyShare: String,
        val isEddsa: Boolean,
    )

    private val args = savedStateHandle.toRoute<Route.Keygen.Generating>()

    val state = MutableStateFlow(KeygenUiModel(action = args.action))

    private val vault =
        Vault(
            id = args.vaultId ?: Uuid.random().toHexString(),
            name = args.vaultName,
            hexChainCode = args.hexChainCode,
            localPartyID = args.localPartyId,
            signers = args.keygenCommittee,
            resharePrefix = args.oldResharePrefix,
            libType = args.libType,
        )

    private val action: TssAction = args.action
    private val keygenCommittee: List<String> = args.keygenCommittee
    private val oldCommittee: List<String> = args.oldCommittee
    private val serverUrl: String = args.serverUrl
    private val sessionId: String = args.sessionId
    private val encryptionKeyHex: String = args.encryptionKeyHex
    private val oldResharePrefix: String = args.oldResharePrefix
    private val isInitiatingDevice: Boolean = args.isInitiatingDevice
    private val libType = args.libType

    private val localStateAccessor: tss.LocalStateAccessor = LocalStateAccessor(vault)
    private var featureFlags = FeatureFlagJson()

    private val isReshareMode: Boolean = action == TssAction.ReShare

    private companion object {
        // Relay message IDs must match the server batch keygen protocol prefixes.
        // Server uses "p-ecdsa", "p-eddsa", "p-mldsa" in ProcessBatchKeygen.
        const val ROOT_ECDSA_MESSAGE_ID = "p-ecdsa"
        const val ROOT_EDDSA_MESSAGE_ID = "p-eddsa"
        const val ROOT_ECDSA_KEY_IMPORT_MESSAGE_ID = "ecdsa_key_import"
        const val ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID = "eddsa_key_import"
        const val ROOT_MLDSA_EXCHANGE_MESSAGE_ID = "p-mldsa"
        const val ROOT_MLDSA_SETUP_MESSAGE_ID = "p-mldsa-setup"
    }

    init {
        generateKey()
    }

    fun tryAgain() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun shouldUseNewKeygenPath(): Boolean =
        shouldUseNewKeygenExecution(
            action = action,
            libType = libType,
            isParallelKeygenFeatureEnabled = featureFlags.isTssBatchEnabled,
        )

    private fun createDklsKeygen(localUi: String, action: TssAction = this.action): DKLSKeygen =
        DKLSKeygen(
            localPartyId = vault.localPartyID,
            keygenCommittee = keygenCommittee,
            mediatorURL = serverUrl,
            sessionID = sessionId,
            encryptionKeyHex = encryptionKeyHex,
            isInitiateDevice = isInitiatingDevice,
            encryption = encryption,
            sessionApi = sessionApi,
            hexChainCode = vault.hexChainCode,
            localUi = localUi,
            action = action,
            oldCommittee = oldCommittee,
            vault = vault,
        )

    private fun createSchnorrKeygen(
        localUi: String,
        setupMessage: ByteArray = byteArrayOf(),
        action: TssAction = this.action,
    ): SchnorrKeygen =
        SchnorrKeygen(
            localPartyId = vault.localPartyID,
            keygenCommittee = keygenCommittee,
            vault = vault,
            oldCommittee = oldCommittee,
            mediatorURL = serverUrl,
            sessionID = sessionId,
            encryptionKeyHex = encryptionKeyHex,
            action = action,
            encryption = encryption,
            sessionApi = sessionApi,
            setupMessage = setupMessage,
            isInitiatingDevice = isInitiatingDevice,
            hexChainCode = vault.hexChainCode,
            localUi = localUi,
        )

    private suspend fun runKeyImportChainKeygen(
        keyImportData: KeyImportData,
        chainSetting: ChainImportSetting,
    ): ChainKeygenResult {
        val chainName = chainSetting.chain.raw
        val chainKey =
            if (isInitiatingDevice) {
                withContext(Dispatchers.IO) { deriveChainKey(keyImportData.mnemonic, chainSetting) }
            } else null

        val isEddsa = chainSetting.chain.TssKeysignType == TssKeyType.EDDSA
        val localUi = chainKey?.privateKeyHex.orEmpty()

        val chainKeyshare =
            if (isEddsa) {
                val chainSchnorr =
                    createSchnorrKeygen(localUi = localUi, action = TssAction.KeyImport)
                chainSchnorr.schnorrKeygenWithRetry(
                    0,
                    KeygenRouting.from(setupMessageId = chainName),
                )
                requireNotNull(chainSchnorr.keyshare) {
                    "Chain $chainName keygen produced no keyshare"
                }
            } else {
                val chainDkls = createDklsKeygen(localUi = localUi, action = TssAction.KeyImport)
                chainDkls.dklsKeygenWithRetry(0, KeygenRouting.from(setupMessageId = chainName))
                requireNotNull(chainDkls.keyshare) {
                    "Chain $chainName keygen produced no keyshare"
                }
            }

        return ChainKeygenResult(
            chainName = chainName,
            pubKey = chainKeyshare.pubKey,
            keyShare = chainKeyshare.keyshare,
            isEddsa = isEddsa,
        )
    }

    private fun generateKey() {
        viewModelScope.launch {
            updateStep(KeygenState.CreatingInstance)

            args.vaultId?.let { vaultId ->
                val cachedVault = vaultRepository.get(vaultId)
                if (cachedVault != null) {
                    vault.pubKeyECDSA = cachedVault.pubKeyECDSA
                    vault.pubKeyEDDSA = cachedVault.pubKeyEDDSA
                    vault.pubKeyMLDSA = cachedVault.pubKeyMLDSA
                    vault.keyshares = cachedVault.keyshares
                }
            }

            state.update { it.copy(error = null) }

            try {
                featureFlags = featureFlagRepository.getFeatureFlags()

                if (action == TssAction.SingleKeygen) {
                    require(vault.pubKeyECDSA.isNotBlank() && vault.pubKeyEDDSA.isNotBlank()) {
                        "SingleKeygen requires an existing vault with ECDSA and EdDSA keys"
                    }
                    startSingleKeygen()
                } else {
                    when (libType) {
                        SigningLibType.DKLS -> startKeygenDkls()
                        SigningLibType.GG20 -> startKeygenGG20()
                        SigningLibType.KeyImport -> startKeyImportKeygen()
                    }
                }

                updateStep(KeygenState.Success)

                delay(1.seconds)

                saveVault()
            } catch (e: Exception) {
                Timber.d(e, "generateKey error")

                state.update { it.copy(error = resolveKeygenErrorFromException(e)) }

                stopService()
            }
        }
    }

    private suspend fun startKeygenDkls() {
        val useNewKeygenPath = shouldUseNewKeygenPath()
        updateStep(
            when (action) {
                TssAction.ReShare -> KeygenState.ReshareECDSA
                else -> KeygenState.KeygenECDSA
            }
        )

        var localUiEcdsa = ""
        var localUiEddsa = ""

        if (action == TssAction.Migrate) {
            try {
                // Verify both key shares exist before attempting migration
                val ecdsaShare = vault.getKeyshare(vault.pubKeyECDSA)
                val eddsaShare = vault.getKeyshare(vault.pubKeyEDDSA)

                if (ecdsaShare.isNullOrBlank() || eddsaShare.isNullOrBlank()) {
                    throw RuntimeException("Missing key shares required for migration")
                }

                val ecdsaUIResp = Tss.getLocalUIEcdsa(ecdsaShare)
                localUiEcdsa = ecdsaUIResp.padEnd(64, '0')

                val eddsaUIResp = Tss.getLocalUIEddsa(eddsaShare)
                localUiEddsa = eddsaUIResp.padEnd(64, '0')
            } catch (e: Exception) {
                throw IllegalStateException("Can't get local ui for migration", e)
            }
        }

        val dklsKeygen = createDklsKeygen(localUi = localUiEcdsa)
        lateinit var schnorr: SchnorrKeygen

        when (action) {
            TssAction.KEYGEN,
            TssAction.Migrate -> {
                if (useNewKeygenPath) {
                    // Root ECDSA and EdDSA keygen are isolated by relay namespace, so they can
                    // run concurrently and we only wait for both shares before finalizing.
                    schnorr =
                        createSchnorrKeygen(
                            localUi = localUiEddsa,
                            // Empty setup — SchnorrKeygen will download the shared setup from the
                            // relay only when the parallel keygen flag is enabled.
                            setupMessage = byteArrayOf(),
                        )
                    coroutineScope {
                        listOf(
                                async {
                                    dklsKeygen.dklsKeygenWithRetry(
                                        0,
                                        KeygenRouting.from(
                                            exchangeMessageId = ROOT_ECDSA_MESSAGE_ID
                                        ),
                                    )
                                },
                                async {
                                    schnorr.schnorrKeygenWithRetry(
                                        0,
                                        KeygenRouting.from(
                                            exchangeMessageId = ROOT_EDDSA_MESSAGE_ID
                                        ),
                                    )
                                },
                            )
                            .awaitAll()
                    }
                } else {
                    dklsKeygen.dklsKeygenWithRetry(0)
                    updateStep(KeygenState.KeygenEdDSA)
                    schnorr =
                        createSchnorrKeygen(
                            localUi = localUiEddsa,
                            setupMessage = dklsKeygen.setupMessage,
                        )
                    schnorr.schnorrKeygenWithRetry(0)
                }
            }

            TssAction.ReShare -> {
                schnorr = createSchnorrKeygen(localUi = localUiEddsa)
                // Reshare still uses the legacy shared relay namespace. Keep it sequential until
                // both ceremonies are explicitly partitioned the same way as keygen.
                dklsKeygen.reshareWithRetry(0)
                updateStep(KeygenState.ReshareEdDSA)
                schnorr.schnorrReshareWithRetry(0)
            }

            TssAction.KeyImport -> error("KeyImport is handled by startKeyImportKeygen()")
            TssAction.SingleKeygen -> error("SingleKeygen is handled by startSingleKeygen()")
        }

        val keyshareEcdsa =
            requireNotNull(dklsKeygen.keyshare) { "ECDSA keygen produced no keyshare" }
        val keyshareEddsa = requireNotNull(schnorr.keyshare) { "EdDSA keygen produced no keyshare" }

        vault.pubKeyECDSA = keyshareEcdsa.pubKey
        vault.pubKeyEDDSA = keyshareEddsa.pubKey
        vault.hexChainCode = keyshareEcdsa.chaincode

        val newKeyshares =
            mutableListOf(
                KeyShare(pubKey = keyshareEcdsa.pubKey, keyShare = keyshareEcdsa.keyshare),
                KeyShare(pubKey = keyshareEddsa.pubKey, keyShare = keyshareEddsa.keyshare),
            )

        vault.keyshares = newKeyshares

        if (action == TssAction.Migrate) {
            vault.libType = SigningLibType.DKLS
        }

        sessionApi.markLocalPartyComplete(serverUrl, sessionId, listOf(vault.localPartyID))

        waitCompleteParties()
    }

    private suspend fun startSingleKeygen() {
        updateStep(KeygenState.KeygenMLDSA)
        val useNewKeygenPath = shouldUseNewKeygenPath()

        val mldsaKeygen =
            MldsaKeygen(
                localPartyId = vault.localPartyID,
                keygenCommittee = keygenCommittee,
                mediatorURL = serverUrl,
                sessionID = sessionId,
                encryptionKeyHex = encryptionKeyHex,
                isInitiateDevice = isInitiatingDevice,
                encryption = encryption,
                sessionApi = sessionApi,
            )

        if (useNewKeygenPath) {
            mldsaKeygen.mldsaKeygenWithRetry(
                0,
                KeygenRouting.from(
                    setupMessageId = ROOT_MLDSA_SETUP_MESSAGE_ID,
                    exchangeMessageId = ROOT_MLDSA_EXCHANGE_MESSAGE_ID,
                ),
            )
        } else {
            mldsaKeygen.mldsaKeygenWithRetry(0)
        }

        val mldsaKeyshare = mldsaKeygen.keyshare ?: error("Failed to generate MLDSA keyshare")

        vault.pubKeyMLDSA = mldsaKeyshare.pubKey
        vault.keyshares =
            vault.keyshares.filterNot { it.pubKey == mldsaKeyshare.pubKey } +
                KeyShare(pubKey = mldsaKeyshare.pubKey, keyShare = mldsaKeyshare.keyshare)

        sessionApi.markLocalPartyComplete(serverUrl, sessionId, listOf(vault.localPartyID))

        waitCompleteParties()
    }

    private suspend fun startKeyImportKeygen() {
        val useNewKeygenPath = shouldUseNewKeygenPath()
        // Non-initiating devices don't go through ImportSeedphrase/ChainsSetup screens,
        // so populate chain settings from the route args (originally from the QR code).
        if (!isInitiatingDevice) {
            keyImportRepository.clear()
            val chainSettings =
                args.chains.mapNotNull { raw ->
                    Chain.entries.find { it.raw == raw }?.let { ChainImportSetting(chain = it) }
                }
            keyImportRepository.setChainSettings(chainSettings)
        }

        val keyImportData = keyImportRepository.get() ?: error("No key import data found")

        try {
            // Only the initiating device knows the mnemonic and derives keys.
            // Non-initiating devices pass empty localUi; the TSS protocol handles distribution.
            val masterKeys =
                if (isInitiatingDevice) {
                    withContext(Dispatchers.IO) { extractMasterKeys(keyImportData.mnemonic) }
                } else null

            // Phase 1+2: Root ECDSA + EdDSA keygen
            updateStep(KeygenState.KeygenECDSA)

            val dklsKeygen =
                createDklsKeygen(
                    localUi = masterKeys?.ecdsaMasterKeyHex.orEmpty(),
                    action = TssAction.KeyImport,
                )
            val schnorrLocalUi = masterKeys?.eddsaMasterKeyHex.orEmpty()
            val schnorr =
                if (useNewKeygenPath) {
                    createSchnorrKeygen(localUi = schnorrLocalUi, action = TssAction.KeyImport)
                        .also { rootSchnorr ->
                            coroutineScope {
                                listOf(
                                        async {
                                            dklsKeygen.dklsKeygenWithRetry(
                                                0,
                                                KeygenRouting.from(
                                                    setupMessageId =
                                                        ROOT_ECDSA_KEY_IMPORT_MESSAGE_ID
                                                ),
                                            )
                                        },
                                        async {
                                            rootSchnorr.schnorrKeygenWithRetry(
                                                0,
                                                KeygenRouting.from(
                                                    setupMessageId =
                                                        ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID
                                                ),
                                            )
                                        },
                                    )
                                    .awaitAll()
                            }
                        }
                } else {
                    dklsKeygen.dklsKeygenWithRetry(0)
                    updateStep(KeygenState.KeygenEdDSA)
                    createSchnorrKeygen(localUi = schnorrLocalUi, action = TssAction.KeyImport)
                        .also { rootSchnorr -> rootSchnorr.schnorrKeygenWithRetry(0) }
                }

            val keyshareEcdsa =
                requireNotNull(dklsKeygen.keyshare) { "Root ECDSA keygen produced no keyshare" }
            val keyshareEddsa =
                requireNotNull(schnorr.keyshare) { "Root EdDSA keygen produced no keyshare" }

            vault.pubKeyECDSA = keyshareEcdsa.pubKey
            vault.pubKeyEDDSA = keyshareEddsa.pubKey
            // Keep the BIP32 chain code from the mnemonic — don't overwrite with DKLS output,
            // which may differ. The original chain code is needed for future BIP32 derivation.

            val seenPubKeys = mutableSetOf(keyshareEcdsa.pubKey, keyshareEddsa.pubKey)
            val allKeyshares =
                mutableListOf(
                    KeyShare(pubKey = keyshareEcdsa.pubKey, keyShare = keyshareEcdsa.keyshare),
                    KeyShare(pubKey = keyshareEddsa.pubKey, keyShare = keyshareEddsa.keyshare),
                )

            // Phase 3: Per-chain keygen
            updateStep(KeygenState.KeygenChains)
            val chainPublicKeys = mutableListOf<ChainPublicKey>()

            val chainResults =
                if (useNewKeygenPath) {
                    coroutineScope {
                        keyImportData.chainSettings
                            .map { chainSetting ->
                                async { runKeyImportChainKeygen(keyImportData, chainSetting) }
                            }
                            .awaitAll()
                    }
                } else {
                    buildList {
                        for (chainSetting in keyImportData.chainSettings) {
                            add(runKeyImportChainKeygen(keyImportData, chainSetting))
                        }
                    }
                }

            for (result in chainResults) {
                if (seenPubKeys.add(result.pubKey)) {
                    allKeyshares.add(KeyShare(pubKey = result.pubKey, keyShare = result.keyShare))
                }
            }
            val chainPublicKeysFromResults =
                chainResults.map { result ->
                    ChainPublicKey(
                        chain = result.chainName,
                        publicKey = result.pubKey,
                        isEddsa = result.isEddsa,
                    )
                }
            chainPublicKeys.addAll(chainPublicKeysFromResults)

            vault.keyshares = allKeyshares
            vault.chainPublicKeys = chainPublicKeys

            sessionApi.markLocalPartyComplete(serverUrl, sessionId, listOf(vault.localPartyID))

            waitCompleteParties()
        } finally {
            keyImportRepository.clear()
        }
    }

    private suspend fun startKeygenGG20() {
        val tss = createTss()

        keygenWithRetry(tss, 1)
    }

    private suspend fun keygenWithRetry(service: ServiceImpl, attempt: Int = 1) {
        withContext(Dispatchers.IO) {
            val messagePuller =
                TssMessagePuller(
                    service,
                    encryptionKeyHex,
                    serverUrl,
                    vault.localPartyID,
                    sessionId,
                    sessionApi,
                    encryption,
                    featureFlags.isEncryptGcmEnabled,
                )

            try {
                messagePuller.pullMessages(null, viewModelScope)

                when (action) {
                    TssAction.KEYGEN,
                    TssAction.Migrate -> {
                        // generate ECDSA
                        updateStep(KeygenState.KeygenECDSA)
                        val keygenRequest = tss.KeygenRequest()
                        keygenRequest.localPartyID = vault.localPartyID
                        keygenRequest.allParties = keygenCommittee.joinToString(",")
                        keygenRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.keygenECDSA(keygenRequest)
                        vault.pubKeyECDSA = ecdsaResp.pubKey
                        delay(1.seconds) // backoff for 1 second
                        updateStep(KeygenState.KeygenEdDSA)
                        val eddsaResp = service.keygenEdDSA(keygenRequest)
                        vault.pubKeyEDDSA = eddsaResp.pubKey
                    }

                    TssAction.ReShare -> {
                        updateStep(KeygenState.ReshareECDSA)
                        val reshareRequest = tss.ReshareRequest()
                        reshareRequest.localPartyID = vault.localPartyID
                        reshareRequest.pubKey = vault.pubKeyECDSA
                        reshareRequest.oldParties = oldCommittee.joinToString(",")
                        reshareRequest.newParties = keygenCommittee.joinToString(",")
                        reshareRequest.resharePrefix =
                            vault.resharePrefix.ifEmpty { oldResharePrefix }
                        reshareRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.reshareECDSA(reshareRequest)
                        updateStep(KeygenState.ReshareEdDSA)
                        delay(1.seconds) // backoff for 1 second
                        reshareRequest.pubKey = vault.pubKeyEDDSA
                        reshareRequest.newResharePrefix = ecdsaResp.resharePrefix
                        val eddsaResp = service.resharingEdDSA(reshareRequest)
                        vault.pubKeyEDDSA = eddsaResp.pubKey
                        vault.pubKeyECDSA = ecdsaResp.pubKey
                        vault.resharePrefix = ecdsaResp.resharePrefix
                    }

                    TssAction.KeyImport -> error("KeyImport will not support for GG20")
                    TssAction.SingleKeygen ->
                        error("SingleKeygen is handled by startSingleKeygen()")
                }

                // here is the keygen process is done
                sessionApi.markLocalPartyComplete(serverUrl, sessionId, listOf(vault.localPartyID))
                Timber.d("Local party ${vault.localPartyID} marked as complete")

                waitCompleteParties()

                Timber.d("All parties have completed the key generation process")

                messagePuller.stop()
            } catch (e: Exception) {
                messagePuller.stop()

                Timber.e(e, "attempt $attempt keygenWithRetry failed")

                if (attempt < MAX_KEYGEN_ATTEMPTS) {
                    keygenWithRetry(service, attempt + 1)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun waitCompleteParties() {
        var counter = 0
        var isSuccess = false
        while (counter < 60) {
            val serverCompletedParties = sessionApi.getCompletedParties(serverUrl, sessionId)
            if (serverCompletedParties.containsAll(keygenCommittee)) {
                isSuccess = true
                break // this means all parties have completed the key generation process
            }
            delay(1.seconds)
            counter++
        }

        if (!isSuccess) {
            throw Exception(
                "Timeout waiting for all parties to complete the key generation process"
            )
        }
    }

    private suspend fun createTss(): ServiceImpl =
        withContext(Dispatchers.IO) {
            val messenger =
                TssMessenger(
                    serverUrl,
                    sessionId,
                    encryptionKeyHex,
                    sessionApi = sessionApi,
                    coroutineScope = viewModelScope,
                    encryption = encryption,
                    isEncryptionGCM = featureFlags.isEncryptGcmEnabled,
                )

            // this will take a while
            return@withContext Tss.newService(messenger, localStateAccessor, true)
        }

    private suspend fun saveVault() {
        val vaultId = vault.id

        val password = args.password
        if (
            !args.email.isNullOrBlank() &&
                !password.isNullOrBlank() &&
                action != TssAction.SingleKeygen
        ) {
            temporaryVaultRepository.add(
                TempVaultDto(
                    vault = vault,
                    email = args.email,
                    password = password,
                    hint = args.hint,
                )
            )

            if (context.canAuthenticateBiometric()) {
                vaultPasswordRepository.savePassword(vaultId, password)
            }
        } else if (action == TssAction.SingleKeygen && !args.email.isNullOrBlank()) {
            // Fast vault SingleKeygen: defer save until OTP verification
            val email = args.email
            checkNotNull(email)
            temporaryVaultRepository.add(
                TempVaultDto(
                    vault = vault,
                    email = email,
                    password = args.password ?: "",
                    hint = null,
                )
            )
        } else if (action == TssAction.SingleKeygen) {
            // Secure vault SingleKeygen: save directly, merge only the MLDSA
            // changes to avoid wiping coins, signers, chainPublicKeys via full upsert
            val existingVault =
                vaultRepository.get(vaultId)
                    ?: error("No vault with id $vaultId exists for SingleKeygen save")
            existingVault.pubKeyMLDSA = vault.pubKeyMLDSA
            existingVault.keyshares = vault.keyshares
            saveVault(existingVault, true)

            // Auto-enable QBTC chain after MLDSA key generation
            val qbtcToken = Coins.Qbtc.QBTC
            val (address, pubKey) =
                chainAccountAddressRepository.getAddress(qbtcToken, existingVault)
            vaultRepository.addTokenToVault(
                vaultId,
                qbtcToken.copy(address = address, hexPublicKey = pubKey),
            )
        } else {
            val shouldOverrideVault = isReshareMode || action == TssAction.Migrate

            try {
                saveVault(vault, shouldOverrideVault)
            } catch (e: DuplicateVaultException) {
                // Defence in depth: if the join-time hexChainCode check missed (e.g. the QR
                // carried an empty chain code), the duplicate is detected here by pubKeyECDSA.
                // Treat it as already-joined and route to the existing vault instead of leaving
                // the success screen stuck on a spinner.
                val existingVault = vaultRepository.getByEcdsa(vault.pubKeyECDSA) ?: throw e
                Timber.w(
                    "saveVault: vault already exists, opening existing one (id=%s)",
                    existingVault.id,
                )
                stopService()
                navigator.route(
                    route = Route.Home(openVaultId = existingVault.id),
                    opts = NavigationOptions(clearBackStack = true),
                )
                return
            }

            vaultDataStoreRepository.setBackupStatus(vaultId = vaultId, false)
        }

        if (action == TssAction.KEYGEN) {
            withContext(Dispatchers.IO) {
                runCatching { referralCodeSettingsRepository.consumePendingReferral(vaultId) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to persist pending referral for vault $vaultId")
                    }
            }
        }

        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)

        delay(2.seconds)

        stopService()

        val vaultType =
            if (vault.isFastVault() && !args.email.isNullOrEmpty()) VaultType.Fast
            else VaultType.Secure

        navigator.route(
            route =
                when (action) {
                    TssAction.KEYGEN ->
                        if (vault.isFastVault()) {
                            Route.Onboarding.VaultBackup(
                                vaultId = vaultId,
                                pubKeyEcdsa = vault.pubKeyECDSA,
                                email = args.email,
                                vaultType = vaultType,
                                action = action,
                                vaultName = args.vaultName,
                                password = args.password,
                                deviceCount = args.deviceCount,
                            )
                        } else {
                            Route.ReviewVaultDevices(
                                vaultId = vaultId,
                                pubKeyEcdsa = vault.pubKeyECDSA,
                                email = args.email,
                                vaultType = vaultType,
                                action = action,
                                vaultName = args.vaultName,
                                password = args.password,
                                devices = keygenCommittee,
                                localPartyId = vault.localPartyID,
                            )
                        }

                    TssAction.ReShare ->
                        Route.Onboarding.VaultBackup(
                            vaultId = vaultId,
                            pubKeyEcdsa = vault.pubKeyECDSA,
                            email = args.email,
                            vaultType = vaultType,
                            action = action,
                            vaultName = args.vaultName,
                            password = args.password,
                            deviceCount = args.deviceCount,
                        )

                    TssAction.Migrate,
                    TssAction.KeyImport ->
                        if (vault.isFastVault()) {
                            Route.Onboarding.VaultBackup(
                                vaultId = vaultId,
                                pubKeyEcdsa = vault.pubKeyECDSA,
                                email = args.email,
                                vaultType = vaultType,
                                action = action,
                                vaultName = args.vaultName,
                                password = args.password,
                                deviceCount = args.deviceCount,
                            )
                        } else {
                            Route.BackupVault(
                                vaultId = vaultId,
                                vaultType = vaultType,
                                action = args.action,
                                passwordType = BackupPasswordType.UserSelectionPassword,
                            )
                        }

                    TssAction.SingleKeygen ->
                        if (!args.email.isNullOrEmpty()) {
                            val email = args.email
                            checkNotNull(email)
                            Route.FastVaultVerification(
                                vaultId = vaultId,
                                pubKeyEcdsa = vault.pubKeyECDSA,
                                email = email,
                                tssAction = action,
                                vaultName = args.vaultName,
                                password = args.password,
                            )
                        } else {
                            Route.BackupVault(
                                vaultId = vaultId,
                                vaultType = vaultType,
                                action = args.action,
                                passwordType = BackupPasswordType.UserSelectionPassword,
                            )
                        }
                },
            opts =
                NavigationOptions(popUpToRoute = Route.Keygen.Generating::class, inclusive = true),
        )
    }

    private fun stopService() {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")
    }

    private fun resolveKeygenErrorFromException(e: Exception): ErrorUiModel {
        if (e is DuplicateVaultException) {
            return if (action == TssAction.KeyImport) {
                ErrorUiModel(
                    title = UiText.StringResource(R.string.import_seedphrase_already_imported),
                    description =
                        UiText.StringResource(R.string.import_seedphrase_duplicate_description),
                )
            } else {
                ErrorUiModel(
                    title = UiText.StringResource(R.string.generating_key_screen_keygen_failed),
                    description = e.message or R.string.unknown_error,
                )
            }
        }

        val isThresholdError = checkIsThresholdError(e)

        return ErrorUiModel(
            title =
                when {
                    isReshareMode ->
                        UiText.StringResource(R.string.generating_key_screen_reshare_failed)

                    else -> UiText.StringResource(R.string.generating_key_screen_keygen_failed)
                },
            description =
                if (isThresholdError) {
                    UiText.StringResource(R.string.threshold_error)
                } else {
                    e.message or R.string.unknown_error
                },
        )
    }

    private fun checkIsThresholdError(exception: Exception) =
        exception.message?.let { message ->
            message.contains("threshold") ||
                message.contains("failed to update from bytes to new local party")
        } ?: false

    private fun usesParallelRootKeyStage(step: KeygenState): Boolean =
        // Parallel DKLS/KeyImport root keygen starts ECDSA and EdDSA together, so the first UI
        // step needs to represent the combined root-key stage rather than ECDSA alone.
        step is KeygenState.KeygenECDSA && shouldUseNewKeygenPath()

    private fun updateStep(step: KeygenState) {
        val usesParallelRootKeyStage = usesParallelRootKeyStage(step)
        state.update { uiModel ->
            uiModel.copy(
                isSuccess = step is KeygenState.Success,
                keygenState = step,
                progress =
                    when (step) {
                        is KeygenState.CreatingInstance -> 0.0f
                        is KeygenState.KeygenECDSA ->
                            if (usesParallelRootKeyStage) 0.50f
                            else if (libType == SigningLibType.KeyImport) 0.25f else 0.33f

                        is KeygenState.KeygenEdDSA -> 0.50f
                        is KeygenState.KeygenMLDSA -> 0.66f
                        is KeygenState.KeygenChains -> 0.83f
                        is KeygenState.ReshareECDSA -> 0.33f
                        is KeygenState.ReshareEdDSA -> 0.66f
                        is KeygenState.Success -> 1f
                        is KeygenState.Error -> 0f
                    },
                steps =
                    uiModel.steps.map { it.copy(isLoading = false) } +
                        when {
                            usesParallelRootKeyStage ->
                                listOf(
                                    KeygenStepUiModel(
                                        UiText.StringResource(
                                            R.string.keygen_step_generating_ecdsa
                                        ),
                                        true,
                                    ),
                                    KeygenStepUiModel(
                                        UiText.StringResource(
                                            R.string.keygen_step_generating_eddsa
                                        ),
                                        true,
                                    ),
                                )

                            else ->
                                listOfNotNull(
                                    when (step) {
                                        is KeygenState.CreatingInstance ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.keygen_step_preparing_vault
                                                ),
                                                true,
                                            )

                                        is KeygenState.KeygenECDSA ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.keygen_step_generating_ecdsa
                                                ),
                                                true,
                                            )

                                        is KeygenState.KeygenEdDSA ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.keygen_step_generating_eddsa
                                                ),
                                                true,
                                            )

                                        is KeygenState.KeygenMLDSA ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.keygen_step_generating_mldsa
                                                ),
                                                true,
                                            )

                                        is KeygenState.KeygenChains ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.keygen_step_generating_chain_keys
                                                ),
                                                true,
                                            )

                                        is KeygenState.ReshareECDSA ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.reshare_step_generating_ecdsa
                                                ),
                                                true,
                                            )

                                        is KeygenState.ReshareEdDSA ->
                                            KeygenStepUiModel(
                                                UiText.StringResource(
                                                    R.string.reshare_step_generating_eddsa
                                                ),
                                                true,
                                            )

                                        else -> null
                                    }
                                )
                        },
            )
        }
    }

    override fun onCleared() {
        stopService()
    }
}

private const val MAX_KEYGEN_ATTEMPTS = 3
