package com.vultisig.wallet.data.usecases

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class IsAssetsValidUseCaseImplTest {

    private val isAssetsValidUseCase = IsAssetsValidUseCaseImpl()

    @Test
    operator fun invoke() {
        assertTrue(isAssetsValidUseCase("BNB.BNB-0x"))
        assertTrue(isAssetsValidUseCase("BNB.BNB"))
        assertTrue(isAssetsValidUseCase("BNB.B"))
        assertFalse(isAssetsValidUseCase("BNB."))
        assertFalse(isAssetsValidUseCase("BNB-"))
        assertFalse(isAssetsValidUseCase("BNB"))
        assertFalse(isAssetsValidUseCase("BN"))
        assertFalse(isAssetsValidUseCase("BN."))
        assertFalse(isAssetsValidUseCase("."))
        assertFalse(isAssetsValidUseCase("-"))
        assertFalse(isAssetsValidUseCase(""))
    }
}