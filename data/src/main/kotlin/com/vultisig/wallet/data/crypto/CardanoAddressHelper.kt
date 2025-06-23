package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.Bech32
import wallet.core.jni.Hash

object CardanoAddressHelper {

    fun createExtendedKey(spendingKeyHex: String, chainCodeHex: String): ByteArray {
        val spendingKeyData = Numeric.hexStringToByteArray(spendingKeyHex)
        val chainCodeData = Numeric.hexStringToByteArray(chainCodeHex)
        if (spendingKeyData.size != 32) {
            error("spending key must be 32 bytes, got ${spendingKeyData.size}")
        }
        if (chainCodeData.size != 32) {
            error("chain code must be 32 bytes, got ${chainCodeData.size}")
        }

        val extendedKeyData = ByteArray(128)
        System.arraycopy(
            spendingKeyData,
            0,
            extendedKeyData,
            0,
            32
        )
        System.arraycopy(
            spendingKeyData,
            0,
            extendedKeyData,
            32,
            32
        )
        System.arraycopy(
            chainCodeData,
            0,
            extendedKeyData,
            64,
            32
        )
        System.arraycopy(
            chainCodeData,
            0,
            extendedKeyData,
            96,
            32
        )

        if (extendedKeyData.size != 128) {
            error("extended key must be 128 bytes, got ${extendedKeyData.size}")
        }

        return extendedKeyData
    }


    fun createEnterpriseAddress(spendingKeyHex: String): String {
        val spendingKeyData = Numeric.hexStringToByteArray(spendingKeyHex)

        if (spendingKeyData.size != 32) {
            error("spending key must be 32 bytes, got ${spendingKeyData.size}")
        }

        // Use Blake2b hash with 28 bytes output size
        val hash = Hash.blake2b(
            spendingKeyData,
            28
        )

        // Create Enterprise address data: first byte (0x61) + 28-byte hash
        // 0x61 = (Kind_Enterprise << 4) + Network_Production = (6 << 4) + 1 = 0x61
        val addressData = ByteArray(29)
        addressData[0] = 0x61.toByte() // Enterprise address on Production network
        System.arraycopy(
            hash,
            0,
            addressData,
            1,
            28
        )

        // Convert to bech32 format with "addr" prefix
        return Bech32.encode(
            "addr",
            addressData
        )
    }
}