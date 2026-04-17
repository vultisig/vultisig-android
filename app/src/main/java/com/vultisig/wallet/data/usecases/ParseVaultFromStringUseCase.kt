@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.mappers.VaultFromOldJsonMapper
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.OldJsonVault
import com.vultisig.wallet.data.models.OldJsonVaultRoot
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.toSigningLibType
import com.vultisig.wallet.data.utils.runCatchingCancellable
import io.ktor.util.decodeBase64Bytes
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import vultisig.vault.v1.Vault as VaultProto

internal interface ParseVaultFromStringUseCase : (String, String?) -> Vault

/**
 * Thrown when the input is a recognised encrypted vault whose password is wrong or missing. The UI
 * maps this to "incorrect password / enter password" rather than a generic error.
 */
internal class WrongPasswordException : IllegalStateException("Invalid or missing vault password")

/**
 * Thrown when the input doesn't match any supported vault format, or the decrypted payload is
 * structurally invalid (not a valid proto, not hex, not JSON). Distinct from
 * [WrongPasswordException] so the UI can show "unsupported or corrupted file" instead of falsely
 * blaming the password.
 */
internal class MalformedVaultException(cause: Throwable? = null) :
    IllegalStateException("Vault file is corrupted or unsupported", cause)

internal class ParseVaultFromStringUseCaseImpl
@Inject
constructor(
    private val vaultFromOldJsonMapper: VaultFromOldJsonMapper,
    private val mapHexToPlainString: MapHexToPlainString,
    private val encryption: Encryption,
    private val protoBuf: ProtoBuf,
    private val json: Json,
) : ParseVaultFromStringUseCase {

    /**
     * Parses [input] as a vault, optionally decrypting with [password].
     *
     * Tries the new protobuf container format first; falls through to the legacy paths (plain JSON,
     * then AES-CBC encrypted wrapper) when the input isn't recognisably new-format.
     *
     * @throws WrongPasswordException when the file is confirmed encrypted and the password is wrong
     *   or missing
     * @throws MalformedVaultException when no supported format matches or the decrypted payload is
     *   structurally invalid
     */
    override fun invoke(input: String, password: String?): Vault =
        parseNewFormat(input, password) ?: parseOldFormat(input, password)

    private fun parseNewFormat(input: String, password: String?): Vault? {
        val container = decodeVaultContainer(input) ?: return null
        val payload = container.decodeVaultPayloadOrNull() ?: return null
        val vaultBytes = container.resolveVaultBytes(payload, password)
        return decodeVaultProto(vaultBytes).toDomain()
    }

    private fun parseOldFormat(input: String, password: String?): Vault {
        parsePlainOldJsonOrNull(input)?.let {
            return vaultFromOldJsonMapper(it)
        }

        // Not plain JSON. Legacy vaults are AES-CBC ciphertext, so their base64-decoded length
        // is a non-zero multiple of the block size. Anything else is garbage, not a
        // password-protected vault, and must not prompt for a password.
        if (!looksLikeLegacyCiphertext(input)) throw MalformedVaultException()
        if (password.isNullOrBlank()) throw WrongPasswordException()

        val decryptedBytes =
            tryDecrypt(input.decodeBase64Bytes(), password) ?: throw WrongPasswordException()
        val decodedJson = decodeLegacyVaultJson(decryptedBytes)
        val oldVault = decodeOldVaultRoot(decodedJson)
        return vaultFromOldJsonMapper(oldVault)
    }

    private fun decodeVaultContainer(input: String): VaultContainerProto? =
        runCatchingCancellable {
                protoBuf.decodeFromByteArray<VaultContainerProto>(input.decodeBase64Bytes())
            }
            .getOrNull()

    private fun VaultContainerProto.decodeVaultPayloadOrNull(): ByteArray? =
        // protobuf3 permissively decodes unrelated bytes into a container with default fields;
        // an empty inner payload isn't proof this is really a new-format vault, so signal "not
        // recognised" and let the old-format parser have a go.
        runCatchingCancellable { vault.decodeBase64Bytes() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun VaultContainerProto.resolveVaultBytes(
        payload: ByteArray,
        password: String?,
    ): ByteArray =
        when {
            !isEncrypted -> payload
            password.isNullOrBlank() -> throw WrongPasswordException()
            else -> tryDecrypt(payload, password) ?: throw WrongPasswordException()
        }

    private fun decodeVaultProto(bytes: ByteArray): VaultProto = orMalformed {
        protoBuf.decodeFromByteArray<VaultProto>(bytes)
    }

    private fun parsePlainOldJsonOrNull(input: String): OldJsonVault? =
        runCatchingCancellable { json.decodeFromString<OldJsonVault>(input) }.getOrNull()

    // [Encryption.decrypt] can both return null AND throw on wrong-password input (the GCM path
    // throws AEADBadTagException, the CBC fallback throws IllegalBlockSizeException on oddly
    // sized data). Collapse both into "couldn't decrypt" so callers can map to
    // [WrongPasswordException].
    private fun tryDecrypt(data: ByteArray, password: String): ByteArray? =
        runCatchingCancellable { encryption.decrypt(data, password.toByteArray()) }.getOrNull()

    private fun decodeLegacyVaultJson(bytes: ByteArray): String = orMalformed {
        mapHexToPlainString(bytes.decodeToString())
    }

    private fun decodeOldVaultRoot(decodedJson: String): OldJsonVault = orMalformed {
        json.decodeFromString<OldJsonVaultRoot>(decodedJson).vault
    }

    private fun looksLikeLegacyCiphertext(input: String): Boolean =
        runCatchingCancellable { input.decodeBase64Bytes() }
            .getOrNull()
            ?.let { it.isNotEmpty() && it.size % AES_BLOCK_SIZE == 0 } ?: false

    private fun VaultProto.toDomain(): Vault =
        Vault(
            id = UUID.randomUUID().toString(),
            name = name,
            pubKeyECDSA = publicKeyEcdsa,
            pubKeyEDDSA = publicKeyEddsa,
            hexChainCode = hexChainCode,
            localPartyID = localPartyId,
            signers = signers,
            resharePrefix = resharePrefix,
            keyshares = keyShares.toDomainKeyShares(),
            pubKeyMLDSA = publicKeyMldsa44,
            coins = emptyList(),
            libType = libType.toSigningLibType(),
            chainPublicKeys = chainPublicKeys.toDomainChainPublicKeys(),
        )

    private fun Iterable<VaultProto.KeyShare?>.toDomainKeyShares(): List<KeyShare> =
        filterNotNull()
            .map { KeyShare(pubKey = it.publicKey, keyShare = it.keyshare) }
            // Dedupe by pubKey. Chains sharing a derivation path (e.g. EVM coinType 60) produce
            // duplicates; last-wins matches the extension's behaviour.
            .associateBy(KeyShare::pubKey)
            .values
            .toList()

    private fun Iterable<VaultProto.ChainPublicKey?>.toDomainChainPublicKeys():
        List<ChainPublicKey> =
        filterNotNull().map {
            ChainPublicKey(chain = it.chain, publicKey = it.publicKey, isEddsa = it.isEddsa)
        }

    private companion object {
        private const val AES_BLOCK_SIZE = 16
    }
}

/**
 * Thin wrapper around [runCatchingCancellable] that converts any non-cancellation failure into a
 * [MalformedVaultException], preserving the original throwable as the cause.
 */
private inline fun <T> orMalformed(block: () -> T): T =
    runCatchingCancellable(block).getOrElse { throw MalformedVaultException(it) }
