@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.peer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.QrHelperModalRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.CreateQrCodeSharingBitmapUseCase
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
internal class KeygenPeerDiscoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var generateQrBitmap: GenerateQrBitmap
    private lateinit var compressQr: CompressQrUseCase
    private lateinit var createQrCodeSharingBitmap: CreateQrCodeSharingBitmapUseCase
    private lateinit var generateServiceName: GenerateServiceName
    private lateinit var discoverParticipants: DiscoverParticipantsUseCase
    private lateinit var generateServerPartyId: GenerateServerPartyId
    private lateinit var secretSettingsRepository: SecretSettingsRepository
    private lateinit var vultiSignerRepository: VultiSignerRepository
    private lateinit var featureFlagRepository: FeatureFlagRepository
    private lateinit var qrHelperModalRepository: QrHelperModalRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var extractMasterKeys: ExtractMasterKeysUseCase
    private lateinit var protoBuf: ProtoBuf
    private lateinit var sessionApi: SessionApi
    private lateinit var networkUtils: NetworkUtils

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = mockk(relaxed = true)
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        generateQrBitmap = mockk(relaxed = true)
        compressQr = mockk(relaxed = true)
        createQrCodeSharingBitmap = mockk(relaxed = true)
        generateServiceName = mockk(relaxed = true)
        discoverParticipants = mockk(relaxed = true)
        generateServerPartyId = mockk(relaxed = true)
        secretSettingsRepository = mockk(relaxed = true)
        vultiSignerRepository = mockk(relaxed = true)
        featureFlagRepository = mockk(relaxed = true)
        qrHelperModalRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        extractMasterKeys = mockk(relaxed = true)
        protoBuf = ProtoBuf
        sessionApi = mockk(relaxed = true)
        networkUtils = mockk(relaxed = true)

        mockkStatic("androidx.navigation.SavedStateHandleKt")
        mockkObject(Utils)

        every { Utils.deviceName(any()) } returns "test-device"
        every { generateServiceName() } returns "test-service"

        // Return false so init -> loadData() short-circuits without network setup
        every { networkUtils.isNetworkAvailable() } returns false
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        unmockkObject(Utils)
    }

    private fun stubRoute(deviceCount: Int? = null) {
        every { any<SavedStateHandle>().toRoute<Route.Keygen.PeerDiscovery>() } returns
            Route.Keygen.PeerDiscovery(
                action = TssAction.KEYGEN,
                vaultName = "Test Vault",
                deviceCount = deviceCount,
            )
    }

    private fun createViewModel(): KeygenPeerDiscoveryViewModel =
        KeygenPeerDiscoveryViewModel(
            savedStateHandle = savedStateHandle,
            context = context,
            navigator = navigator,
            generateQrBitmap = generateQrBitmap,
            compressQr = compressQr,
            createQrCodeSharingBitmap = createQrCodeSharingBitmap,
            generateServiceName = generateServiceName,
            discoverParticipants = discoverParticipants,
            generateServerPartyId = generateServerPartyId,
            secretSettingsRepository = secretSettingsRepository,
            vultiSignerRepository = vultiSignerRepository,
            featureFlagRepository = featureFlagRepository,
            qrHelperModalRepository = qrHelperModalRepository,
            vaultRepository = vaultRepository,
            keyImportRepository = keyImportRepository,
            extractMasterKeys = extractMasterKeys,
            protoBuf = protoBuf,
            sessionApi = sessionApi,
            networkUtils = networkUtils,
        )

    /**
     * Simulates the auto-selection logic from
     * [KeygenPeerDiscoveryViewModel.startParticipantDiscovery]. This is the exact same algorithm
     * used in the ViewModel when new devices are discovered.
     */
    private fun simulateAutoDiscovery(
        vm: KeygenPeerDiscoveryViewModel,
        discoveredDevices: List<String>,
    ) {
        val currentState = vm.state.value
        val existingDevices = currentState.devices.toSet()
        val newDevices = discoveredDevices - existingDevices

        val maxOtherDevices = currentState.minimumDevices - 1
        val remainingSlots = maxOtherDevices - currentState.selectedDevices.size
        val devicesToAutoSelect = newDevices.take(remainingSlots.coerceAtLeast(0))
        val selectedDevices = currentState.selectedDevices.toSet() + devicesToAutoSelect

        vm.state.update {
            it.copy(devices = discoveredDevices, selectedDevices = selectedDevices.toList())
        }
    }

    // --- selectDevice tests ---

    @Test
    fun `selectDevice adds device when under limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // minimumDevices = 3, so maxOtherDevices = 2
        vm.selectDevice("device-A")

        assertTrue(vm.state.value.selectedDevices.contains("device-A"))
        assertEquals(1, vm.state.value.selectedDevices.size)
    }

    @Test
    fun `selectDevice adds multiple devices up to limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // minimumDevices = 3, maxOtherDevices = 2
        vm.selectDevice("device-A")
        vm.selectDevice("device-B")

        assertEquals(listOf("device-A", "device-B"), vm.state.value.selectedDevices)
    }

    @Test
    fun `selectDevice rejects device when at limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // minimumDevices = 3, maxOtherDevices = 2
        vm.selectDevice("device-A")
        vm.selectDevice("device-B")
        // Already at limit (2 selected), third should be rejected
        vm.selectDevice("device-C")

        assertEquals(2, vm.state.value.selectedDevices.size)
        assertFalse(vm.state.value.selectedDevices.contains("device-C"))
    }

    @Test
    fun `selectDevice deselects device when already selected`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        vm.selectDevice("device-A")
        vm.selectDevice("device-B")
        assertEquals(2, vm.state.value.selectedDevices.size)

        // Deselect device-A
        vm.selectDevice("device-A")

        assertEquals(1, vm.state.value.selectedDevices.size)
        assertFalse(vm.state.value.selectedDevices.contains("device-A"))
        assertTrue(vm.state.value.selectedDevices.contains("device-B"))
    }

    @Test
    fun `selectDevice deselection works when at limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        vm.selectDevice("device-A")
        vm.selectDevice("device-B")
        assertEquals(2, vm.state.value.selectedDevices.size)

        // Deselect at limit should still work
        vm.selectDevice("device-B")

        assertEquals(1, vm.state.value.selectedDevices.size)
        assertTrue(vm.state.value.selectedDevices.contains("device-A"))
    }

    @Test
    fun `selectDevice works with default minimumDevices of 2`() {
        stubRoute(deviceCount = null) // defaults to MIN_KEYGEN_DEVICES = 2
        val vm = createViewModel()

        // minimumDevices = 2, maxOtherDevices = 1
        vm.selectDevice("device-A")
        assertEquals(1, vm.state.value.selectedDevices.size)

        // Should reject second device
        vm.selectDevice("device-B")
        assertEquals(1, vm.state.value.selectedDevices.size)
        assertFalse(vm.state.value.selectedDevices.contains("device-B"))
    }

    @Test
    fun `selectDevice allows re-select after deselect at limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        vm.selectDevice("device-A")
        vm.selectDevice("device-B")
        assertEquals(2, vm.state.value.selectedDevices.size)

        // Deselect one
        vm.selectDevice("device-A")
        assertEquals(1, vm.state.value.selectedDevices.size)

        // Now can add a new one
        vm.selectDevice("device-C")
        assertEquals(2, vm.state.value.selectedDevices.size)
        assertTrue(vm.state.value.selectedDevices.contains("device-B"))
        assertTrue(vm.state.value.selectedDevices.contains("device-C"))
    }

    // --- Auto-selection logic tests (simulating startParticipantDiscovery) ---

    @Test
    fun `auto-discovery caps selected devices to minimumDevices minus 1`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // Simulate 5 devices discovered at once
        simulateAutoDiscovery(vm, listOf("d1", "d2", "d3", "d4", "d5"))

        // minimumDevices = 3, so maxOtherDevices = 2
        assertEquals(2, vm.state.value.selectedDevices.size)
        assertEquals(listOf("d1", "d2"), vm.state.value.selectedDevices)
        // All 5 should be visible as available devices
        assertEquals(5, vm.state.value.devices.size)
    }

    @Test
    fun `auto-discovery with default 2 device limit selects only 1`() {
        stubRoute(deviceCount = null) // defaults to 2
        val vm = createViewModel()

        simulateAutoDiscovery(vm, listOf("d1", "d2", "d3"))

        // minimumDevices = 2, maxOtherDevices = 1
        assertEquals(1, vm.state.value.selectedDevices.size)
        assertEquals(listOf("d1"), vm.state.value.selectedDevices)
    }

    @Test
    fun `auto-discovery with 4 device limit selects up to 3`() {
        stubRoute(deviceCount = 4)
        val vm = createViewModel()

        simulateAutoDiscovery(vm, listOf("d1", "d2", "d3", "d4", "d5"))

        // minimumDevices = 4, maxOtherDevices = 3
        assertEquals(3, vm.state.value.selectedDevices.size)
        assertEquals(listOf("d1", "d2", "d3"), vm.state.value.selectedDevices)
    }

    @Test
    fun `auto-discovery does not exceed limit when devices arrive in batches`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // Simulate devices arriving incrementally (each call is a new emission)
        simulateAutoDiscovery(vm, listOf("d1"))
        assertEquals(1, vm.state.value.selectedDevices.size)

        simulateAutoDiscovery(vm, listOf("d1", "d2"))
        assertEquals(2, vm.state.value.selectedDevices.size)

        // Third and fourth devices should not be auto-selected (limit is 2)
        simulateAutoDiscovery(vm, listOf("d1", "d2", "d3"))
        assertEquals(2, vm.state.value.selectedDevices.size)

        simulateAutoDiscovery(vm, listOf("d1", "d2", "d3", "d4"))
        assertEquals(2, vm.state.value.selectedDevices.size)

        assertEquals(listOf("d1", "d2"), vm.state.value.selectedDevices)
        assertEquals(4, vm.state.value.devices.size)
    }

    @Test
    fun `auto-discovery respects already manually selected devices`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // Manually select one device first
        vm.selectDevice("d1")
        assertEquals(1, vm.state.value.selectedDevices.size)

        // Now simulate discovery finding more devices
        // Since d1 is already selected, only 1 more slot available
        simulateAutoDiscovery(vm, listOf("d2", "d3", "d4"))

        // Should auto-select only 1 more (d2), not all 3
        assertEquals(2, vm.state.value.selectedDevices.size)
        assertTrue(vm.state.value.selectedDevices.contains("d1"))
        assertTrue(vm.state.value.selectedDevices.contains("d2"))
        assertFalse(vm.state.value.selectedDevices.contains("d3"))
    }

    @Test
    fun `auto-discovery does not select when already at limit`() {
        stubRoute(deviceCount = 3)
        val vm = createViewModel()

        // Manually fill to limit
        vm.selectDevice("d1")
        vm.selectDevice("d2")
        assertEquals(2, vm.state.value.selectedDevices.size)

        // Simulate discovery of new devices
        simulateAutoDiscovery(vm, listOf("d3", "d4", "d5"))

        // No new devices should be auto-selected
        assertEquals(2, vm.state.value.selectedDevices.size)
        assertTrue(vm.state.value.selectedDevices.contains("d1"))
        assertTrue(vm.state.value.selectedDevices.contains("d2"))
    }

    // --- Crash path tests ---

    @Test
    fun `when toRoute throws, init navigates back`() {
        every { any<SavedStateHandle>().toRoute<Route.Keygen.PeerDiscovery>() } throws
            IllegalStateException("deserialization failed")

        createViewModel()

        coVerify { navigator.navigate(Destination.Back) }
    }

    @Test
    fun `tryAgain does nothing when args is null`() {
        every { any<SavedStateHandle>().toRoute<Route.Keygen.PeerDiscovery>() } throws
            IllegalStateException("deserialization failed")

        val vm = createViewModel()
        // args is null; tryAgain should be a no-op and not crash
        vm.tryAgain()

        // navigate(Back) called exactly once (from init), not a second time from tryAgain
        coVerify(exactly = 1) { navigator.navigate(Destination.Back) }
    }
}
