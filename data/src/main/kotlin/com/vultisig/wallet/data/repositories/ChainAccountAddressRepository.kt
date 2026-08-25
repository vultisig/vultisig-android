@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SolanaAccountOwnership
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.chains.helpers.BittensorHelper
import com.vultisig.wallet.data.chains.helpers.MayaChainHelper
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.QbtcHelper
import com.vultisig.wallet.data.crypto.SolanaProgramDerivedAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import com.vultisig.wallet.data.utils.compatibleType
import javax.inject.Inject
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

interface ChainAccountAddressRepository {

    suspend fun getAddress(chain: Chain, vault: Vault): Pair<String, String>

    suspend fun getAddress(coin: Coin, vault: Vault): Pair<String, String>

    fun isValid(chain: Chain, address: String): Boolean

    /**
     * Whether [address] can be handed funds on [chain], and why not when it can't.
     *
     * Stricter than [isValid] on purpose, and separate from it on purpose: [isValid] also screens
     * contract and mint addresses, which are routinely program-derived, so the recipient rule
     * cannot live there without rejecting them.
     *
     * Suspends because an off-curve Solana address is only rejected once the cluster says it is a
     * token account; see [RecipientValidity.NotAWalletAddress].
     */
    suspend fun validateRecipient(chain: Chain, address: String): RecipientValidity
}

/** The verdict [ChainAccountAddressRepository.validateRecipient] returns. */
enum class RecipientValidity {
    Valid,

    /** Not an address on this chain at all. */
    InvalidForChain,

    /**
     * A well-formed address that cannot receive funds: on Solana, a token account. Sending to one
     * makes an SPL transfer derive an associated token account *of* a token account, which nobody
     * can spend from.
     *
     * Off-curve alone does not earn this verdict. Every program-derived address is off-curve, and
     * plenty of them are real destinations — a Squads vault or a DAO treasury holds SPL through
     * `ATA(pda, mint)` and spends it with `invoke_signed`. Only an address the cluster reports as
     * owned by a token program is refused; an off-curve address the cluster cannot be reached about
     * is refused too, since an unanswered lookup is not an answer.
     */
    NotAWalletAddress,
}

private const val EDDSA_PUB_KEY_HEX_LENGTH = 64

/** The programs that own token accounts: classic SPL Token and Token-2022. */
private val SOLANA_TOKEN_PROGRAM_IDS =
    setOf(
        "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
        "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
    )

private val HEX_CHARS = Regex("^[0-9a-fA-F]+$")

/**
 * Validates that [eddsaPubKey] is a 32-byte (64 hex-character) Ed25519 public key. Throws
 * [IllegalArgumentException] with a descriptive message instead of letting [PublicKey] throw a
 * generic [java.security.InvalidParameterException] when the key is malformed.
 */
internal fun validateEddsaPubKey(chain: Chain, eddsaPubKey: String) {
    require(eddsaPubKey.isNotBlank()) { "EdDSA public key for ${chain.raw} is missing" }
    require(eddsaPubKey.length == EDDSA_PUB_KEY_HEX_LENGTH) {
        "EdDSA public key for ${chain.raw} has invalid length: ${eddsaPubKey.length} " +
            "(expected $EDDSA_PUB_KEY_HEX_LENGTH hex characters)"
    }
    require(eddsaPubKey.matches(HEX_CHARS)) {
        "EdDSA public key for ${chain.raw} contains non-hex characters"
    }
}

internal class ChainAccountAddressRepositoryImpl
@Inject
constructor(private val solanaApi: SolanaApi) : ChainAccountAddressRepository {

    override suspend fun getAddress(chain: Chain, vault: Vault): Pair<String, String> {
        // For KeyImport vaults, chain-specific public keys are already derived.
        // Look for exact chain match first, then match by derivation path
        // (e.g., all EVM chains share m/44'/60'/0'/0/0)
        val chainPubKey = findChainPublicKey(chain, vault)

        when (chain.TssKeysignType) {
            TssKeyType.MLDSA -> {
                val mldsaPubKey = vault.pubKeyMLDSA
                require(mldsaPubKey.isNotBlank()) { "MLDSA public key is required for QBTC" }
                val address = QbtcHelper.deriveAddress(mldsaPubKey)
                return Pair(address, mldsaPubKey)
            }

            TssKeyType.ECDSA -> {
                val derivedPublicKey =
                    if (chainPubKey != null) {
                        chainPubKey.publicKey
                    } else {
                        PublicKeyHelper.getDerivedPublicKey(
                            vault.pubKeyECDSA,
                            vault.hexChainCode,
                            chain.coinType.compatibleDerivationPath(),
                        )
                    }
                val publicKey =
                    PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
                if (chain == Chain.MayaChain) {
                    return Pair(MayaChainHelper.getAddress(publicKey), derivedPublicKey)
                } else {
                    val pk =
                        publicKey.takeIf { chain.coinType != CoinType.TRON }
                            ?: publicKey.uncompressed()
                    val address =
                        adjustAddressPrefix(
                            chain.coinType.compatibleType,
                            chain.coinType.compatibleType.deriveAddressFromPublicKey(pk),
                        )
                    return Pair(address, derivedPublicKey)
                }
            }

            TssKeyType.EDDSA -> {
                val eddsaPubKey = chainPubKey?.publicKey ?: vault.pubKeyEDDSA
                validateEddsaPubKey(chain, eddsaPubKey)

                if (chain == Chain.Cardano) {
                    val address = CardanoUtils.createEnterpriseAddress(eddsaPubKey)

                    if (!AnyAddress.isValid(address, CoinType.CARDANO)) {
                        error("WalletCore validation failed for Cardano address: $address")
                    }

                    return Pair(
                        AnyAddress(address, CoinType.CARDANO, "ada").description(),
                        eddsaPubKey,
                    )
                }
                val publicKey = PublicKey(eddsaPubKey.hexToByteArray(), PublicKeyType.ED25519)
                if (chain == Chain.Bittensor) {
                    val rawKey = BittensorHelper.hexToBytes(eddsaPubKey)
                    val address = BittensorHelper.ss58Encode(rawKey)
                    return Pair(address, eddsaPubKey)
                }
                return Pair(chain.coinType.deriveAddressFromPublicKey(publicKey), eddsaPubKey)
            }
        }
    }

    override suspend fun getAddress(coin: Coin, vault: Vault): Pair<String, String> =
        getAddress(coin.chain, vault)

    override fun isValid(chain: Chain, address: String): Boolean =
        when (chain) {
            Chain.MayaChain -> AnyAddress.isValidBech32(address, chain.coinType, "maya")

            Chain.Qbtc -> AnyAddress.isValidBech32(address, CoinType.COSMOS, "qbtc")

            Chain.Sei -> AnyAddress.isValid(address, CoinType.ETHEREUM)

            Chain.Bittensor -> AnyAddress.isValidSS58(address, CoinType.POLKADOT, 42)

            else -> chain.coinType.validate(address)
        }

    override suspend fun validateRecipient(chain: Chain, address: String): RecipientValidity =
        when {
            !isValid(chain, address) -> RecipientValidity.InvalidForChain
            chain != Chain.Solana -> RecipientValidity.Valid
            SolanaProgramDerivedAddress.isWalletAddress(address) -> RecipientValidity.Valid
            else -> solanaOffCurveVerdict(address)
        }

    /**
     * The verdict for a well-formed Solana address that is off the ed25519 curve, which the owning
     * program alone can sign for. That covers both the account this guard exists for — a token
     * account, which strands an SPL transfer — and destinations that are perfectly spendable, so
     * the cluster decides which one this is.
     *
     * A lookup that fails is refused rather than waved through: on the send path the same cluster
     * supplies the blockhash and the fee, so a transaction cannot be built while it is unreachable
     * anyway, and the address book can be retried.
     */
    private suspend fun solanaOffCurveVerdict(address: String): RecipientValidity =
        when (val ownership = solanaApi.getAccountOwnership(address)) {
            is SolanaAccountOwnership.Owned ->
                if (ownership.programId in SOLANA_TOKEN_PROGRAM_IDS) {
                    RecipientValidity.NotAWalletAddress
                } else {
                    RecipientValidity.Valid
                }
            // No account at that address, so it is not a token account. A program vault that only
            // ever holds SPL never needs its own account on-chain: the tokens live in ATAs it owns.
            SolanaAccountOwnership.Missing -> RecipientValidity.Valid
            SolanaAccountOwnership.Unavailable -> RecipientValidity.NotAWalletAddress
        }

    /**
     * For KeyImport vaults, find the chain-specific public key. First tries an exact chain match,
     * then falls back to finding another chain with the same derivation path (e.g., all EVM chains
     * share m/44'/60'/0'/0/0).
     */
    private fun findChainPublicKey(chain: Chain, vault: Vault): ChainPublicKey? {
        if (vault.libType != SigningLibType.KeyImport) return null

        val isEddsa = chain.TssKeysignType == TssKeyType.EDDSA

        // Exact chain match
        val exact =
            vault.chainPublicKeys.firstOrNull { it.chain == chain.raw && it.isEddsa == isEddsa }
        if (exact != null) return exact

        // For ECDSA chains, find another chain with the same derivation path
        if (!isEddsa) {
            val targetDerivePath = chain.coinType.compatibleDerivationPath()
            return vault.chainPublicKeys.firstOrNull { cpk ->
                !cpk.isEddsa &&
                    try {
                        Chain.fromRaw(cpk.chain).coinType.compatibleDerivationPath() ==
                            targetDerivePath
                    } catch (_: Exception) {
                        false
                    }
            }
        }

        return null
    }

    private fun adjustAddressPrefix(type: CoinType, address: String): String =
        if (type == CoinType.BITCOINCASH) {
            address.replace("bitcoincash:", "")
        } else address
}
