package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.Encryption
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay

class FastVaultKeysignHelper
@Inject
constructor(
    private val sessionApi: SessionApi,
    private val vultiSignerRepository: VultiSignerRepository,
    private val encryption: Encryption,
) {

    /**
     * Signs a message hash using FastVault keysign ceremony (DKLS MPC 2-of-3).
     *
     * @param vault The vault to sign with (must be a FastVault)
     * @param password The FastVault password
     * @param messageHash The hex-encoded message hash to sign
     * @return The signature as "0x" + r + s + recovery_id
     */
    suspend fun sign(vault: Vault, password: String, messageHash: String): String {
        require(vault.isFastVault()) { "Vault is not a FastVault" }

        val sessionId = UUID.randomUUID().toString()
        val encryptionKeyHex = Utils.encryptionKeyHex
        val mediatorUrl = Endpoints.VULTISIG_RELAY_URL
        val localPartyId = vault.localPartyID
        val derivePath = "m/44'/60'/0'/0/0"

        // Step 1: Register session with relay
        sessionApi.startSession(mediatorUrl, sessionId, listOf(localPartyId))

        // Step 2: Tell server signer to join
        vultiSignerRepository.joinKeysign(
            JoinKeysignRequestJson(
                publicKeyEcdsa = vault.pubKeyECDSA,
                messages = listOf(messageHash),
                sessionId = sessionId,
                hexEncryptionKey = encryptionKeyHex,
                derivePath = derivePath,
                isEcdsa = true,
                password = password,
                chain = "",
            )
        )

        // Step 3: Poll for participants until server joins
        val committee = waitForCommittee(mediatorUrl, sessionId, localPartyId)

        // Step 4: Start session with the full committee
        sessionApi.startWithCommittee(mediatorUrl, sessionId, committee)

        // Step 5: Run DKLS keysign
        // KeyImport vaults normally sign with root key (chainPath=null in DKLSKeysign),
        // but for auth we need the derived key signature. Override libType to DKLS
        // so DKLSKeysign applies the derivePath.
        val keysignVault =
            if (vault.libType == SigningLibType.KeyImport) {
                vault.copy(libType = SigningLibType.DKLS)
            } else {
                vault
            }
        val dkls =
            DKLSKeysign(
                keysignCommittee = committee,
                mediatorURL = mediatorUrl,
                sessionID = sessionId,
                messageToSign = listOf(messageHash),
                vault = keysignVault,
                encryptionKeyHex = encryptionKeyHex,
                chainPath = derivePath,
                isInitiateDevice = true,
                sessionApi = sessionApi,
                encryption = encryption,
            )

        dkls.keysignWithRetry()

        // Step 6: Extract and format signature
        val sig =
            dkls.signatures[messageHash]
                ?: error("Keysign completed but no signature found for message")

        // Ethereum personal sign expects v = 27 + recoveryId (0 or 1)
        val recoveryByte = sig.recoveryID.toInt(16) + 27
        return "0x${sig.r}${sig.s}${"%02x".format(recoveryByte)}"
    }

    private suspend fun waitForCommittee(
        mediatorUrl: String,
        sessionId: String,
        localPartyId: String,
    ): List<String> {
        val maxAttempts = 60 // 60 seconds timeout
        repeat(maxAttempts) {
            try {
                val participants = sessionApi.getParticipants(mediatorUrl, sessionId)
                if (participants.size >= 2) {
                    return participants
                }
            } catch (_: Exception) {
                // retry
            }
            delay(1000)
        }
        error("Timed out waiting for server signer to join keysign session")
    }
}
