@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.peer

import com.vultisig.wallet.data.api.models.signer.BatchKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.BatchReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.CreateMldsaVaultRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeyImportRequest
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.MigrateRequest
import com.vultisig.wallet.data.api.models.signer.toJson
import com.vultisig.wallet.data.keygen.isBatchEligibleReshare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.models.proto.v1.SingleKeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

private fun KeygenActionContext.encode(linkType: String, proto: ByteArray): String =
    "https://vultisig.com?type=NewVault&tssType=$linkType&jsonData=" +
        compressQr(proto).encodeBase64()

internal object KeygenStrategy : KeygenActionStrategy {
    override val linkType: String = "Keygen"

    override suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String =
        ctx.encode(
            linkType,
            ctx.protoBuf.encodeToByteArray(
                KeygenMessageProto(
                    sessionId = ctx.sessionId,
                    hexChainCode = ctx.session.hexChainCode,
                    serviceName = ctx.serviceName,
                    encryptionKeyHex = ctx.encryptionKeyHex,
                    useVultisigRelay = isRelayEnabled,
                    vaultName = ctx.vaultName,
                    libType = ctx.session.libType.toProto(),
                )
            ),
        )

    override suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String) {
        if (ctx.session.isTssBatchEnabled) {
            ctx.vultiSignerRepository.joinBatchKeygen(
                BatchKeygenRequestJson(
                    vaultName = ctx.vaultName,
                    sessionId = ctx.sessionId,
                    hexEncryptionKey = ctx.encryptionKeyHex,
                    hexChainCode = ctx.session.hexChainCode,
                    localPartyId = ctx.generateServerPartyId(),
                    encryptionPassword = password,
                    email = email,
                    libType = ctx.session.libType.toJson(),
                    protocols =
                        listOf(
                            BatchKeygenRequestJson.PROTOCOL_ECDSA,
                            BatchKeygenRequestJson.PROTOCOL_EDDSA,
                        ),
                )
            )
        } else {
            ctx.vultiSignerRepository.joinKeygen(
                JoinKeygenRequestJson(
                    vaultName = ctx.vaultName,
                    sessionId = ctx.sessionId,
                    hexEncryptionKey = ctx.encryptionKeyHex,
                    hexChainCode = ctx.session.hexChainCode,
                    localPartyId = ctx.generateServerPartyId(),
                    encryptionPassword = password,
                    email = email,
                    libType = ctx.session.libType.toJson(),
                )
            )
        }
    }
}

internal object KeyImportStrategy : KeygenActionStrategy {
    override val linkType: String = "KeyImport"

    override fun resolveLibType(default: SigningLibType): SigningLibType = SigningLibType.KeyImport

    override suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String =
        ctx.encode(
            linkType,
            ctx.protoBuf.encodeToByteArray(
                KeygenMessageProto(
                    sessionId = ctx.sessionId,
                    hexChainCode = ctx.session.hexChainCode,
                    serviceName = ctx.serviceName,
                    encryptionKeyHex = ctx.encryptionKeyHex,
                    useVultisigRelay = isRelayEnabled,
                    vaultName = ctx.vaultName,
                    libType = SigningLibType.KeyImport.toProto(),
                    chains =
                        ctx.keyImportRepository.get()?.chainSettings?.map { it.chain.raw }
                            ?: emptyList(),
                )
            ),
        )

    override suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String) {
        // Server uses joinKeyImport endpoint to determine the flow
        ctx.vultiSignerRepository.joinKeyImport(
            JoinKeyImportRequest(
                vaultName = ctx.vaultName,
                sessionId = ctx.sessionId,
                hexEncryptionKey = ctx.encryptionKeyHex,
                hexChainCode = ctx.session.hexChainCode,
                localPartyId = ctx.generateServerPartyId(),
                encryptionPassword = password,
                email = email,
                libType = SigningLibType.DKLS.toJson(),
                chains =
                    ctx.keyImportRepository.get()?.chainSettings?.map { it.chain.raw }
                        ?: emptyList(),
            )
        )
    }
}

/** Builds the shared Reshare proto payload used by both ReShare and Migrate. */
private suspend fun buildResharePayload(
    ctx: KeygenActionContext,
    isRelayEnabled: Boolean,
    linkType: String,
    action: TssAction,
): String =
    ctx.encode(
        linkType,
        ctx.protoBuf.encodeToByteArray(
            ReshareMessageProto(
                sessionId = ctx.sessionId,
                hexChainCode = ctx.session.hexChainCode,
                serviceName = ctx.serviceName,
                publicKeyEcdsa = ctx.session.pubKeyEcdsa,
                oldParties = ctx.session.signers,
                encryptionKeyHex = ctx.encryptionKeyHex,
                useVultisigRelay = isRelayEnabled,
                oldResharePrefix = ctx.session.resharePrefix,
                vaultName = ctx.vaultName,
                libType = ctx.session.libType.toProto(),
                // Only opt the reshare ceremony into batch mode; migrate shares the same proto but
                // is excluded from batched reshare. Both DKLS and KeyImport vaults qualify
                // (matches iOS).
                isTssBatch =
                    isBatchEligibleReshare(action, ctx.session.libType) &&
                        ctx.session.isTssBatchEnabled,
            )
        ),
    )

internal object ReShareStrategy : KeygenActionStrategy {
    override val linkType: String = "Reshare"

    override suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String =
        buildResharePayload(ctx, isRelayEnabled, linkType, TssAction.ReShare)

    override suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String) {
        val session = ctx.session
        if (
            session.isTssBatchEnabled && isBatchEligibleReshare(TssAction.ReShare, session.libType)
        ) {
            require(session.pubKeyEcdsa.isNotBlank()) {
                "Reshare requires the existing vault's ECDSA public key"
            }
            ctx.vultiSignerRepository.joinBatchReshare(
                BatchReshareRequestJson(
                    publicKeyEcdsa = session.pubKeyEcdsa,
                    sessionId = ctx.sessionId,
                    hexEncryptionKey = ctx.encryptionKeyHex,
                    localPartyId = ctx.generateServerPartyId(),
                    oldParties = session.signers,
                    encryptionPassword = password,
                    email = email,
                    protocols =
                        listOf(
                            BatchReshareRequestJson.PROTOCOL_ECDSA,
                            BatchReshareRequestJson.PROTOCOL_EDDSA,
                        ),
                )
            )
        } else {
            ctx.vultiSignerRepository.joinReshare(
                JoinReshareRequestJson(
                    vaultName = ctx.vaultName,
                    publicKeyEcdsa = session.pubKeyEcdsa,
                    sessionId = ctx.sessionId,
                    hexEncryptionKey = ctx.encryptionKeyHex,
                    hexChainCode = session.hexChainCode,
                    localPartyId = session.localPartyId,
                    encryptionPassword = password,
                    email = email,
                    oldParties = session.signers,
                    oldResharePrefix = session.resharePrefix,
                )
            )
        }
    }
}

internal object MigrateStrategy : KeygenActionStrategy {
    override val linkType: String = "Migrate"

    override fun resolveLibType(default: SigningLibType): SigningLibType = SigningLibType.DKLS

    override suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String =
        buildResharePayload(ctx, isRelayEnabled, linkType, TssAction.Migrate)

    override suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String) {
        ctx.vultiSignerRepository.migrate(
            MigrateRequest(
                publicKeyEcdsa = ctx.session.pubKeyEcdsa,
                sessionId = ctx.sessionId,
                hexEncryptionKey = ctx.encryptionKeyHex,
                encryptionPassword = password,
                email = email,
            )
        )
    }
}

internal object SingleKeygenStrategy : KeygenActionStrategy {
    override val linkType: String = "SingleKeygen"

    override suspend fun buildPayload(ctx: KeygenActionContext, isRelayEnabled: Boolean): String =
        ctx.encode(
            linkType,
            ctx.protoBuf.encodeToByteArray(
                SingleKeygenMessageProto(
                    sessionId = ctx.sessionId,
                    hexChainCode = ctx.session.hexChainCode,
                    serviceName = ctx.serviceName,
                    publicKeyEcdsa = ctx.session.pubKeyEcdsa,
                    encryptionKeyHex = ctx.encryptionKeyHex,
                    useVultisigRelay = isRelayEnabled,
                    vaultName = ctx.vaultName,
                    libType = ctx.session.libType.toProto(),
                )
            ),
        )

    override suspend fun joinServer(ctx: KeygenActionContext, email: String, password: String) {
        ctx.vultiSignerRepository.createMldsa(
            CreateMldsaVaultRequestJson(
                publicKey = ctx.session.pubKeyEcdsa,
                sessionId = ctx.sessionId,
                hexEncryptionKey = ctx.encryptionKeyHex,
                encryptionPassword = password,
                email = email,
            )
        )
    }
}
