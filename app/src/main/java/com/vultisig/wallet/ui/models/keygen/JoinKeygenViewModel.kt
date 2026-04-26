@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mappers.KeygenMessageFromProtoMapper
import com.vultisig.wallet.data.mappers.ReshareMessageFromProtoMapper
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.models.proto.v1.SingleKeygenMessageProto
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.DiscoveryTimeout
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.DuplicateVaultName
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.InvalidQr
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.UnknownError
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.UnknownTss
import com.vultisig.wallet.ui.models.keygen.JoinKeygenError.WrongResharePrefix
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.decodeBase64Bytes
import java.net.Inet4Address
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

internal sealed class JoinKeygenError(val message: UiText) {
    data object DuplicateVaultName :
        JoinKeygenError(R.string.join_key_gen_vault_with_duplicate_name_exists.asUiText())

    data object InvalidQr : JoinKeygenError(R.string.join_keysign_invalid_qr.asUiText())

    data object UnknownTss : JoinKeygenError(R.string.join_key_gen_unknown_tssaction.asUiText())

    data object WrongResharePrefix : JoinKeygenError(R.string.join_keysign_wrong_reshare.asUiText())

    data object DiscoveryTimeout :
        JoinKeygenError(R.string.join_key_gen_mediator_discovery_timeout.asUiText())

    data class UnknownError(val error: String) : JoinKeygenError(error.asUiText())
}

internal data class JoinKeygenUiModel(
    val isSuccess: Boolean = false,
    val error: JoinKeygenError? = null,
    val alreadyJoined: AlreadyJoinedVault? = null,
)

internal data class AlreadyJoinedVault(val vaultId: VaultId, val vaultName: String)

private class JoinKeygenException(val error: JoinKeygenError) : Exception()

private val DISCOVERY_TIMEOUT = 30.seconds

@HiltViewModel
internal class JoinKeygenViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val protoBuf: ProtoBuf,
    private val mapKeygenMessageFromProto: KeygenMessageFromProtoMapper,
    private val mapReshareMessageFromProto: ReshareMessageFromProtoMapper,
    private val decompressQr: DecompressQrUseCase,
    private val sessionApi: SessionApi,
) : ViewModel() {

    val state = MutableStateFlow(JoinKeygenUiModel())

    private val args = savedStateHandle.toRoute<Route.Keygen.Join>()

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: MediatorServiceDiscoveryListener? = null

    init {
        viewModelScope.launch {
            try {
                val deepLink = DeepLinkHelper(Base64.UrlSafe.decode(args.qr).decodeToString())

                val bytes =
                    decompressQr((deepLink.getJsonData() ?: error(InvalidQr)).decodeBase64Bytes())

                val existingVaults = vaultRepository.getAll()

                val session =
                    when (val action = deepLink.getTssAction()) {
                        TssAction.KEYGEN,
                        TssAction.KeyImport -> {
                            val message =
                                mapKeygenMessageFromProto(
                                    protoBuf.decodeFromByteArray<KeygenMessageProto>(bytes)
                                )

                            // hexChainCode uniquely identifies the vault across devices.
                            // If a vault with the same chain code is already on this device,
                            // skip the join and surface a clear "already on device" message
                            // instead of running keygen + failing on duplicate save.
                            val alreadyJoinedVault = existingVaults.find {
                                it.hexChainCode == message.hexChainCode &&
                                    it.hexChainCode.isNotBlank()
                            }
                            if (alreadyJoinedVault != null) {
                                state.update {
                                    it.copy(
                                        alreadyJoined =
                                            AlreadyJoinedVault(
                                                vaultId = alreadyJoinedVault.id,
                                                vaultName = alreadyJoinedVault.name,
                                            )
                                    )
                                }
                                return@launch
                            }

                            assertNoVaultNameDuplicates(existingVaults, message.vaultName)

                            val serverUrl =
                                if (message.useVultisigRelay) {
                                    Endpoints.VULTISIG_RELAY_URL
                                } else {
                                    discoverMediator(message.serviceName)
                                }
                            Session(
                                sessionId = message.sessionID,
                                action = action,
                                hexChainCode = message.hexChainCode,
                                serviceName = message.serviceName,
                                useVultisigRelay = message.useVultisigRelay,
                                encryptionKeyHex = message.encryptionKeyHex,
                                vaultName = message.vaultName,
                                libType = message.libType,
                                localPartyId = Utils.deviceName(context),
                                serverUrl = serverUrl,
                                oldCommittee = emptyList(),
                                oldResharePrefix = "",
                                chains = message.chains,
                            )
                        }

                        TssAction.ReShare,
                        TssAction.Migrate -> {
                            val message =
                                mapReshareMessageFromProto(
                                    protoBuf.decodeFromByteArray<ReshareMessageProto>(bytes)
                                )

                            val existingVault = existingVaults.find {
                                it.pubKeyECDSA == message.pubKeyECDSA
                            }

                            if (
                                existingVault != null &&
                                    existingVault.resharePrefix != message.oldResharePrefix
                            ) {
                                error(WrongResharePrefix)
                            }

                            // if we don't reshare vault which we already have,
                            // we should not create duplicate names
                            if (existingVault == null) {
                                assertNoVaultNameDuplicates(existingVaults, message.vaultName)
                            }

                            val serverUrl =
                                if (message.useVultisigRelay) {
                                    Endpoints.VULTISIG_RELAY_URL
                                } else {
                                    discoverMediator(message.serviceName)
                                }

                            Session(
                                sessionId = message.sessionID,
                                action = action,
                                hexChainCode = existingVault?.hexChainCode ?: message.hexChainCode,
                                serviceName = message.serviceName,
                                useVultisigRelay = message.useVultisigRelay,
                                encryptionKeyHex = message.encryptionKeyHex,
                                vaultName = existingVault?.name ?: message.vaultName,
                                libType =
                                    if (action == TssAction.Migrate) SigningLibType.DKLS
                                    else message.libType,
                                localPartyId =
                                    existingVault?.localPartyID ?: Utils.deviceName(context),
                                serverUrl = serverUrl,
                                oldCommittee = message.oldParties,
                                oldResharePrefix = message.oldResharePrefix,
                                vaultId = existingVault?.id,
                            )
                        }

                        TssAction.SingleKeygen -> {
                            val message =
                                protoBuf.decodeFromByteArray<SingleKeygenMessageProto>(bytes)

                            val existingVault =
                                existingVaults.find { it.pubKeyECDSA == message.publicKeyEcdsa }
                                    ?: error(
                                        UnknownError(
                                            "No vault found matching the initiator's ECDSA public key"
                                        )
                                    )

                            require(existingVault.pubKeyMLDSA.isBlank()) {
                                "Vault already has an MLDSA key"
                            }

                            require(existingVault.libType != SigningLibType.KeyImport) {
                                "Key import vaults do not support MLDSA keygen"
                            }

                            val serverUrl =
                                if (message.useVultisigRelay) {
                                    Endpoints.VULTISIG_RELAY_URL
                                } else {
                                    discoverMediator(message.serviceName)
                                }

                            Session(
                                sessionId = message.sessionId,
                                action = TssAction.SingleKeygen,
                                hexChainCode = existingVault.hexChainCode,
                                serviceName = message.serviceName,
                                useVultisigRelay = message.useVultisigRelay,
                                encryptionKeyHex = message.encryptionKeyHex,
                                vaultName = existingVault.name,
                                libType = existingVault.libType,
                                localPartyId = existingVault.localPartyID,
                                serverUrl = serverUrl,
                                oldCommittee = emptyList(),
                                oldResharePrefix = "",
                                vaultId = existingVault.id,
                            )
                        }

                        else -> error(UnknownTss)
                    }

                sessionApi.startSession(
                    session.serverUrl,
                    session.sessionId,
                    listOf(session.localPartyId),
                )

                waitForKeygenToStart(session)
            } catch (e: Exception) {
                Timber.e(e)
                when (e) {
                    is JoinKeygenException -> {
                        state.update { it.copy(error = e.error) }
                    }

                    else -> {
                        state.update {
                            it.copy(
                                error = UnknownError(e.message ?: "An unexpected error occurred")
                            )
                        }
                    }
                }
            }
        }
    }

    private fun error(error: JoinKeygenError): Nothing {
        throw JoinKeygenException(error)
    }

    fun navigateBack() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun openExistingVault() {
        val vaultId = state.value.alreadyJoined?.vaultId ?: return
        viewModelScope.launch {
            navigator.route(
                route = Route.Home(openVaultId = vaultId),
                opts = NavigationOptions(clearBackStack = true),
            )
        }
    }

    private fun assertNoVaultNameDuplicates(existingVaults: List<Vault>, name: String) {
        if (existingVaults.any { it.name == name }) {
            error(DuplicateVaultName)
        }
    }

    private suspend fun discoverMediator(serviceName: String): String =
        withTimeoutOrNull(DISCOVERY_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                val listener =
                    MediatorServiceDiscoveryListener(
                        nsdManager = nsdManager,
                        serviceName = serviceName,
                        onServerAddressDiscovered = { serverUrl ->
                            stopDiscoveryQuietly()
                            if (cont.isActive) cont.resume(serverUrl)
                        },
                    )
                discoveryListener = listener
                cont.invokeOnCancellation { stopDiscoveryQuietly() }
                nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            }
        } ?: error(DiscoveryTimeout)

    private fun stopDiscoveryQuietly() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    private suspend fun waitForKeygenToStart(session: Session) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                val keygenCommittee =
                    try {
                        fetchKeygenCommittee(session)
                    } catch (e: Exception) {
                        Timber.e("Error fetching keygen committee: $e")
                        emptyList()
                    }

                if (session.localPartyId in keygenCommittee) {
                    state.update { it.copy(isSuccess = true) }

                    delay(1.5.seconds)

                    navigator.route(
                        route =
                            Route.Keygen.Generating(
                                action = session.action,
                                sessionId = session.sessionId,
                                serverUrl = session.serverUrl,
                                localPartyId = session.localPartyId,
                                vaultName = session.vaultName,
                                hexChainCode = session.hexChainCode,
                                keygenCommittee = keygenCommittee,
                                encryptionKeyHex = session.encryptionKeyHex,
                                libType = session.libType,
                                isInitiatingDevice = false,
                                vaultId = session.vaultId,
                                oldCommittee = session.oldCommittee,
                                oldResharePrefix = session.oldResharePrefix,
                                email = null,
                                password = null,
                                hint = null,
                                deviceCount = null,
                                chains = session.chains,
                            ),
                        opts =
                            NavigationOptions(
                                popUpToRoute = Route.Keygen.Join::class,
                                inclusive = true,
                            ),
                    )

                    return@withContext
                }

                delay(1.seconds)
            }
        }
    }

    private suspend fun fetchKeygenCommittee(session: Session) =
        sessionApi.checkCommittee(session.serverUrl, session.sessionId)

    data class Session(
        val sessionId: String,
        val action: TssAction,
        val serverUrl: String,
        val hexChainCode: String,
        val serviceName: String,
        val useVultisigRelay: Boolean,
        val encryptionKeyHex: String,
        val oldCommittee: List<String>,
        val oldResharePrefix: String,
        val vaultName: String,
        val libType: SigningLibType,
        val localPartyId: String,
        val vaultId: VaultId? = null,
        val chains: List<String> = emptyList(),
    )
}

class MediatorServiceDiscoveryListener(
    private val nsdManager: NsdManager,
    private val serviceName: String,
    private val onServerAddressDiscovered: (String) -> Unit,
) : NsdManager.DiscoveryListener, NsdManager.ResolveListener {

    override fun onDiscoveryStarted(regType: String) {
        Timber.d("Service discovery started, regType: $regType")
    }

    override fun onServiceFound(service: NsdServiceInfo) {
        Timber.d("Service found: %s", service.serviceName)
        if (service.serviceName == serviceName) {
            Timber.d("Service found: %s", service.serviceName)

            if (
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                nsdManager.registerServiceInfoCallback(
                    service,
                    { it.run() },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            Timber.d(
                                "Failed to resolve service: ${service.serviceName}, error: $errorCode"
                            )
                        }

                        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                            onServiceResolved(serviceInfo)
                            nsdManager.unregisterServiceInfoCallback(this)
                        }

                        override fun onServiceLost() {
                            Timber.d("Service lost during resolution: ${service.serviceName}")
                        }

                        override fun onServiceInfoCallbackUnregistered() {
                            // Cleanup if needed
                        }
                    },
                )
            } else {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(
                    service,
                    MediatorServiceDiscoveryListener(
                        nsdManager,
                        serviceName,
                        onServerAddressDiscovered,
                    ),
                )
            }
        }
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        Timber.d("Service lost: %s, port: %d", service.serviceName, service.port)
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Timber.d("Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Timber.d("Failed to start discovery: $serviceType, error: $errorCode")
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Timber.d("Failed to stop discovery: $serviceType, error: $errorCode")
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.d("Failed to resolve service: ${serviceInfo?.serviceName}, error: $errorCode")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        val logAddress =
            if (
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                serviceInfo?.hostAddresses?.firstOrNull()
            } else {
                @Suppress("DEPRECATION") serviceInfo?.host
            }
        Timber.d(
            "Service resolved: ${serviceInfo?.serviceName}, address: ${logAddress?.hostAddress}, port: ${serviceInfo?.port}"
        )

        serviceInfo?.let { info ->
            val address =
                if (
                    android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    val addresses = info.hostAddresses
                    addresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                } else {
                    @Suppress("DEPRECATION") val address = info.host
                    if (address !is Inet4Address || address.isLoopbackAddress) {
                        null
                    } else {
                        address
                    }
                }
            address?.hostAddress?.let {
                onServerAddressDiscovered("http://${it}:${serviceInfo.port}")
            }
        }
    }
}
