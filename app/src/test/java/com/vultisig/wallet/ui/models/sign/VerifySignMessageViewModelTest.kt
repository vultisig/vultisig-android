package com.vultisig.wallet.ui.models.sign

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.repositories.CustomMessagePayloadDto
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

@OptIn(ExperimentalCoroutinesApi::class)
internal class VerifySignMessageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val customMessagePayloadRepo: CustomMessagePayloadRepo = mockk(relaxed = true)
    private val vaultPasswordRepository: VaultPasswordRepository = mockk(relaxed = true)
    private val launchKeysign: LaunchKeysignUseCase = mockk(relaxed = true)
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { isVaultHasFastSignById(any()) } returns false
        coEvery { vaultPasswordRepository.getPassword(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing payload navigates back and leaves the sign message model empty`() = runTest {
        coEvery { customMessagePayloadRepo.get(TX_ID) } returns null

        val viewModel = createViewModel()

        coVerify(exactly = 1) { navigator.back() }
        assertEquals("", viewModel.state.value.model.method)
        assertEquals("", viewModel.state.value.model.message)
    }

    @Test
    fun `present payload populates method and message and does not navigate back`() = runTest {
        val payload =
            mockk<CustomMessagePayload>(relaxed = true) {
                every { method } returns "personal_sign"
                every { message } returns "hello"
            }
        coEvery { customMessagePayloadRepo.get(TX_ID) } returns
            CustomMessagePayloadDto(id = TX_ID, vaultId = VAULT_ID, payload = payload)

        val viewModel = createViewModel()

        assertEquals("personal_sign", viewModel.state.value.model.method)
        assertEquals("hello", viewModel.state.value.model.message)
        coVerify(exactly = 0) { navigator.back() }
    }

    private fun createViewModel(): VerifySignMessageViewModel =
        VerifySignMessageViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(SendDst.ARG_TRANSACTION_ID to TX_ID, SendDst.ARG_VAULT_ID to VAULT_ID)
                ),
            customMessagePayloadRepo = customMessagePayloadRepo,
            vaultPasswordRepository = vaultPasswordRepository,
            launchKeysignUseCase = launchKeysign,
            isVaultHasFastSignById = isVaultHasFastSignById,
            navigator = navigator,
        )

    private companion object {
        const val TX_ID = "tx-1"
        const val VAULT_ID = "vault-1"
    }
}
