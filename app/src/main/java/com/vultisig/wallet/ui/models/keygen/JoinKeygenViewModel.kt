@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mappers.KeygenMessageFromProtoMapper
import com.vultisig.wallet.data.mappers.ReshareMessageFromProtoMapper
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import java.net.Inet4Address
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

data class JoinKeygenUiModel(
    val isSuccess: Boolean = false,
)

private class DuplicateVaultNameException : Exception("Vault with duplicate name exists")

@HiltViewModel
internal class JoinKeygenViewModel @Inject constructor(
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
                val deepLink = DeepLinkHelper(
                    Base64.UrlSafe.decode(args.qr)
                        .decodeToString()
                )

                val bytes = decompressQr(
                    (deepLink.getJsonData() ?: error("Invalid QR code"))
                        .decodeBase64Bytes()
                )

                val existingVaults = vaultRepository.getAll()

                val session = when (deepLink.getTssAction()) {
                    TssAction.KEYGEN -> {
                        val message = mapKeygenMessageFromProto(
                            protoBuf.decodeFromByteArray<KeygenMessageProto>(bytes)
                        )

                        assertNoVaultNameDuplicates(existingVaults, message.vaultName)

                        val serverUrl = if (message.useVultisigRelay) {
                            Endpoints.VULTISIG_RELAY_URL
                        } else {
                            discoverMediator(message.serviceName)
                        }

                        Session(
                            sessionId = message.sessionID,
                            action = TssAction.KEYGEN,
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
                        )
                    }

                    TssAction.ReShare -> {
                        val message = mapReshareMessageFromProto(
                            protoBuf.decodeFromByteArray<ReshareMessageProto>(bytes)
                        )

                        val existingVault = existingVaults
                            .find { it.pubKeyECDSA == message.pubKeyECDSA }

                        if (existingVault != null &&
                            existingVault.resharePrefix != message.oldResharePrefix
                        ) {
                            error("Wrong reshare prefix")
                        }

                        // if we don't reshare vault which we already have,
                        // we should not create duplicate names
                        if (existingVault == null) {
                            assertNoVaultNameDuplicates(existingVaults, message.vaultName)
                        }

                        val serverUrl = if (message.useVultisigRelay) {
                            Endpoints.VULTISIG_RELAY_URL
                        } else {
                            discoverMediator(message.serviceName)
                        }


                        Session(
                            sessionId = message.sessionID,
                            action = TssAction.ReShare,
                            hexChainCode = existingVault?.hexChainCode ?: message.hexChainCode,
                            serviceName = message.serviceName,
                            useVultisigRelay = message.useVultisigRelay,
                            encryptionKeyHex = message.encryptionKeyHex,
                            vaultName = existingVault?.name ?: message.vaultName,
                            libType = message.libType,
                            localPartyId = Utils.deviceName(context),
                            serverUrl = serverUrl,
                            oldCommittee = message.oldParties,
                            oldResharePrefix = message.oldResharePrefix,
                        )
                    }

                    else -> error("Unknown TssAction")
                }

                sessionApi.startSession(
                    session.serverUrl,
                    session.sessionId,
                    listOf(session.localPartyId)
                )

                waitForKeygenToStart(session)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun assertNoVaultNameDuplicates(existingVaults: List<Vault>, name: String) {
        if (existingVaults.any { it.name == name }) {
            throw DuplicateVaultNameException()
        }
    }

    private suspend fun discoverMediator(serviceName: String): String = suspendCoroutine { cont ->
        discoveryListener = MediatorServiceDiscoveryListener(
            nsdManager = nsdManager,
            serviceName = serviceName,
            onServerAddressDiscovered = { serverUrl ->
                nsdManager.stopServiceDiscovery(discoveryListener)

                cont.resume(serverUrl)
            }
        )

        nsdManager.discoverServices(
            "_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener
        )
    }

    private suspend fun waitForKeygenToStart(
        session: Session,
    ) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                val keygenCommittee = try {
                    fetchKeygenCommittee(session)
                } catch (e: Exception) {
                    emptyList()
                }

                if (session.localPartyId in keygenCommittee) {
                    state.update { it.copy(isSuccess = true) }

                    delay(1.5.seconds)

                    navigator.route(
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

                            oldCommittee = session.oldCommittee,
                            oldResharePrefix = session.oldResharePrefix,

                            email = null,
                            password = null,
                            hint = null,
                        )
                    )

                    return@withContext
                }

                delay(1.seconds)
            }
        }
    }

    private suspend fun fetchKeygenCommittee(
        session: Session,
    ) = sessionApi.checkCommittee(session.serverUrl, session.sessionId)

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
            nsdManager.resolveService(
                service,
                MediatorServiceDiscoveryListener(nsdManager, serviceName, onServerAddressDiscovered)
            )
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
        Timber.d("Failed to resolve service: ${serviceInfo?.serviceName} , error: $errorCode")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        Timber.d("Service resolved: ${serviceInfo?.serviceName} ,address: ${serviceInfo?.host?.address.toString()} , port: ${serviceInfo?.port}")

        serviceInfo?.let { it ->
            val address = it.host
            if (address !is Inet4Address) {
                return
            }
            if (address.isLoopbackAddress) {
                return
            }
            address.hostAddress?.let {
                // This is a workaround for the emulator
                if (it == "10.0.2.16") {
                    onServerAddressDiscovered("http://192.168.1.35:18080")
                    return
                }
                onServerAddressDiscovered("http://${it}:${serviceInfo.port}")
            }
        }
    }

}
