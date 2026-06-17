@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.peer

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Immutable snapshot of the per-session config resolved once by
 * [KeygenPeerDiscoveryViewModel.loadData]. Holding it as one object means every consumer (QR
 * payload, FastVault dispatch, navigation arg) observes the same values with no temporal coupling
 * on initialization order.
 */
internal data class KeygenSession(
    val hexChainCode: String,
    val localPartyId: String,
    val libType: SigningLibType,
    val pubKeyEcdsa: String,
    val signers: List<String>,
    val resharePrefix: String,
    val isTssBatchEnabled: Boolean,
)

/**
 * Collaborators and immutable inputs a [KeygenActionStrategy] needs to build its join-QR payload
 * and dispatch the FastVault server join. Bundled so the per-action logic lives in one place.
 */
internal class KeygenActionContext(
    val sessionId: String,
    val serviceName: String,
    val encryptionKeyHex: String,
    val vaultName: String,
    val session: KeygenSession,
    val compressQr: CompressQrUseCase,
    val protoBuf: ProtoBuf,
    val vultiSignerRepository: VultiSignerRepository,
    val generateServerPartyId: GenerateServerPartyId,
    val keyImportRepository: KeyImportRepository,
)

/**
 * Per-[TssAction] strategy owning the four action-specific concerns: the deep-link [linkType], the
 * generating-step [resolveLibType], the QR [buildPayload], and the FastVault [joinServer] dispatch.
 * Add or change an action in one implementation.
 */
internal interface KeygenActionStrategy {

    /** `tssType` string embedded in the join-QR deep link. */
    val linkType: String

    /**
     * Lib type used for the generating step. Most actions keep [default] (the session lib type);
     * Migrate forces DKLS and KeyImport forces KeyImport.
     */
    fun resolveLibType(default: SigningLibType): SigningLibType = default

    /** Builds the deep-link QR payload that joiners scan. */
    suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String

    /**
     * Dispatches the FastVault server join for this action. Only called once email and password are
     * known to be present, so both are passed non-null.
     */
    suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String)
}

internal fun TssAction.strategy(): KeygenActionStrategy =
    when (this) {
        TssAction.KEYGEN -> KeygenStrategy
        TssAction.KeyImport -> KeyImportStrategy
        TssAction.ReShare -> ReShareStrategy
        TssAction.Migrate -> MigrateStrategy
        TssAction.SingleKeygen -> SingleKeygenStrategy
    }
