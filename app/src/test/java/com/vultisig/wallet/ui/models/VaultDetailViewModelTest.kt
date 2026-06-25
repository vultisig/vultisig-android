@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.usecases.ShareBitmapUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class VaultDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var shareBitmap: ShareBitmapUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Details>() } returns
            Route.Details(vaultId = VAULT_ID)
        vaultRepository = mockk(relaxed = true)
        shareBitmap = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes the MLDSA public key when the vault has one`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns vault(pubKeyMLDSA = MLDSA_KEY)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiModel.value.pubKeyMLDSA shouldBe MLDSA_KEY
        }

    @Test
    fun `exposes an empty MLDSA public key when the vault has none`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns vault(pubKeyMLDSA = "")

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiModel.value.pubKeyMLDSA shouldBe ""
        }

    @Test
    fun `keeps exposing the ECDSA and EdDSA keys`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns vault(pubKeyMLDSA = MLDSA_KEY)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiModel.value.pubKeyECDSA shouldBe ECDSA_KEY
            vm.uiModel.value.pubKeyEDDSA shouldBe EDDSA_KEY
        }

    private fun createViewModel() =
        VaultDetailViewModel(
            savedStateHandle = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            shareBitmap = shareBitmap,
        )

    private fun vault(pubKeyMLDSA: String) =
        Vault(
            id = VAULT_ID,
            name = "Test Vault",
            pubKeyECDSA = ECDSA_KEY,
            pubKeyEDDSA = EDDSA_KEY,
            pubKeyMLDSA = pubKeyMLDSA,
            signers = listOf("iPhone", "Server-1"),
            localPartyID = "iPhone",
        )

    private companion object {
        private const val VAULT_ID = "test-vault-id"
        private const val ECDSA_KEY = "ecdsa-public-key"
        private const val EDDSA_KEY = "eddsa-public-key"
        private const val MLDSA_KEY = "mldsa-public-key"
    }
}
