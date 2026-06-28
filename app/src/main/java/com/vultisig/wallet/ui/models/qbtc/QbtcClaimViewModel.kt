@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.qbtc

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.ClaimableUtxo
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.LoadClaimableQbtcUtxosUseCase
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimAmountFormatter
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBlockedReason
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBtcRoundRunner
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimConfig
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimError
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimLoadResult
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimOrchestrator
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimPeerResultPusher
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimPhase
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimRunInput
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcProofService
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mappers.PayloadToProtoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.qbtc.QbtcClaimFastVaultRoundRunner
import com.vultisig.wallet.data.qbtc.QbtcClaimKeysignSession
import com.vultisig.wallet.data.qbtc.QbtcClaimRelayResultPusher
import com.vultisig.wallet.data.qbtc.QbtcClaimSecureVaultRoundRunner
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.keysign.MissingQbtcClaimAccountException
import com.vultisig.wallet.ui.models.keysign.ResolveQbtcClaimCoinsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.encodeBase64
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

@Immutable
internal data class QbtcClaimUtxoUiModel(
    val key: String,
    val shortId: String,
    val subtitleConfirmations: Long?,
    val qbtcAmount: String,
    val btcAmount: String,
)

@Immutable
internal sealed interface QbtcClaimUiState {
    data object Loading : QbtcClaimUiState

    data class Blocked(val reason: QbtcClaimBlockedReason) : QbtcClaimUiState

    data class Selecting(
        val utxos: List<QbtcClaimUtxoUiModel>,
        val selectedKeys: Set<String>,
        val totalSelectedSats: Long,
        val totalEligibleSats: Long,
        val canConfirm: Boolean,
        val isAllSelected: Boolean,
    ) : QbtcClaimUiState

    /** Secure Vault: showing the pairing QR and waiting for the co-signing device(s) to join. */
    data class Pairing(
        val qr: BitmapPainter?,
        val joinedDevices: List<String>,
        val localPartyId: String,
        val minimumDevices: Int,
    ) : QbtcClaimUiState

    data class Signing(val phase: QbtcClaimPhase) : QbtcClaimUiState

    data class Done(val txHash: String, val totalSats: Long, val explorerUrl: String) :
        QbtcClaimUiState

    data class Failed(val error: QbtcClaimError) : QbtcClaimUiState
}

@HiltViewModel
internal class QbtcClaimViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val resolveQbtcClaimCoins: ResolveQbtcClaimCoinsUseCase,
    private val loadClaimableUtxos: LoadClaimableQbtcUtxosUseCase,
    private val proofService: QbtcProofService,
    private val fastVaultRoundRunner: QbtcClaimFastVaultRoundRunner,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val sessionApi: SessionApi,
    private val discoverParticipants: DiscoverParticipantsUseCase,
    private val payloadToProtoMapper: PayloadToProtoMapper,
    private val compressQr: CompressQrUseCase,
    private val generateQrBitmap: GenerateQrBitmap,
    private val generateServiceName: GenerateServiceName,
    private val encryption: Encryption,
    private val protoBuf: ProtoBuf,
    private val json: Json,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.QbtcClaim>().vaultId

    val uiState = MutableStateFlow<QbtcClaimUiState>(QbtcClaimUiState.Loading)

    private var vault: Vault? = null
    private var btcCoin: Coin? = null
    private var qbtcCoin: Coin? = null
    private var claimable: List<ClaimableUtxo> = emptyList()
    private val selectedKeys = linkedSetOf<String>()
    private var claimJob: Job? = null

    init {
        load()
    }

    fun load() {
        uiState.value = QbtcClaimUiState.Loading
        viewModelScope.safeLaunch {
            val loadedVault = vaultRepository.get(vaultId)
            // The claim needs a Bitcoin and a QBTC coin, but neither chain has to be enabled in the
            // vault — they're derived on the fly when missing so the claim works regardless of
            // which
            // chains the user has added (as Windows does; iOS currently blocks when the coin is
            // absent — see https://github.com/vultisig/vultisig-ios/issues/4679).
            val coins =
                if (loadedVault == null) null
                else
                    try {
                        resolveQbtcClaimCoins(loadedVault)
                    } catch (_: MissingQbtcClaimAccountException) {
                        null
                    }
            if (loadedVault == null) {
                uiState.value =
                    QbtcClaimUiState.Blocked(
                        QbtcClaimBlockedReason.UtxoFetchFailed("Vault not found")
                    )
                return@safeLaunch
            }
            if (coins == null) {
                uiState.value =
                    QbtcClaimUiState.Blocked(
                        QbtcClaimBlockedReason.UtxoFetchFailed("Missing Bitcoin or QBTC account")
                    )
                return@safeLaunch
            }
            vault = loadedVault
            btcCoin = coins.btc
            qbtcCoin = coins.qbtc

            when (val result = loadClaimableUtxos(coins.btc.address)) {
                is QbtcClaimLoadResult.Blocked -> {
                    uiState.value = QbtcClaimUiState.Blocked(result.reason)
                }
                is QbtcClaimLoadResult.Available -> {
                    claimable = result.utxos
                    // Preselect up to the cap, mirroring iOS's `prefix(maxClaimUtxos)`. Selecting
                    // everything unbounded would push the count past the cap on wallets with >50
                    // claimable UTXOs and permanently disable Confirm.
                    selectedKeys.clear()
                    selectedKeys.addAll(
                        claimable.take(QbtcClaimConfig.MAX_CLAIM_UTXOS).map { it.key() }
                    )
                    emitSelecting()
                }
            }
        }
    }

    fun toggle(key: String) {
        when {
            key in selectedKeys -> selectedKeys.remove(key)
            selectedKeys.size < QbtcClaimConfig.MAX_CLAIM_UTXOS -> selectedKeys.add(key)
        }
        emitSelecting()
    }

    /** Fast Vault: sign with the server co-signer using the entered vault password. */
    fun confirm(fastVaultPassword: String) {
        val claim = readyClaim() ?: return
        claimJob =
            viewModelScope.safeLaunch {
                runOrchestrator(
                    claim.vault,
                    claim.btcCoin,
                    claim.qbtcCoin,
                    claim.selected,
                    fastVaultPassword,
                    fastVaultRoundRunner,
                    pusher = null,
                )
            }
    }

    /** Secure Vault: pair with the co-signing device over a QR, then run the claim. */
    fun startSecureVaultClaim() {
        val claim = readyClaim() ?: return
        claimJob =
            viewModelScope.safeLaunch {
                val session = pairAndKickoff(claim.vault, claim.btcCoin)
                if (session == null) {
                    uiState.value = QbtcClaimUiState.Failed(QbtcClaimError.PAIRING_TIMEOUT)
                    return@safeLaunch
                }
                runOrchestrator(
                    claim.vault,
                    claim.btcCoin,
                    claim.qbtcCoin,
                    claim.selected,
                    fastVaultPassword = "",
                    runner = QbtcClaimSecureVaultRoundRunner(session, sessionApi, encryption),
                    pusher = QbtcClaimRelayResultPusher(session, sessionApi, encryption, json),
                )
            }
    }

    /**
     * Resolves the inputs needed to start a claim, or null when a claim is already running, the
     * selection is invalid, or the vault/coins haven't loaded yet — each of which aborts the
     * action.
     */
    private fun readyClaim(): ReadyClaim? {
        if (claimJob?.isActive == true) return null
        if (!canConfirm()) return null
        val vault = vault ?: return null
        val btcCoin = btcCoin ?: return null
        val qbtcCoin = qbtcCoin ?: return null
        return ReadyClaim(vault, btcCoin, qbtcCoin, selectedUtxos())
    }

    fun retry() = load()

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun isFastVault(): Boolean = vault?.isFastVault() ?: true

    private suspend fun runOrchestrator(
        vault: Vault,
        btcCoin: Coin,
        qbtcCoin: Coin,
        selected: List<ClaimableUtxo>,
        fastVaultPassword: String,
        runner: QbtcClaimBtcRoundRunner,
        pusher: QbtcClaimPeerResultPusher?,
    ) = coroutineScope {
        val orchestrator = QbtcClaimOrchestrator(proofService, runner, pusher)
        val collector = launch { orchestrator.phase.collect { onPhase(it) } }
        try {
            orchestrator.run(
                QbtcClaimRunInput(
                    vault = vault,
                    btcCoin = btcCoin,
                    qbtcCoin = qbtcCoin,
                    utxos = selected,
                    fastVaultPassword = fastVaultPassword,
                )
            )
        } finally {
            collector.cancel()
        }
        onPhase(orchestrator.phase.value)
    }

    /**
     * Provisions a relay session, shows the pairing QR (a normal keysign QR flagged `isQbtcClaim`),
     * waits for the co-signing device to register, then kicks off the committee and returns the
     * established session. The peer recomputes the claim hash itself, so the QR carries no signing
     * material. Returns null if no peer joins within [PEER_DISCOVERY_TIMEOUT] so the caller can
     * surface a clear error instead of leaving the user stranded on the pairing screen.
     */
    private suspend fun pairAndKickoff(vault: Vault, btcCoin: Coin): QbtcClaimKeysignSession? {
        val sessionId = UUID.randomUUID().toString()
        val encryptionKeyHex = Utils.encryptionKeyHex
        val serverUrl = Endpoints.VULTISIG_RELAY_URL
        val localPartyId = vault.localPartyID
        val threshold = Utils.getThreshold(vault.signers.size)

        fun pairing(qr: BitmapPainter?, devices: List<String>) =
            QbtcClaimUiState.Pairing(
                qr = qr,
                joinedDevices = devices,
                localPartyId = localPartyId,
                minimumDevices = threshold,
            )

        uiState.value = pairing(qr = null, devices = emptyList())
        val qr =
            withContext(Dispatchers.IO) {
                renderPairingQr(vault, btcCoin, sessionId, encryptionKeyHex)
            }
        uiState.value = pairing(qr = qr, devices = emptyList())

        sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))

        val peersNeeded = (threshold - 1).coerceAtLeast(1)
        // Bounded by a generous, human-paced timeout — pairing means picking up a second device and
        // scanning, so it allows far longer than the Fast Vault runner's 60s server-response wait.
        // withTimeoutOrNull (not withTimeout) avoids a TimeoutCancellationException that safeLaunch
        // would rethrow as a plain cancellation, which would leave the UI stuck on Pairing.
        val peers =
            withTimeoutOrNull(PEER_DISCOVERY_TIMEOUT) {
                discoverParticipants(serverUrl, sessionId, localPartyId)
                    .onEach { devices -> uiState.value = pairing(qr = qr, devices = devices) }
                    .first { it.size >= peersNeeded }
            } ?: return null

        val committee = (listOf(localPartyId) + peers.take(peersNeeded)).distinct()
        sessionApi.startWithCommittee(serverUrl, sessionId, committee)
        return QbtcClaimKeysignSession(serverUrl, sessionId, encryptionKeyHex, committee)
    }

    private fun renderPairingQr(
        vault: Vault,
        btcCoin: Coin,
        sessionId: String,
        encryptionKeyHex: String,
    ): BitmapPainter {
        val payloadProto = payloadToProtoMapper(claimPayload(vault, btcCoin))
        val deepLink =
            "https://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}" +
                "&vault=${vault.pubKeyECDSA}&jsonData=" +
                compressQr(
                        protoBuf.encodeToByteArray(
                            KeysignMessageProto(
                                sessionId = sessionId,
                                serviceName = generateServiceName(),
                                keysignPayload = payloadProto,
                                encryptionKeyHex = encryptionKeyHex,
                                useVultisigRelay = true,
                            )
                        )
                    )
                    .encodeBase64()
        val bitmap = generateQrBitmap(deepLink, Color.White, Color.Transparent, null)
        return BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None)
    }

    /** A flag-carrier keysign payload — the peer recomputes the claim hash from its own vault. */
    private fun claimPayload(vault: Vault, btcCoin: Coin): KeysignPayload =
        KeysignPayload(
            coin = btcCoin,
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger.ZERO, sendMaxAmount = false),
            vaultPublicKeyECDSA = vault.pubKeyECDSA,
            vaultLocalPartyID = vault.localPartyID,
            libType = vault.libType,
            wasmExecuteContractPayload = null,
            isQbtcClaim = true,
            skipBroadcast = true,
        )

    private fun selectedUtxos(): List<ClaimableUtxo> = claimable.filter { it.key() in selectedKeys }

    private fun onPhase(phase: QbtcClaimPhase) {
        uiState.value =
            when (phase) {
                QbtcClaimPhase.Idle,
                QbtcClaimPhase.SigningBtc,
                QbtcClaimPhase.GeneratingProofAndBroadcasting -> QbtcClaimUiState.Signing(phase)
                is QbtcClaimPhase.Done ->
                    QbtcClaimUiState.Done(
                        txHash = phase.result.txHashHex,
                        totalSats = phase.result.totalSatsClaimed,
                        explorerUrl =
                            explorerLinkRepository.getTransactionLink(
                                Chain.Qbtc,
                                phase.result.txHashHex,
                            ),
                    )
                is QbtcClaimPhase.Failed -> QbtcClaimUiState.Failed(phase.errorKind)
            }
    }

    private fun canConfirm(): Boolean =
        selectedKeys.isNotEmpty() && selectedKeys.size <= QbtcClaimConfig.MAX_CLAIM_UTXOS

    private fun emitSelecting() {
        val total = selectedUtxos().sumOf { it.amount }
        val totalEligible = claimable.sumOf { it.amount }
        val cap = minOf(claimable.size, QbtcClaimConfig.MAX_CLAIM_UTXOS)
        uiState.value =
            QbtcClaimUiState.Selecting(
                utxos = claimable.map { it.toUiModel() },
                selectedKeys = selectedKeys.toSet(),
                totalSelectedSats = total,
                totalEligibleSats = totalEligible,
                canConfirm = canConfirm(),
                isAllSelected = selectedKeys.size == cap,
            )
    }

    private fun ClaimableUtxo.key(): String = "$txid:$vout"

    private fun ClaimableUtxo.toUiModel(): QbtcClaimUtxoUiModel =
        QbtcClaimUtxoUiModel(
            key = key(),
            shortId = shortTxid(),
            subtitleConfirmations = confirmations,
            qbtcAmount = QbtcClaimAmountFormatter.formatQbtc(amount),
            btcAmount = QbtcClaimAmountFormatter.formatBtc(amount),
        )

    private fun ClaimableUtxo.shortTxid(): String =
        if (txid.length <= 14) "$txid:$vout" else "${txid.take(4)}…${txid.takeLast(4)}:$vout"

    private data class ReadyClaim(
        val vault: Vault,
        val btcCoin: Coin,
        val qbtcCoin: Coin,
        val selected: List<ClaimableUtxo>,
    )

    private companion object {
        val PEER_DISCOVERY_TIMEOUT = 300.seconds
    }
}
