package com.vultisig.wallet.data.usecases

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class DepositMemoAssetsValidatorUseCaseImplTest {

    private val validator = DepositMemoAssetsValidatorUseCaseImpl()

    @Test
    fun `isValidAsset returns true for valid assets`() {
        assertTrue(validator.invoke("ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7"))
        assertTrue(validator.invoke("ARB.GLD-0XAFD091F140C21770F4E5D53D26B2859AE97555AA"))
        assertTrue(validator.invoke("ARB.PEPE-0X25D887CE7A35172C62FEBFD67A1856F20FAEBB00"))
        assertTrue(validator.invoke("KUJI.USK"))
        assertTrue(validator.invoke("ARB.USDT-0XFD086BC7CD5C481DCC9C85EBE478A1C0B69FCBB9"))
    }

    @Test
    fun `isValidAsset returns false for empty input`() {
        assertFalse(validator.invoke(""))
        assertFalse(validator.invoke(" "))
    }

    @Test
    fun `isValidAsset returns false for invalid chain name`() {
        assertFalse(validator.invoke("AB.USDC")) // Chain name too short
        assertFalse(validator.invoke("ABCDEFGHIJKLMN.USDC")) // Chain name too long
        assertFalse(validator.invoke("123.USDC")) // Chain name contains numbers
        assertFalse(validator.invoke("ETH-.USDC")) // Chain name contains invalid characters
    }

    @Test
    fun `isValidAsset returns false for invalid asset info`() {
        assertFalse(validator.invoke("ETH.")) // Asset info missing
        assertFalse(validator.invoke("ETH.USDT-")) // Asset address missing after hyphen
        assertFalse(validator.invoke("ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7-extra")) // Extra characters after asset address
    }

    @Test
    fun `isValidAsset returns false for boundary conditions`() {
        assertTrue(validator.invoke("ABC.USDC")) // Chain name with minimum length
        assertTrue(validator.invoke("ABCDEFGHIJ.USDC")) // Chain name with maximum length
        assertTrue(validator.invoke("ETH.A")) // Asset info with minimum length
        assertTrue(validator.invoke("ETH.ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")) // Asset info with maximum length
    }

    @Test
    fun `isValidAsset returns false for unexpected input`() {
        assertFalse(validator.invoke("ETH.USDC.extra")) // More than two parts after splitting by dot
        assertFalse(validator.invoke("ETH..USDC")) // Consecutive dots
        assertFalse(validator.invoke("ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7.")) // Trailing dot
    }
}