@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.peer

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import com.vultisig.wallet.data.common.Endpoints.VULTISIG_RELAY_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.keygen.isBatchEligibleReshare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.QrHelperModalRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.CreateQrCodeSharingBitmapUseCase
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.QrShareField
import com.vultisig.wallet.data.usecases.QrShareInfo
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.usecases.tss.ParticipantName
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.errors.ErrorUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.v2.V2.colors
import com.vultisig.wallet.ui.utils.NetworkUtils
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.forCanvasMinify
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

private const val MIN_KEYGEN_DEVICES = 2

// The device-count picker tops out at "4+" (see ChooseDeviceCountScreen), encoded as a count of 4.
// Vaults at or above this let the initiator add more than the threshold, so the peer-discovery
// label shows the "+" (e.g. "Devices (3/4+)").
private const val UNBOUNDED_KEYGEN_DEVICE_COUNT = 4

data class PeerDiscoveryUiModel(
    val qr: BitmapPainter? = null,
    val network: NetworkOption = NetworkOption.Internet,
    val localPartyId: String = "",
    val devices: List<String> = emptyList(),
    val selectedDevices: List<String> = emptyList(),
    val minimumDevices: Int = MIN_KEYGEN_DEVICES,
    val minimumDevicesDisplayed: Int = MIN_KEYGEN_DEVICES,
    val deviceCount: Int? = null,
    val allowsMoreDevices: Boolean = false,
    val showQrHelpModal: Boolean = false,
    val connectingToServer: ConnectingToServerUiModel? = null,
    val error: ErrorUiModel? = null,
    val warning: ErrorUiModel? = null,
    val enableNotification: Boolean,
    val resendCooldownSeconds: Int = 0,
)

data class ConnectingToServerUiModel(val isSuccess: Boolean = false)

enum class NetworkOption {
    Internet,
    Local,
}

@HiltViewModel
internal class KeygenPeerDiscoveryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val generateQrBitmap: GenerateQrBitmap,
    private val compressQr: CompressQrUseCase,
    private val createQrCodeSharingBitmap: CreateQrCodeSharingBitmapUseCase,
    generateServiceName: GenerateServiceName,
    private val discoverParticipants: DiscoverParticipantsUseCase,
    private val generateServerPartyId: GenerateServerPartyId,
    private val secretSettingsRepository: SecretSettingsRepository,
    private val vultiSignerRepository: VultiSignerRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val qrHelperModalRepository: QrHelperModalRepository,
    private val vaultRepository: VaultRepository,
    private val keyImportRepository: KeyImportRepository,
    private val extractMasterKeys: ExtractMasterKeysUseCase,
    private val protoBuf: ProtoBuf,
    private val sessionApi: SessionApi,
    private val networkUtils: NetworkUtils,
    private val mediatorServiceController: MediatorServiceController,
) : ViewModel() {

    private val args: Route.Keygen.PeerDiscovery? =
        runCatching { savedStateHandle.toRoute<Route.Keygen.PeerDiscovery>() }
            .onFailure { Timber.e(it, "Failed to deserialize PeerDiscovery args") }
            .getOrNull()

    private val _state =
        MutableStateFlow(
            PeerDiscoveryUiModel(
                minimumDevices = args?.deviceCount ?: MIN_KEYGEN_DEVICES,
                minimumDevicesDisplayed = args?.deviceCount ?: MIN_KEYGEN_DEVICES,
                deviceCount = args?.deviceCount,
                allowsMoreDevices =
                    (args?.deviceCount ?: MIN_KEYGEN_DEVICES) >= UNBOUNDED_KEYGEN_DEVICE_COUNT,
                enableNotification = false,
            )
        )
    val state: StateFlow<PeerDiscoveryUiModel> = _state.asStateFlow()

    private val sessionId = Uuid.random().toHexString()
    private val serviceName = generateServiceName()

    private val encryptionKeyHex = Utils.encryptionKeyHex

    private val vaultName: String = args?.vaultName ?: ""

    /**
     * Immutable per-session config. Seeded with the same construction-time defaults the old `var`
     * soup used, then replaced atomically by [loadData] once chain code, lib type, signers and the
     * `tss-batch` flag are resolved. Holding it as one object (instead of eight separately-mutated
     * vars) means the QR payload, the FastVault dispatch and the navigation arg always observe a
     * consistent snapshot rather than a half-updated mix.
     */
    private var session: KeygenSession =
        KeygenSession(
            // For KeyImport the BIP32 chain code is derived from the mnemonic in loadData(); use an
            // empty placeholder until then. Other actions use a random hex immediately.
            hexChainCode = if (args?.action == TssAction.KeyImport) "" else Utils.encryptionKeyHex,
            localPartyId = Utils.deviceName(context),
            libType = SigningLibType.GG20,
            pubKeyEcdsa = "",
            signers = emptyList(),
            resharePrefix = "",
            isTssBatchEnabled = false,
        )

    // fast vault data
    private val email = args?.email
    private val password = args?.password

    private val qrBitmap = MutableStateFlow<Bitmap?>(null)

    private var discoverParticipantsJob: Job? = null

    private var serverUrl: String = VULTISIG_RELAY_URL

    init {
        if (args == null) {
            viewModelScope.launch { navigator.navigate(Destination.Back) }
        } else {
            loadData()
            observeAutoStartKeygen()
        }
    }

    /**
     * Auto-kicks off keygen for 2/2 and 3/3 secure-vault flows so the initiator does not have to
     * tap Next once the deterministic peer threshold has been met. Mirrors the Windows
     * `AutoStartKeygen` component. Skipped for Fast Vault, which already auto-fires from
     * [startVultiServerConnection] when the server peer is discovered — double-firing would launch
     * two concurrent session-start attempts.
     */
    private fun observeAutoStartKeygen() {
        val args = args ?: return
        val targetDeviceCount = args.deviceCount ?: return
        if (targetDeviceCount !in 2..3) return
        if (!email.isNullOrBlank() && !password.isNullOrBlank()) return

        viewModelScope.launch {
            _state.map { it.selectedDevices.size }.first { it >= targetDeviceCount - 1 }
            next()
        }
    }

    private fun showNetworkWarning() {
        _state.update {
            it.copy(
                warning =
                    ErrorUiModel(
                        title = R.string.key_gen_discovery_no_connection_available.asUiText(),
                        description = R.string.key_gen_discovery_enable_wifi.asUiText(),
                    )
            )
        }
    }

    private fun isConnected() = networkUtils.isNetworkAvailable()

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun shareQr(activity: Context) {
        val qr = qrBitmap.value ?: return

        // Mirror loadData()'s fast/secure predicate: active-vault migrate (signers > 2) goes
        // through peer discovery even when email+password are present, so the share card must
        // not advertise "Fast Vault" for it.
        val signerCount = session.signers.size
        val isFastVault =
            !email.isNullOrBlank() &&
                !password.isNullOrBlank() &&
                !(args?.action == TssAction.Migrate && signerCount > 2)
        val typeRes =
            if (isFastVault) R.string.qr_share_type_fast_vault
            else R.string.qr_share_type_secure_vault
        val info =
            QrShareInfo(
                title = context.getString(R.string.qr_title_join_keygen),
                fields =
                    listOf(
                        QrShareField(
                            context.getString(R.string.qr_share_label_vault),
                            vaultName.forCanvasMinify(),
                        ),
                        QrShareField(
                            context.getString(R.string.qr_share_label_type),
                            context.getString(typeRes),
                        ),
                    ),
            )

        val shareBitmap = createQrCodeSharingBitmap(qr, info)

        activity.share(shareBitmap, shareFileName(vaultName, vaultName.sha256(), ShareType.KEYGEN))
    }

    fun switchMode() {
        if (args == null) return
        // Tear down the current transport before switching, otherwise the old discovery job or
        // local mediator receiver can keep emitting and repopulate the just-cleared device list
        // (or leave a stale local session/receiver behind).
        discoverParticipantsJob?.cancel()
        mediatorServiceController.stop()
        _state.update {
            it.copy(
                network =
                    when (it.network) {
                        NetworkOption.Internet -> NetworkOption.Local
                        NetworkOption.Local -> NetworkOption.Internet
                    },
                devices = emptyList(),
                selectedDevices = emptyList(),
            )
        }
        viewModelScope.safeLaunch { startPeerDiscovery() }
    }

    fun selectDevice(device: ParticipantName) {
        _state.update {
            val isReshare = args?.action == TssAction.ReShare
            val maxOtherDevices = it.minimumDevices - 1
            it.copy(
                selectedDevices =
                    if (device in it.selectedDevices) it.selectedDevices - device
                    else if (!isReshare && it.selectedDevices.size >= maxOtherDevices)
                        it.selectedDevices
                    else it.selectedDevices + device
            )
        }
    }

    fun tryAgain() {
        if (args == null) return
        loadData()
    }

    fun next() {
        val args = args ?: return
        discoverParticipantsJob?.cancel()
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to start keygen session")
                _state.update {
                    it.copy(
                        warning =
                            ErrorUiModel(
                                title = UiText.StringResource(R.string.error_view_default_title),
                                description =
                                    UiText.StringResource(R.string.error_view_default_description),
                            )
                    )
                }
            }
        ) {
            val session = session
            val existingVault = args.vaultId?.let { vaultRepository.get(it) }
            // Snapshot the selection once: startWithCommittee suspends, and oldCommittee below
            // reads it again — without the snapshot a selection change mid-call would desync the
            // started committee from the navigation args.
            val selectedDevices = _state.value.selectedDevices
            val keygenCommittee = (listOf(session.localPartyId) + selectedDevices).distinct()
            sessionApi.startWithCommittee(serverUrl, sessionId, keygenCommittee)

            navigator.route(
                Route.Keygen.Generating(
                    action = args.action,
                    sessionId = sessionId,
                    serverUrl = serverUrl,
                    localPartyId = session.localPartyId,
                    vaultName = vaultName,
                    hexChainCode = session.hexChainCode,
                    keygenCommittee = keygenCommittee,
                    encryptionKeyHex = encryptionKeyHex,
                    isInitiatingDevice = true,
                    libType = args.action.strategy().resolveLibType(session.libType),
                    email = email,
                    password = password,
                    hint = args.hint,
                    vaultId = args.vaultId,
                    oldCommittee =
                        existingVault?.signers?.filter {
                            selectedDevices.contains(it) || it == session.localPartyId
                        } ?: emptyList(),
                    oldResharePrefix = existingVault?.resharePrefix ?: "",
                    deviceCount = args.deviceCount,
                    // Mirrors the QR opt-in: when the initiator chose batched reshare on
                    // FastVault, every peer (including this one) must follow the same path.
                    // Both DKLS and KeyImport vaults qualify because they share the same root
                    // ECDSA / EdDSA threshold protocols — matches iOS / Windows.
                    isTssBatch =
                        isBatchEligibleReshare(args.action, session.libType) &&
                            session.isTssBatchEnabled,
                ),
                opts =
                    NavigationOptions(
                        popUpToRoute = Route.Keygen.PeerDiscovery::class,
                        inclusive = false,
                    ),
            )
        }
    }

    private fun loadData() {
        if (isConnected().not()) {
            showNetworkWarning()
            return
        }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load peer discovery data")
                _state.update {
                    it.copy(
                        error =
                            ErrorUiModel(
                                title = UiText.StringResource(R.string.error_view_default_title),
                                description =
                                    UiText.StringResource(R.string.error_view_default_description),
                            )
                    )
                }
            }
        ) {
            val args = args ?: return@safeLaunch

            var hexChainCode =
                if (args.action == TssAction.KeyImport) "" else Utils.encryptionKeyHex
            if (args.action == TssAction.KeyImport && hexChainCode.isEmpty()) {
                val mnemonic = keyImportRepository.get()?.mnemonic
                if (mnemonic == null) {
                    Timber.w("KeyImport: no mnemonic found in repository")
                    _state.update {
                        it.copy(
                            error =
                                ErrorUiModel(
                                    title = UiText.StringResource(R.string.key_import_error_title),
                                    description =
                                        UiText.StringResource(
                                            R.string.key_import_error_no_mnemonic_description
                                        ),
                                )
                        )
                    }
                    return@safeLaunch
                }
                val masterKeys =
                    try {
                        extractMasterKeys(mnemonic)
                    } catch (e: Exception) {
                        Timber.e(e, "KeyImport: failed to extract master keys")
                        _state.update {
                            it.copy(
                                error =
                                    ErrorUiModel(
                                        title =
                                            UiText.StringResource(R.string.key_import_error_title),
                                        description =
                                            UiText.StringResource(
                                                R.string.key_import_error_description
                                            ),
                                    )
                            )
                        }
                        return@safeLaunch
                    }
                if (masterKeys == null) {
                    Timber.w("KeyImport: extractMasterKeys returned null")
                    _state.update {
                        it.copy(
                            error =
                                ErrorUiModel(
                                    title = UiText.StringResource(R.string.key_import_error_title),
                                    description =
                                        UiText.StringResource(R.string.key_import_error_description),
                                )
                        )
                    }
                    return@safeLaunch
                }
                hexChainCode = masterKeys.hexChainCode
            }

            var libType =
                if (secretSettingsRepository.isDklsEnabled.first()) SigningLibType.DKLS
                else SigningLibType.GG20
            val isTssBatchEnabled = featureFlagRepository.getFeatureFlags().isTssBatchEnabled

            var localPartyId = Utils.deviceName(context)
            var pubKeyEcdsa = ""
            var signers: List<String> = emptyList()
            var resharePrefix = ""

            val existingVault = args.vaultId?.let { vaultRepository.get(it) }
            if (existingVault != null) {
                libType = existingVault.libType
                hexChainCode = existingVault.hexChainCode
                localPartyId = existingVault.localPartyID
                pubKeyEcdsa = existingVault.pubKeyECDSA
                resharePrefix = existingVault.resharePrefix
                signers = existingVault.signers

                if (args.action == TssAction.Migrate || args.action == TssAction.SingleKeygen) {
                    _state.update {
                        it.copy(
                            minimumDevices = existingVault.signers.size,
                            minimumDevicesDisplayed = existingVault.signers.size,
                        )
                    }
                }
            }

            session =
                KeygenSession(
                    hexChainCode = hexChainCode,
                    localPartyId = localPartyId,
                    libType = libType,
                    pubKeyEcdsa = pubKeyEcdsa,
                    signers = signers,
                    resharePrefix = resharePrefix,
                    isTssBatchEnabled = isTssBatchEnabled,
                )

            _state.update { it.copy(error = null, warning = null) }

            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                // For active vault, we should present PeerDiscovery screen, so the other device can
                // join
                // Also need to request the server to join the upgrade process
                if (args.action == TssAction.Migrate && signers.count() > 2) {
                    startPeerDiscovery()
                    requestVultiServerConnection()
                } else {
                    startVultiServerConnection()
                }
            } else {
                startPeerDiscovery()
            }
        }
    }

    private fun actionContext(session: KeygenSession) =
        KeygenActionContext(
            sessionId = sessionId,
            serviceName = serviceName,
            encryptionKeyHex = encryptionKeyHex,
            vaultName = vaultName,
            session = session,
            compressQr = compressQr,
            protoBuf = protoBuf,
            vultiSignerRepository = vultiSignerRepository,
            generateServerPartyId = generateServerPartyId,
            keyImportRepository = keyImportRepository,
        )

    private suspend fun startPeerDiscovery() {
        val args = args ?: return
        val session = session
        checkQrHelperModalIsVisited()

        val isRelayEnabled = _state.value.network == NetworkOption.Internet

        val keygenPayload =
            args.action.strategy().buildPayload(actionContext(session), isRelayEnabled)

        loadQr(keygenPayload)

        _state.update { it.copy(localPartyId = session.localPartyId) }

        if (isRelayEnabled) {
            serverUrl = VULTISIG_RELAY_URL
            startSessionWithRetry()
            startParticipantDiscovery()
        } else {
            serverUrl = LOCAL_MEDIATOR_SERVER_URL
            startMediatorService()
        }
    }

    private suspend fun startVultiServerConnection() {
        _state.update { it.copy(connectingToServer = ConnectingToServerUiModel(false)) }

        try {
            startSessionWithRetry()

            requestVultiServerConnection()

            startParticipantDiscovery(
                onDiscovered = { devices ->
                    if (devices.size == 1) {
                        _state.update {
                            it.copy(
                                connectingToServer = it.connectingToServer?.copy(isSuccess = true)
                            )
                        }

                        delay(2.seconds)

                        next()
                    }
                }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to connect to Vultiserver")
            _state.update {
                it.copy(
                    error =
                        ErrorUiModel(
                            title = UiText.StringResource(R.string.error_view_default_title),
                            description =
                                UiText.DynamicString(
                                    e.message
                                        ?: context.getString(
                                            R.string.error_view_default_description
                                        )
                                ),
                        )
                )
            }
        }
    }

    private fun startParticipantDiscovery(
        onDiscovered: (suspend (devices: List<ParticipantName>) -> Unit)? = null
    ) {
        val session = session
        discoverParticipantsJob?.cancel()
        discoverParticipantsJob =
            viewModelScope.safeLaunch {
                discoverParticipants(serverUrl, sessionId, session.localPartyId).collect { devices
                    ->
                    applyDiscoveredDevices(devices)
                    onDiscovered?.invoke(devices)
                }
            }
    }

    /**
     * Merges a freshly-discovered device list into state, auto-selecting peers up to the threshold
     * (all of them for ReShare, otherwise filling the remaining slots). Extracted so it can be
     * unit-tested directly without standing up the discovery flow.
     */
    @VisibleForTesting
    internal fun applyDiscoveredDevices(devices: List<ParticipantName>) {
        val currentState = _state.value
        val existingDevices = currentState.devices.toSet()
        val newDevices = devices - existingDevices

        val devicesToAutoSelect =
            if (args?.action == TssAction.ReShare) {
                newDevices
            } else {
                val maxOtherDevices =
                    if (currentState.minimumDevices > 1) currentState.minimumDevices - 1
                    else currentState.minimumDevices
                val remainingSlots = maxOtherDevices - currentState.selectedDevices.size
                newDevices.take(remainingSlots.coerceAtLeast(0))
            }
        val selectedDevices = currentState.selectedDevices.toSet() + devicesToAutoSelect

        _state.update { it.copy(devices = devices, selectedDevices = selectedDevices.toList()) }
    }

    private suspend fun checkQrHelperModalIsVisited() {
        val showQrHelpModal = !qrHelperModalRepository.isVisited()
        _state.update { it.copy(showQrHelpModal = showQrHelpModal) }
    }

    private suspend fun loadQr(data: String) {
        val qrBitmap =
            withContext(Dispatchers.IO) {
                generateQrBitmap(data, colors.neutrals.n50, Color.Transparent, null)
            }
        this@KeygenPeerDiscoveryViewModel.qrBitmap.value = qrBitmap
        val bitmapPainter =
            BitmapPainter(qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None)
        _state.update { it.copy(qr = bitmapPainter) }
    }

    private suspend fun requestVultiServerConnection() {
        val args = args ?: return
        val session = session
        val email = email
        val password = password
        if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
            args.action.strategy().joinServer(actionContext(session), email, password)
        }
    }

    fun dismissQrHelpModal() {
        viewModelScope.launch {
            qrHelperModalRepository.visited()
            _state.update { it.copy(showQrHelpModal = false) }
        }
    }

    private fun startMediatorService() {
        mediatorServiceController.start(serviceName) {
            // send a request to local mediator server to start the session
            viewModelScope.safeLaunch { withContext(Dispatchers.IO) { startSessionWithRetry() } }

            startParticipantDiscovery()
        }
    }

    private suspend fun startSessionWithRetry() {
        val localPartyId = session.localPartyId
        repeat(3) { attempt ->
            try {
                delay(1000)
                if (isSessionStarted().not())
                    sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("startSessionAndDiscovery").e(e, "Attempt ${attempt + 1} failed")
                if (attempt >= 2) {
                    Timber.tag("startSessionAndDiscovery").e("All attempts to start session failed")
                    _state.update {
                        it.copy(
                            error =
                                ErrorUiModel(
                                    title =
                                        UiText.StringResource(R.string.error_view_default_title),
                                    description =
                                        UiText.StringResource(
                                            R.string.error_view_default_description
                                        ),
                                )
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        mediatorServiceController.stop()
        super.onCleared()
    }

    private suspend fun isSessionStarted(): Boolean {
        return try {
            sessionApi.getParticipants(serverUrl, sessionId).isNotEmpty()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to get session participants, assuming session not started")
            false
        }
    }
}
