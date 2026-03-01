@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.peer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.graphics.scale
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeyImportRequest
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.MigrateRequest
import com.vultisig.wallet.data.api.models.signer.toJson
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import com.vultisig.wallet.data.common.Endpoints.VULTISIG_RELAY_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.QrHelperModalRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.CreateQrCodeSharingBitmapUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.usecases.tss.ParticipantName
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
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.encodeBase64
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MIN_KEYGEN_DEVICES = 2

data class PeerDiscoveryUiModel(
    val qr: BitmapPainter? = null,
    val network: NetworkOption = NetworkOption.Internet,
    val localPartyId: String = "",
    val devices: List<String> = emptyList(),
    val selectedDevices: List<String> = emptyList(),
    val minimumDevices: Int = MIN_KEYGEN_DEVICES,
    // we're trying to promote minimum of three devices
    val minimumDevicesDisplayed: Int = MIN_KEYGEN_DEVICES + 1,
    val showQrHelpModal: Boolean = false,
    val showDevicesHint: Boolean = true,
    val connectingToServer: ConnectingToServerUiModel? = null,
    val error: ErrorUiModel? = null,
    val warning: ErrorUiModel? = null,
)

data class ConnectingToServerUiModel(
    val isSuccess: Boolean = false,
)

enum class NetworkOption {
    Internet, Local,
}

@HiltViewModel
internal class KeygenPeerDiscoveryViewModel @Inject constructor(
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
    private val qrHelperModalRepository: QrHelperModalRepository,
    private val vaultRepository: VaultRepository,
    private val keyImportRepository: KeyImportRepository,
    private val extractMasterKeys: ExtractMasterKeysUseCase,

    private val protoBuf: ProtoBuf,
    private val sessionApi: SessionApi,
    private val networkUtils: NetworkUtils,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Keygen.PeerDiscovery>()

    val state = MutableStateFlow(PeerDiscoveryUiModel(
        minimumDevices = args.deviceCount ?: MIN_KEYGEN_DEVICES,
        minimumDevicesDisplayed = (args.deviceCount ?: (MIN_KEYGEN_DEVICES + 1)),
    ))

    private val sessionId = Uuid.random().toHexString()
    private val serviceName = generateServiceName()

    private val encryptionKeyHex = Utils.encryptionKeyHex

    // For KeyImport, derive the BIP32 chain code from the mnemonic so the vault can
    // derive addresses for chains not explicitly imported. For other actions, use a random hex.
    private var hexChainCode: String = if (args.action == TssAction.KeyImport) {
        val mnemonic = keyImportRepository.get()?.mnemonic
            ?: error("KeyImport requires a mnemonic in KeyImportRepository")
        extractMasterKeys(mnemonic).hexChainCode
    } else {
        Utils.encryptionKeyHex
    }
    private var localPartyId = Utils.deviceName(context)
    private val vaultName: String = args.vaultName
    private var libType = SigningLibType.GG20
    private var pubKeyEcdsa = ""
    private var signers: List<String> = emptyList()
    private var resharePrefix: String = ""

    // fast vault data
    private val email = args.email
    private val password = args.password

    private val qrBitmap = MutableStateFlow<Bitmap?>(null)

    private var discoverParticipantsJob: Job? = null

    private var serverUrl: String = VULTISIG_RELAY_URL

    init {
        loadData()
    }

    private fun showNetworkWarning() {
        state.update {
            it.copy(
                warning = ErrorUiModel(
                    title = R.string.key_gen_discovery_no_connection_available.asUiText(),
                    description = R.string.key_gen_discovery_enable_wifi.asUiText()
                )
            )
        }
    }

    private fun isConnected() = networkUtils.isNetworkAvailable()

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun shareQr(activity: Context) {
        val qr = qrBitmap.value ?: return

        val scaleModifier = 4

        val scaledQr = qr.scale(
            width = qr.width * scaleModifier,
            height = qr.height * scaleModifier,
            filter = false
        )

        val shareBitmap = createQrCodeSharingBitmap(
            scaledQr,
            R.string.qr_title_join_keygen,
            R.string.qr_title_join_keygen_description,
        )

        activity.share(
            shareBitmap,
            shareFileName(vaultName, vaultName.sha256(), ShareType.KEYGEN)
        )
    }

    fun closeDevicesHint() {
        state.update {
            it.copy(
                showDevicesHint = false,
            )
        }
    }

    fun switchMode() {
        state.update {
            it.copy(
                network = when (it.network) {
                    NetworkOption.Internet -> NetworkOption.Local
                    NetworkOption.Local -> NetworkOption.Internet
                },
                devices = emptyList(),
                selectedDevices = emptyList(),
            )
        }
        viewModelScope.launch {
            startPeerDiscovery()
        }
    }

    fun selectDevice(device: ParticipantName) {
        state.update {
            it.copy(
                selectedDevices = if (device in it.selectedDevices)
                    it.selectedDevices - device
                else it.selectedDevices + device
            )
        }
    }

    fun tryAgain() {
        loadData()
    }

    fun next() {
        discoverParticipantsJob?.cancel()
        viewModelScope.launch {
            val existingVault = args.vaultId?.let {
                vaultRepository.get(it)
            }

            navigator.route(
                Route.Keygen.Generating(
                    action = args.action,
                    sessionId = sessionId,
                    serverUrl = serverUrl,
                    localPartyId = localPartyId,
                    vaultName = vaultName,
                    hexChainCode = hexChainCode,
                    keygenCommittee = listOf(localPartyId) + state.value.selectedDevices,
                    encryptionKeyHex = encryptionKeyHex,
                    isInitiatingDevice = true,
                    libType = when (args.action) {
                        TssAction.Migrate -> SigningLibType.DKLS
                        TssAction.KeyImport -> SigningLibType.KeyImport
                        else -> libType
                    },

                    email = email,
                    password = password,
                    hint = args.hint,

                    vaultId = args.vaultId,
                    oldCommittee = existingVault?.signers
                        ?.filter { state.value.selectedDevices.contains(it) || it == localPartyId }
                        ?: emptyList(),
                    oldResharePrefix = existingVault?.resharePrefix ?: "",
                    deviceCount = args.deviceCount,
                ),
                opts = NavigationOptions(
                    popUpToRoute = Route.Keygen.PeerDiscovery::class,
                    inclusive = false,
                )
            )
        }
    }

    private fun loadData() {
        if (isConnected().not()) {
            showNetworkWarning()
            return
        }
        viewModelScope.launch {
            setupLibType()

            val existingVault = args.vaultId?.let {
                vaultRepository.get(it)
            }

            if (existingVault != null) {
                libType = existingVault.libType
                hexChainCode = existingVault.hexChainCode
                localPartyId = existingVault.localPartyID
                pubKeyEcdsa = existingVault.pubKeyECDSA
                resharePrefix = existingVault.resharePrefix
                signers = existingVault.signers

                if (args.action == TssAction.Migrate) {
                    state.update {
                        it.copy(
                            minimumDevices = existingVault.signers.size,
                            minimumDevicesDisplayed = existingVault.signers.size + 1,
                        )
                    }
                }
            }

            state.update {
                it.copy(
                    error = null,
                    warning = null
                )
            }

            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                // For active vault , we should present PeerDiscovery screen, so the other device can join
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

    private suspend fun startPeerDiscovery() {
        checkQrHelperModalIsVisited()

        val isRelayEnabled = state.value.network == NetworkOption.Internet

        val keygenPayload = createKeygenPayload(
            isRelayEnabled = isRelayEnabled
        )

        loadQr(keygenPayload)

        state.update {
            it.copy(
                localPartyId = localPartyId,
            )
        }

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
        state.update { it.copy(connectingToServer = ConnectingToServerUiModel(false)) }

        try {
            startSessionWithRetry()

            requestVultiServerConnection()

            startParticipantDiscovery(
                onDiscovered = { devices ->
                    if (devices.size == 1) {
                        state.update {
                            it.copy(
                                connectingToServer = it.connectingToServer?.copy(
                                    isSuccess = true
                                )
                            )
                        }

                        delay(2.seconds)

                        next()
                    }
                }
            )
        } catch (e: Exception) {
            // TODO handle exceptions more precisely
            state.update {
                it.copy(
                    error = ErrorUiModel(
                        title = UiText.StringResource(R.string.error_view_default_title),
                        description = UiText.StringResource(R.string.error_view_default_description),
                    )
                )
            }
        }
    }

    private fun startParticipantDiscovery(
        onDiscovered: (suspend (devices: List<ParticipantName>) -> Unit)? = null,
    ) {
        discoverParticipantsJob?.cancel()
        discoverParticipantsJob = viewModelScope.launch {
            discoverParticipants(serverUrl, sessionId, localPartyId)
                .collect { devices ->
                    val currentState = state.value
                    val existingDevices = currentState.devices.toSet()
                    val newDevices = devices - existingDevices

                    val selectedDevices =
                        currentState.selectedDevices.toSet() + newDevices

                    state.update {
                        it.copy(
                            devices = devices,
                            selectedDevices = selectedDevices.toList()
                        )
                    }

                    onDiscovered?.invoke(devices)
                }
        }
    }

    private suspend fun checkQrHelperModalIsVisited() {
        val showQrHelpModal = !qrHelperModalRepository.isVisited()
        state.update {
            it.copy(
                showQrHelpModal = showQrHelpModal
            )
        }
    }


    private suspend fun loadQr(data: String) {
        val qrBitmap = withContext(Dispatchers.IO) {
            generateQrBitmap(data, colors.neutrals.n50, Color.Transparent, null)
        }
        this@KeygenPeerDiscoveryViewModel.qrBitmap.value = qrBitmap
        val bitmapPainter = BitmapPainter(
            qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None
        )
        state.update { it.copy(qr = bitmapPainter) }
    }

    private suspend fun setupLibType() {
        libType = if (secretSettingsRepository.isDklsEnabled.first()) {
            SigningLibType.DKLS
        } else {
            SigningLibType.GG20
        }
    }

    private fun createKeygenPayload(
        isRelayEnabled: Boolean,
    ) = when (args.action) {
        TssAction.KEYGEN ->
            "https://vultisig.com?type=NewVault&tssType=Keygen&jsonData=" +
                    compressQr(
                        protoBuf.encodeToByteArray(
                            KeygenMessageProto(
                                sessionId = sessionId,
                                hexChainCode = hexChainCode,
                                serviceName = serviceName,
                                encryptionKeyHex = encryptionKeyHex,
                                useVultisigRelay = isRelayEnabled,
                                vaultName = vaultName,
                                libType = libType.toProto(),
                            )
                        )
                    ).encodeBase64()
        TssAction.KeyImport ->
            "https://vultisig.com?type=NewVault&tssType=KeyImport&jsonData=" +
                    compressQr(
                        protoBuf.encodeToByteArray(
                            KeygenMessageProto(
                                sessionId = sessionId,
                                hexChainCode = hexChainCode,
                                serviceName = serviceName,
                                encryptionKeyHex = encryptionKeyHex,
                                useVultisigRelay = isRelayEnabled,
                                vaultName = vaultName,
                                libType = SigningLibType.KeyImport.toProto(),
                                chains = keyImportRepository.get()?.chainSettings?.map { it.chain.raw }
                                    ?: emptyList(),
                            )
                        )
                    ).encodeBase64()
        TssAction.ReShare, TssAction.Migrate ->
            "https://vultisig.com?type=NewVault&tssType=${args.action.toLinkTssType()}&jsonData=" +
                    compressQr(
                        protoBuf.encodeToByteArray(
                            ReshareMessageProto(
                                sessionId = sessionId,
                                hexChainCode = hexChainCode,
                                serviceName = serviceName,
                                publicKeyEcdsa = pubKeyEcdsa,
                                oldParties = signers,
                                encryptionKeyHex = encryptionKeyHex,
                                useVultisigRelay = isRelayEnabled,
                                oldResharePrefix = resharePrefix,
                                vaultName = args.vaultName,
                                libType = libType.toProto(),
                            )
                        )
                    ).encodeBase64()
    }

    private fun TssAction.toLinkTssType(): String =
        when (this) {
            TssAction.KEYGEN -> "Keygen"
            TssAction.ReShare -> "Reshare"
            TssAction.Migrate -> "Migrate"
            TssAction.KeyImport -> "KeyImport"
        }

    private suspend fun requestVultiServerConnection() {
        if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
            when (args.action) {
                TssAction.ReShare -> {
                    vultiSignerRepository.joinReshare(
                        JoinReshareRequestJson(
                            vaultName = vaultName,
                            publicKeyEcdsa = pubKeyEcdsa,
                            sessionId = sessionId,
                            hexEncryptionKey = encryptionKeyHex,
                            hexChainCode = hexChainCode,
                            localPartyId = localPartyId,
                            encryptionPassword = password,
                            email = email,
                            oldParties = signers,
                            oldResharePrefix = resharePrefix,
                        )
                    )
                }

                TssAction.KEYGEN -> {
                    vultiSignerRepository.joinKeygen(
                        JoinKeygenRequestJson(
                            vaultName = vaultName,
                            sessionId = sessionId,
                            hexEncryptionKey = encryptionKeyHex,
                            hexChainCode = hexChainCode,
                            localPartyId = generateServerPartyId(),
                            encryptionPassword = password,
                            email = email,
                            libType = libType.toJson()
                        )
                    )
                }

                TssAction.Migrate -> {
                    vultiSignerRepository.migrate(
                        MigrateRequest(
                            publicKeyEcdsa = pubKeyEcdsa,
                            sessionId = sessionId,
                            hexEncryptionKey = encryptionKeyHex,
                            encryptionPassword = password,
                            email = email
                        )
                    )
                }

                TssAction.KeyImport -> {
                    // Server uses joinKeyImport endpoint to determine the flow
                    vultiSignerRepository.joinKeyImport(
                        JoinKeyImportRequest(
                            vaultName = vaultName,
                            sessionId = sessionId,
                            hexEncryptionKey = encryptionKeyHex,
                            hexChainCode = hexChainCode,
                            localPartyId = generateServerPartyId(),
                            encryptionPassword = password,
                            email = email,
                            libType = SigningLibType.DKLS.toJson(),
                            chains = keyImportRepository.get()?.chainSettings?.map { it.chain.raw }
                                ?: emptyList()
                        )
                    )
                }
            }
        }
    }

    fun dismissQrHelpModal() {
        viewModelScope.launch {
            qrHelperModalRepository.visited()
            state.update {
                it.copy(
                    showQrHelpModal = false
                )
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun startMediatorService() {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(serviceStartedReceiver, filter)
        }

        MediatorService.start(context, serviceName)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.d("onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    delay(1000) // back off a second
                    startSessionWithRetry()
                }

                startParticipantDiscovery()
            }
        }
    }

    private suspend fun startSessionWithRetry() {
        repeat(3) { attempt ->
            try {
                delay(1000)
                if (isSessionStarted().not())
                    sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))
                return
            } catch (e: Exception) {
                Timber.tag("startSessionAndDiscovery").e(
                    e,
                    "Attempt ${attempt + 1} failed"
                )
                if (attempt >= 2) {
                    Timber.tag("startSessionAndDiscovery").e("All attempts to start session failed")
                    state.update {
                        it.copy(
                            error = ErrorUiModel(
                                title = UiText.StringResource(R.string.error_view_default_title),
                                description = UiText.StringResource(R.string.error_view_default_description),
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun isSessionStarted(): Boolean {
        return try {
            sessionApi.getParticipants(
                serverUrl,
                sessionId
            ).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }


}