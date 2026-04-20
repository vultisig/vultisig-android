@file:OptIn(ExperimentalSerializationApi::class, ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType
import vultisig.vault.v1.Vault as VaultProto

/**
 * Import-fixture generator. Remove `@Disabled` and run with:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests \
 *   "com.vultisig.wallet.data.usecases.GenerateImportFixtures.generate"
 * ```
 *
 * Output: `import-fixtures/` at the repo root. Encrypted fixtures use the password "test123".
 */
internal class GenerateImportFixtures {

    @Disabled("Fixture generator. Remove @Disabled to run.")
    @Test
    fun generate() {
        val dir =
            File(OUTPUT_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
        fixtures.forEach { it.writeTo(dir) }
        println("Wrote ${fixtures.size} fixtures to ${dir.absoluteFile.canonicalFile}:")
        dir.listFiles()
            ?.sortedBy { it.name }
            ?.forEach { println("  ${it.name.padEnd(32)} ${it.length()}B") }
    }

    // --- fixture spec (declarative, read top-to-bottom) ----------------------

    private val fixtures =
        listOf(
            text("01-valid-unencrypted.vult") { vault("Fixture Unencrypted").encode() },
            text("02-valid-encrypted.vult") { vault("Fixture Encrypted").encode(PASSWORD) },
            bytes("03-random-garbage.vult") { randomBytes(2048) },
            text("04-plaintext-renamed.vult") { "this is not a vault file, just plain text" },
            text("05-empty.vult") { "" },
            text("06-truncated-encrypted.vult") {
                vault("Will Be Truncated").encode(PASSWORD).take(50)
            },
            text("07-bitflipped-encrypted.vult") {
                vault("Bitflipped").encode(PASSWORD, corrupt = ::bitflipLastFifth)
            },
            zip("08-multi-vault.zip") {
                text("bundle-a.vult") { vault("Bundle A").encode() }
                text("bundle-b.vult") { vault("Bundle B").encode(PASSWORD) }
            },
            zip("09-mixed-good-and-bad.zip") {
                text("good.vult") { vault("Mixed Good").encode() }
                bytes("bad.vult") { randomBytes(1024) }
            },
            zip("10-only-garbage.zip") {
                bytes("junk1.vult") { randomBytes(512) }
                text("junk2.vult") { "plain text entry" }
            },
        )

    // --- sealed fixture hierarchy --------------------------------------------

    private sealed class Fixture(val name: String) {
        abstract fun writeTo(dir: File)
    }

    private class Text(name: String, val content: () -> String) : Fixture(name) {
        override fun writeTo(dir: File) = File(dir, name).writeText(content())
    }

    private class Bytes(name: String, val content: () -> ByteArray) : Fixture(name) {
        override fun writeTo(dir: File) = File(dir, name).writeBytes(content())
    }

    private class Zip(name: String, val entries: List<Entry>) : Fixture(name) {
        override fun writeTo(dir: File) {
            ZipOutputStream(File(dir, name).outputStream()).use { zip ->
                entries.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(entry.content())
                    zip.closeEntry()
                }
            }
        }

        class Entry(val name: String, val content: () -> ByteArray)
    }

    private class ZipBuilder {
        val entries = mutableListOf<Zip.Entry>()

        fun text(name: String, content: () -> String) {
            entries += Zip.Entry(name) { content().toByteArray() }
        }

        fun bytes(name: String, content: () -> ByteArray) {
            entries += Zip.Entry(name, content)
        }
    }

    private fun text(name: String, content: () -> String): Fixture = Text(name, content)

    private fun bytes(name: String, content: () -> ByteArray): Fixture = Bytes(name, content)

    private fun zip(name: String, build: ZipBuilder.() -> Unit): Fixture =
        Zip(name, ZipBuilder().apply(build).entries)

    // --- vault + container helpers -------------------------------------------

    private fun vault(name: String): VaultProto =
        VaultProto(
            name = name,
            publicKeyEcdsa = "03aa".padEnd(66, '0'),
            publicKeyEddsa = "0".repeat(64),
            hexChainCode = "11".repeat(32),
            localPartyId = "fixture-device-${name.hashCode()}",
            signers = listOf("fixture-device-1", "fixture-device-2"),
            resharePrefix = "fx",
            libType = LibType.LIB_TYPE_DKLS,
            keyShares =
                listOf(
                    VaultProto.KeyShare(
                        publicKey = "03aa".padEnd(66, '0'),
                        keyshare = "dGVzdC1rZXlzaGFyZS1ieXRlcw==",
                    )
                ),
            chainPublicKeys = emptyList(),
        )

    /**
     * Wraps the vault in a [VaultContainerProto] and returns the base64 string the app would write
     * to disk. Pass [password] to encrypt (AES-GCM); pass [corrupt] to mutate the payload between
     * encryption and containerisation (useful for bit-flip / truncation fixtures).
     */
    private fun VaultProto.encode(
        password: String? = null,
        corrupt: (ByteArray) -> ByteArray = { it },
    ): String {
        val inner = ProtoBuf.encodeToByteArray(this)
        val payload =
            corrupt(if (password == null) inner else aesGcmEncrypt(inner, password.toByteArray()))
        val container =
            VaultContainerProto(vault = Base64.encode(payload), isEncrypted = password != null)
        return Base64.encode(ProtoBuf.encodeToByteArray(container))
    }

    // --- crypto / util -------------------------------------------------------

    private fun aesGcmEncrypt(data: ByteArray, password: ByteArray): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest(password)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(data)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)

    private fun bitflipLastFifth(bytes: ByteArray): ByteArray =
        bytes.also {
            val i = (it.size * 4) / 5
            it[i] = (it[i].toInt() xor 0xFF).toByte()
        }

    companion object {
        private const val OUTPUT_DIR = "../import-fixtures"
        private const val PASSWORD = "test123"
    }
}
