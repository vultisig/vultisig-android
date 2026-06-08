package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.chains.helpers.QBTCTransactionHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import java.util.Base64

/**
 * Builds the SignDoc artefacts (bodyBytes, authInfoBytes, chainId, accountNumber) for a Cosmos-SDK
 * staking operation. Consumed by the KeysignPayload builder whenever the user submits a Terra
 * staking flow.
 *
 * Port of iOS `CosmosStakingSignDataResolver.swift` (vultisig-ios PR #4432).
 *
 * Each [CosmosStakingPayload] subtype dispatches to the matching [CosmosStakingHelper] encoder,
 * packs the `Any`-wrapped message(s) into a TxBody, and pairs them with an AuthInfo derived from
 * the chain's [CosmosStakingConfig] entry. Gas + fee scale linearly with the message count for the
 * batched-claim path; single-msg flows collapse to N=1.
 *
 * Validator preflight (bech32 / HRP / 20-byte payload) runs HERE before any SignDoc bytes are
 * produced — so an invalid validator throws at build time and never burns an MPC ceremony on a
 * chain-rejected tx.
 */
object CosmosStakingSignDataResolver {

    /**
     * Hard cap on validators in a single batched withdraw-rewards tx. Mirrors the UI soft cap;
     * enforced again here so the resolver cannot be bypassed by upstream callers wiring payloads
     * directly (e.g. tests, scripted payloads). LUNC's 2M-per-msg gas budget means an 8-validator
     * batch lands at 16M total gas (under columbus-5 block budget); raising the cap would push the
     * batch past block reality.
     */
    const val MAX_BATCH_WITHDRAW_VALIDATORS = 8

    /** Matches a positive integer with no sign, leading zero, or non-digit characters. */
    private val POSITIVE_INTEGER = Regex("^[1-9]\\d*$")

    sealed class ResolverException(message: String) : IllegalArgumentException(message) {
        object MissingChainSpecific :
            ResolverException("Cosmos blockchain-specific data is required")

        object InvalidPublicKey :
            ResolverException(
                "Public key must be a compressed secp256k1 pubkey (33 bytes, 0x02/0x03 prefix)"
            )

        class MissingPayloadField(val field: String) :
            ResolverException("Missing required payload field: $field")

        class InvalidAmount(val field: String) :
            ResolverException("Invalid amount for $field: expected a positive integer")

        class ValidatorPreflightFailed(val reason: String) :
            ResolverException("Validator preflight failed: $reason")

        object SelfRedelegation :
            ResolverException(
                "Source and destination validators must differ (ErrSelfRedelegation)"
            )

        object NoValidatorsToClaim : ResolverException("No validators selected for reward claim")

        class TooManyValidatorsToClaim(val max: Int, val actual: Int) :
            ResolverException("Batched claim exceeds the $max-validator cap (requested $actual)")
    }

    /**
     * Resolves the SignDoc artefacts for a staking payload. Caller passes the immutable [payload],
     * the chain + delegator address + hex public key of the coin being staked, and the Cosmos
     * chain-specific (already populated upstream — sequence + account number).
     *
     * The signing scheme is derived from the chain: secp256k1 for the Terra family, ML-DSA
     * (post-quantum) for QBTC — see [decodePubKeyAndType]. The staking msg bodies and gas/fee are
     * scheme-independent, so only the pubkey decode + AuthInfo type URL differ.
     *
     * Returns a [SignDirectProto] ready to drop into
     * [com.vultisig.wallet.data.models.payload.KeysignPayload.signDirect].
     */
    fun resolve(
        payload: CosmosStakingPayload,
        chain: Chain,
        delegatorAddress: String,
        hexPublicKey: String,
        chainSpecific: BlockChainSpecific,
    ): SignDirectProto {
        if (chainSpecific !is BlockChainSpecific.Cosmos)
            throw ResolverException.MissingChainSpecific

        val (pubKey, pubKeyTypeUrl) = decodePubKeyAndType(chain, hexPublicKey)

        return buildSignDirect(
            payload = payload,
            chain = chain,
            delegatorAddress = delegatorAddress,
            pubKey = pubKey,
            pubKeyTypeUrl = pubKeyTypeUrl,
            chainSpecific = chainSpecific,
        )
    }

    /**
     * Decodes the signer pubkey and its Cosmos `Any` type URL for the chain's signing scheme.
     *
     * QBTC signs with ML-DSA (post-quantum), so its ~1312-byte pubkey can't pass the compressed-
     * secp256k1 33-byte guard and its AuthInfo must carry `/cosmos.crypto.mldsa.PubKey`. The
     * resulting `signDirect` bytes round-trip through the relay proto so the co-signing peer
     * rebuilds the identical SignDoc hash;
     * [com.vultisig.wallet.data.chains.helpers.QBTCTransactionHelper] consumes them directly
     * instead of the secp256k1 / WalletCore path the Terra family feeds. Every other staking chain
     * keeps the strict secp256k1 guard + URL.
     */
    private fun decodePubKeyAndType(chain: Chain, hexPublicKey: String): Pair<ByteArray, String> =
        when (chain.TssKeysignType) {
            TssKeyType.MLDSA ->
                (hexPublicKey.hexToByteArrayOrNull()?.takeIf { it.isNotEmpty() }
                    ?: throw ResolverException.InvalidPublicKey) to
                    QBTCTransactionHelper.PUB_KEY_TYPE_URL
            else ->
                (decodePubKey(hexPublicKey) ?: throw ResolverException.InvalidPublicKey) to
                    CosmosStakingHelper.PUBKEY_TYPE_URL
        }

    /**
     * Shared SignDoc assembly for both signing schemes. The validator preflight, encoder dispatch
     * and gas/fee scaling are pubkey-independent — only the AuthInfo's pubkey `Any` URL differs, so
     * the scheme reaches here via [pubKeyTypeUrl].
     */
    private fun buildSignDirect(
        payload: CosmosStakingPayload,
        chain: Chain,
        delegatorAddress: String,
        pubKey: ByteArray,
        pubKeyTypeUrl: String,
        chainSpecific: BlockChainSpecific.Cosmos,
    ): SignDirectProto {
        val entry = CosmosStakingConfig.entryFor(chain)

        val msgsAny = encodeMessages(payload, chain, delegatorAddress, entry.bondDenom)

        // Linear gas + fee scaling for batched-claim — single-msg flows use N=1 which collapses to
        // the base config values.
        val multiplier = msgsAny.size.coerceAtLeast(1).toLong()
        val gasLimit = entry.gasLimit * multiplier
        val feeAmount = entry.feeAmount * multiplier

        val bodyBytes = CosmosStakingHelper.buildTxBodyMulti(msgsAny, memo = "")
        val authInfoBytes =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = pubKey,
                sequence = chainSpecific.sequence.toLong(),
                gasLimit = gasLimit,
                feeDenom = entry.feeDenom,
                feeAmount = feeAmount,
                pubKeyTypeUrl = pubKeyTypeUrl,
            )

        return SignDirectProto(
            bodyBytes = bodyBytes.base64NoWrap(),
            authInfoBytes = authInfoBytes.base64NoWrap(),
            chainId = entry.chainId,
            accountNumber = chainSpecific.accountNumber.toString(),
        )
    }

    private fun encodeMessages(
        payload: CosmosStakingPayload,
        chain: Chain,
        delegator: String,
        denom: String,
    ): List<ByteArray> =
        when (payload) {
            is CosmosStakingPayload.Delegate ->
                listOf(encodeDelegate(payload, chain, delegator, denom))
            is CosmosStakingPayload.Undelegate ->
                listOf(encodeUndelegate(payload, chain, delegator, denom))
            is CosmosStakingPayload.Redelegate ->
                listOf(encodeRedelegate(payload, chain, delegator, denom))
            is CosmosStakingPayload.WithdrawRewards ->
                encodeWithdrawRewards(payload, chain, delegator)
        }

    private fun encodeDelegate(
        payload: CosmosStakingPayload.Delegate,
        chain: Chain,
        delegator: String,
        denom: String,
    ): ByteArray {
        require(payload.validatorAddress) { "validatorAddress" }
        requirePositiveAmount(payload.amount) { "amount" }
        preflight(payload.validatorAddress, chain)
        return CosmosStakingHelper.encodeDelegate(
            delegator = delegator,
            validator = payload.validatorAddress,
            amount = payload.amount,
            denom = denom,
        )
    }

    private fun encodeUndelegate(
        payload: CosmosStakingPayload.Undelegate,
        chain: Chain,
        delegator: String,
        denom: String,
    ): ByteArray {
        require(payload.validatorAddress) { "validatorAddress" }
        requirePositiveAmount(payload.amount) { "amount" }
        preflight(payload.validatorAddress, chain)
        return CosmosStakingHelper.encodeUndelegate(
            delegator = delegator,
            validator = payload.validatorAddress,
            amount = payload.amount,
            denom = denom,
        )
    }

    private fun encodeRedelegate(
        payload: CosmosStakingPayload.Redelegate,
        chain: Chain,
        delegator: String,
        denom: String,
    ): ByteArray {
        require(payload.validatorSrcAddress) { "validatorSrcAddress" }
        require(payload.validatorDstAddress) { "validatorDstAddress" }
        // cosmos-sdk rejects a same-validator redelegate with ErrSelfRedelegation. The picker
        // excludes the source, but a scripted/programmatic payload can wire equal validators —
        // fail closed here so it never burns an MPC ceremony on a chain-rejected tx.
        if (payload.validatorSrcAddress == payload.validatorDstAddress) {
            throw ResolverException.SelfRedelegation
        }
        requirePositiveAmount(payload.amount) { "amount" }
        preflight(payload.validatorSrcAddress, chain)
        preflight(payload.validatorDstAddress, chain)
        return CosmosStakingHelper.encodeBeginRedelegate(
            delegator = delegator,
            validatorSrc = payload.validatorSrcAddress,
            validatorDst = payload.validatorDstAddress,
            amount = payload.amount,
            denom = denom,
        )
    }

    private fun encodeWithdrawRewards(
        payload: CosmosStakingPayload.WithdrawRewards,
        chain: Chain,
        delegator: String,
    ): List<ByteArray> {
        if (payload.validators.isEmpty()) throw ResolverException.NoValidatorsToClaim
        if (payload.validators.size > MAX_BATCH_WITHDRAW_VALIDATORS) {
            throw ResolverException.TooManyValidatorsToClaim(
                max = MAX_BATCH_WITHDRAW_VALIDATORS,
                actual = payload.validators.size,
            )
        }
        return payload.validators.map { validator ->
            preflight(validator, chain)
            CosmosStakingHelper.encodeWithdrawDelegatorReward(
                delegator = delegator,
                validator = validator,
            )
        }
    }

    private fun preflight(validator: String, chain: Chain) {
        try {
            ValidatorBech32Preflight.validate(validator, chain)
        } catch (e: ValidatorBech32Preflight.ValidatorBech32Exception) {
            throw ResolverException.ValidatorPreflightFailed(
                    e.message ?: "validator bech32 invalid"
                )
                .apply { initCause(e) }
        }
    }

    private fun require(value: String, name: () -> String) {
        if (value.isBlank()) throw ResolverException.MissingPayloadField(name())
    }

    /**
     * Last local preflight for stake amounts. The UI builds base-unit strings, but upstream callers
     * (tests, scripted payloads) can wire a payload directly, so reject anything that is not a
     * trimmed positive integer before it reaches the SignDoc bytes.
     */
    private fun requirePositiveAmount(value: String, name: () -> String) {
        if (!value.trim().matches(POSITIVE_INTEGER)) {
            throw ResolverException.InvalidAmount(name())
        }
    }

    /**
     * A compressed secp256k1 pubkey is 33 bytes with a `0x02` / `0x03` prefix. Reject malformed
     * input up-front — uncompressed or off-curve keys would otherwise burn an MPC ceremony and be
     * rejected on-chain after signing.
     */
    private fun decodePubKey(hex: String): ByteArray? {
        val bytes = hex.hexToByteArrayOrNull() ?: return null
        if (bytes.size != 33) return null
        val prefix = bytes[0].toInt() and 0xFF
        if (prefix != 0x02 && prefix != 0x03) return null
        return bytes
    }

    private fun String.hexToByteArrayOrNull(): ByteArray? {
        val cleaned = removePrefix("0x")
        if (cleaned.length % 2 != 0) return null
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = cleaned[2 * i].digitToIntOrNull(16) ?: return null
            val lo = cleaned[2 * i + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun ByteArray.base64NoWrap(): String = Base64.getEncoder().encodeToString(this)
}
