@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.peer

import android.content.Context
import android.graphics.Bitmap
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
import com.vultisig.wallet.data.common.Endpoints.VULTISIG_RELAY_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import com.vultisig.wallet.data.repositories.QrHelperModalRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.CreateQrCodeSharingBitmapUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.NeutralsColors
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MIN_KEYGEN_DEVICES = 2

data class PeerDiscoveryUiModel(
    val qr: BitmapPainter? = null,
    val localPartyId: String = "",
    val devices: List<String> = emptyList(),
    val selectedDevices: List<String> = emptyList(),
    val minimumDevices: Int = MIN_KEYGEN_DEVICES,
    // we're trying to promote minimum of three devices
    val minimumDevicesDisplayed: Int = MIN_KEYGEN_DEVICES + 1,
    val isQrHelpModalVisited: Boolean = true,
)

// TODO switch network option
enum class NetworkOption {
    Internet, Local,
}

@HiltViewModel
internal class PeerDiscoveryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,

    private val generateQrBitmap: GenerateQrBitmap,
    private val compressQr: CompressQrUseCase,
    private val createQrCodeSharingBitmap: CreateQrCodeSharingBitmapUseCase,
    generateServiceName: GenerateServiceName,
    private val discoverParticipants: DiscoverParticipantsUseCase,

    private val secretSettingsRepository: SecretSettingsRepository,

    private val protoBuf: ProtoBuf,
    private val sessionApi: SessionApi,
    private val qrHelperModalRepository: QrHelperModalRepository,

    ) : ViewModel() {

    val state = MutableStateFlow(PeerDiscoveryUiModel())

    private val params = savedStateHandle.toRoute<Route.Keygen.PeerDiscovery>()

    private val network = MutableStateFlow(NetworkOption.Internet)

    private val sessionId = Uuid.random().toHexString()
    private val serviceName = generateServiceName()

    private val hexChainCode: String = Utils.encryptionKeyHex
    private val encryptionKeyHex = Utils.encryptionKeyHex
    private val localPartyId = Utils.deviceName(context)

    private val vaultName: String = params.vaultName
    private var libType = SigningLibType.GG20

    private val qrBitmap = MutableStateFlow<Bitmap?>(null)

    private var discoverParticipantsJob: Job? = null

    private var serverUrl: String = VULTISIG_RELAY_URL

    init {
        loadData()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun openHelp() {
        // TODO open help bottom sheet
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

    fun next() {
        viewModelScope.launch {
            navigator.route(
                Route.Keygen.Generating(
                    // TODO change to reshare when reshare is added
                    action = TssAction.KEYGEN,
                    sessionId = sessionId,
                    serverUrl = serverUrl,
                    localPartyId = localPartyId,
                    name = vaultName,
                    hexChainCode = hexChainCode,
                    keygenCommittee = state.value.selectedDevices,
                    encryptionKeyHex = encryptionKeyHex,
                    isInitiatingDevice = true,
                    libType = libType,

                    // TODO vault.signers.filter { uiState.value.selection.contains(it) }
                    //  maybe we can do it in keygen view model
                    oldCommittee = emptyList(),
                    oldResharePrefix = "",//oldResharePrefix,
                )
            )
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            checkQrHelperModalIsVisited()

            setupLibType()

            val isRelayEnabled = network.value == NetworkOption.Internet

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
                val serverUrl = VULTISIG_RELAY_URL

                try {
                    sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))

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
                            }
                    }
                } catch (e: Exception) {
                    Timber.e("Failed to start session", e)
                    // TODO display error, retry
                }
            } else {
                // TODO local relay
                // when relay is disabled, start the mediator service
                // startMediatorService()
            }
        }
    }

    private suspend fun checkQrHelperModalIsVisited() {
        val isQrHelpModalVisited = qrHelperModalRepository.isVisited()
        state.update {
            it.copy(
                isQrHelpModalVisited = isQrHelpModalVisited
            )
        }
    }


    private suspend fun loadQr(data: String) {
        val qrBitmap = withContext(Dispatchers.IO) {
            generateQrBitmap(data, NeutralsColors.Default.n50, Color.Transparent, null)
        }
        this@PeerDiscoveryViewModel.qrBitmap.value = qrBitmap
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
    ) = "https://vultisig.com?type=NewVault&tssType=Keygen&jsonData=" +
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

    fun dismissQrHelpModal() {
        viewModelScope.launch {
            qrHelperModalRepository.visited()
            state.update {
                it.copy(
                    isQrHelpModalVisited = true
                )
            }
        }
    }

}