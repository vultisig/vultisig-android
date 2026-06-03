package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * Black-box behavioral spec for [ValidatorBech32Preflight.validate]. Mirrors iOS
 * `ValidatorBech32PreflightTests.swift`. Tests assert acceptance / rejection only — the preflight
 * wraps Trust Wallet Core's bech32 + 20-byte Cosmos address validation, so internal error
 * discriminants are not pinned.
 */
class ValidatorBech32PreflightTests {

    // MARK: - Empty / structural

    @Test
    fun `empty address is rejected`() {
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception.Empty> {
            ValidatorBech32Preflight.validate("", Chain.Terra)
        }
    }

    @Test
    fun `garbage string is rejected`() {
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate("not bech32 at all!!", Chain.Terra)
        }
    }

    @Test
    fun `string without separator is rejected`() {
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate("terravaloperabcdefghij", Chain.Terra)
        }
    }

    @Test
    fun `mixed case address is rejected`() {
        // BIP-173 forbids mixed case. A Terra address pasted from a misformatted email signature
        // must not slip through.
        val mixed = makeValidValoperAddress().uppercasedFirstHalf()
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate(mixed, Chain.Terra)
        }
    }

    // MARK: - Prefix

    @Test
    fun `wrong prefix is rejected for Terra`() {
        // `terra1…` is a delegator account address; `terravaloper1…` is the operator address. The
        // valoper form is what x/staking expects; accidentally using the account form would burn
        // an MPC ceremony.
        val address = makeAddress(hrp = "terra", payloadLength = 20)
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate(address, Chain.Terra)
        }
    }

    @Test
    fun `cosmoshub valoper rejected on Terra chain`() {
        // Cosmoshub valoper would pass a generic `cosmos` prefix check — the per-chain HRP guard
        // is the only thing that catches it.
        val address = makeAddress(hrp = "cosmosvaloper", payloadLength = 20)
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate(address, Chain.Terra)
        }
    }

    // MARK: - Payload length

    @Test
    fun `32-byte consensus payload is rejected`() {
        // valconspub1… consensus pubkeys are 32 bytes wrapped in the same bech32 envelope. The HRP
        // differs in production, but a malicious submitter could spoof the valoper HRP — the
        // 20-byte Cosmos-address guard catches that.
        val address = makeAddress(hrp = "terravaloper", payloadLength = 32)
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate(address, Chain.Terra)
        }
    }

    // MARK: - Happy path

    @Test
    fun `valid 20-byte terravaloper passes on Terra`() {
        val address = makeAddress(hrp = "terravaloper", payloadLength = 20)
        ValidatorBech32Preflight.validate(address, Chain.Terra)
    }

    @Test
    fun `valid 20-byte terravaloper also passes on TerraClassic`() {
        // LUNC shares the `terravaloper` HRP with LUNA — both chains pass the same address.
        val address = makeAddress(hrp = "terravaloper", payloadLength = 20)
        ValidatorBech32Preflight.validate(address, Chain.TerraClassic)
    }

    @Test
    fun `flipped checksum byte is rejected`() {
        // The last char of a valid bech32 address is part of the checksum. Flipping it must be
        // rejected.
        val original = makeAddress(hrp = "terravaloper", payloadLength = 20)
        val charBefore = original.last()
        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val oldIndex = charset.indexOf(charBefore).takeIf { it >= 0 } ?: 0
        val nextIndex = (oldIndex + 1) % charset.length
        val flipped = original.dropLast(1) + charset[nextIndex]
        assertFailsWith<ValidatorBech32Preflight.ValidatorBech32Exception> {
            ValidatorBech32Preflight.validate(flipped, Chain.Terra)
        }
    }

    // MARK: - Helpers

    private fun makeValidValoperAddress(): String = makeAddress("terravaloper", 20)

    private fun makeAddress(hrp: String, payloadLength: Int): String {
        // Deterministic payload so tests are reproducible.
        val payload = ByteArray(payloadLength) { (it and 0xFF).toByte() }
        return Bech32TestEncoder.encode(hrp, payload)
    }

    private fun String.uppercasedFirstHalf(): String {
        val mid = length / 2
        return substring(0, mid).uppercase() + substring(mid)
    }
}
