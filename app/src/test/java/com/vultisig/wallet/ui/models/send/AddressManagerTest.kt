@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AddressManagerTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val addressParserRepository: AddressParserRepository = mockk(relaxed = true)

    private val requestAddressBookEntry: RequestAddressBookEntryUseCase = mockk(relaxed = true)

    private val addressFieldState = TextFieldState()
    private val providerBondFieldState = TextFieldState()
    private val destinationTagFieldState = TextFieldState()
    private val selectedToken = MutableStateFlow<Coin?>(null)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isDstAddressComplete tracks non-blank field text`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)
            manager.start()
            advanceUntilIdle()

            manager.isDstAddressComplete.value.shouldBeFalse()

            addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            manager.isDstAddressComplete.value.shouldBeTrue()

            addressFieldState.setTextAndPlaceCursorAtEnd("   ")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            manager.isDstAddressComplete.value.shouldBeFalse()
        }

    @Test
    fun `setOutputAddress writes to the field state`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)

            manager.setOutputAddress("0xdeadbeef")

            addressFieldState.text.toString() shouldBe "0xdeadbeef"
        }

    @Test
    fun `valid address sets resolvedDstAddress and emits onAddressValidated`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "0xabc") } returns true
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            val emissions = collectValidations(manager)

            addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value shouldBe "0xabc"
            manager.dstAddressLabel.value.shouldBeNull()
            emissions shouldHaveSize 1
        }

    @Test
    fun `repeated valid input does not clear an existing dstAddressLabel`() =
        runTest(mainDispatcher) {
            // First: ENS resolves "vitalik.eth" -> "0xres", label is set to "vitalik.eth".
            // Then the resolved value flows through again as a valid address — the label
            // must survive (the user did not type a new raw address).
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "vitalik.eth") } returns
                false
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "0xres") } returns true
            coEvery { addressParserRepository.resolveName("vitalik.eth", Chain.Ethereum) } returns
                "0xres"

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            addressFieldState.setTextAndPlaceCursorAtEnd("vitalik.eth")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value shouldBe "0xres"
            manager.dstAddressLabel.value shouldBe "vitalik.eth"

            addressFieldState.setTextAndPlaceCursorAtEnd("0xres")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.dstAddressLabel.value shouldBe "vitalik.eth"
        }

    @Test
    fun `non-empty invalid input attempts ENS resolution and rewrites the field`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "vitalik.eth") } returns
                false
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "0xres") } returns true
            coEvery { addressParserRepository.resolveName("vitalik.eth", Chain.Ethereum) } returns
                "0xres"
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            addressFieldState.setTextAndPlaceCursorAtEnd("vitalik.eth")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value shouldBe "0xres"
            manager.dstAddressLabel.value shouldBe "vitalik.eth"
            addressFieldState.text.toString() shouldBe "0xres"
        }

    @Test
    fun `ENS resolving to invalid address clears state`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "bad.eth") } returns false
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "garbage") } returns false
            coEvery { addressParserRepository.resolveName("bad.eth", Chain.Ethereum) } returns
                "garbage"
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            addressFieldState.setTextAndPlaceCursorAtEnd("bad.eth")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value.shouldBeNull()
            manager.dstAddressLabel.value.shouldBeNull()
        }

    @Test
    fun `empty input clears resolved state`() =
        runTest(mainDispatcher) {
            // Seed a real resolved state first, otherwise asserting "null after empty" would
            // pass even if the clear path regressed (state would already be null).
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "vitalik.eth") } returns
                false
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "0xres") } returns true
            coEvery { addressParserRepository.resolveName("vitalik.eth", Chain.Ethereum) } returns
                "0xres"
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            addressFieldState.setTextAndPlaceCursorAtEnd("vitalik.eth")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            // Confirm we actually populated state before testing the clear.
            manager.resolvedDstAddress.value shouldBe "0xres"
            manager.dstAddressLabel.value shouldBe "vitalik.eth"

            addressFieldState.setTextAndPlaceCursorAtEnd("")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value.shouldBeNull()
            manager.dstAddressLabel.value.shouldBeNull()
        }

    @Test
    fun `resolveName throwing leaves state cleared and does not crash`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, "explode.eth") } returns
                false
            coEvery { addressParserRepository.resolveName("explode.eth", Chain.Ethereum) } throws
                IllegalStateException("rpc down")
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            addressFieldState.setTextAndPlaceCursorAtEnd("explode.eth")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.resolvedDstAddress.value.shouldBeNull()
            manager.dstAddressLabel.value.shouldBeNull()

            coVerify { addressParserRepository.resolveName("explode.eth", Chain.Ethereum) }
        }

    @Test
    fun `onAddressValidated does not emit while no token is selected`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns true
            val manager = build(backgroundScope)
            manager.start()
            // No token selected — combine never emits, handleAddressInput never runs.

            val emissions = collectValidations(manager)

            addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            emissions.shouldBeEmpty()
        }

    private fun TestScope.collectValidations(manager: AddressManager): MutableList<Unit> {
        val emissions = mutableListOf<Unit>()
        backgroundScope.launch { manager.onAddressValidated.collect { emissions += Unit } }
        return emissions
    }

    private fun build(scope: CoroutineScope) =
        AddressManager(
            scope = scope,
            addressFieldState = addressFieldState,
            providerBondFieldState = providerBondFieldState,
            destinationTagFieldState = destinationTagFieldState,
            selectedToken = selectedToken,
            chainAccountAddressRepository = chainAccountAddressRepository,
            addressParserRepository = addressParserRepository,
            requestAddressBookEntry = requestAddressBookEntry,
            vaultIdProvider = { null },
            checkIfTokenSelectionRequired = { _, _ -> },
        )

    private fun ethToken(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xself",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun xrpToken(): Coin =
        Coin(
            chain = Chain.Ripple,
            ticker = "XRP",
            logo = "",
            address = "rSelf",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "ripple",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `pasting a tagged X-address normalizes the address and locks the tag`() =
        runTest(mainDispatcher) {
            val xAddress = "X7AcgcsBL6XDcUb289X4mJ8djcdyKaGZMhc9YTE92ehJ2Fu"
            val classic = "r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59"
            every { chainAccountAddressRepository.isValid(Chain.Ripple, classic) } returns true
            every { chainAccountAddressRepository.isValid(Chain.Ripple, xAddress) } returns false

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = xrpToken()

            addressFieldState.setTextAndPlaceCursorAtEnd(xAddress)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            addressFieldState.text.toString() shouldBe classic
            destinationTagFieldState.text.toString() shouldBe "1"
            manager.destinationTagLocked.value.shouldBeTrue()
            manager.resolvedDstAddress.value shouldBe classic
            manager.dstAddressLabel.value shouldBe xAddress
        }

    @Test
    fun `pasting a no-tag X-address normalizes but leaves the tag editable`() =
        runTest(mainDispatcher) {
            val xAddress = "X7AcgcsBL6XDcUb289X4mJ8djcdyKaB5hJDWMArnXr61cqZ"
            val classic = "r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59"
            every { chainAccountAddressRepository.isValid(Chain.Ripple, classic) } returns true

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = xrpToken()

            addressFieldState.setTextAndPlaceCursorAtEnd(xAddress)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            addressFieldState.text.toString() shouldBe classic
            destinationTagFieldState.text.toString() shouldBe ""
            manager.destinationTagLocked.value.shouldBeFalse()
        }

    @Test
    fun `replacing a normalized X-address releases the lock and drops the derived tag`() =
        runTest(mainDispatcher) {
            val xAddress = "X7AcgcsBL6XDcUb289X4mJ8djcdyKaGZMhc9YTE92ehJ2Fu"
            val classic = "r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59"
            val other = "rOtherClassicAddress"
            every { chainAccountAddressRepository.isValid(Chain.Ripple, classic) } returns true
            every { chainAccountAddressRepository.isValid(Chain.Ripple, other) } returns true

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = xrpToken()

            addressFieldState.setTextAndPlaceCursorAtEnd(xAddress)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()
            manager.destinationTagLocked.value.shouldBeTrue()

            addressFieldState.setTextAndPlaceCursorAtEnd(other)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            manager.destinationTagLocked.value.shouldBeFalse()
            destinationTagFieldState.text.toString() shouldBe ""
        }

    @Test
    fun `replacing a tagged X-address with an untagged one drops the derived tag`() =
        runTest(mainDispatcher) {
            val tagged = "X7AcgcsBL6XDcUb289X4mJ8djcdyKaGZMhc9YTE92ehJ2Fu"
            val untagged = "X7AcgcsBL6XDcUb289X4mJ8djcdyKaB5hJDWMArnXr61cqZ"
            val classic = "r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59"
            every { chainAccountAddressRepository.isValid(Chain.Ripple, classic) } returns true

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = xrpToken()

            addressFieldState.setTextAndPlaceCursorAtEnd(tagged)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()
            destinationTagFieldState.text.toString() shouldBe "1"
            manager.destinationTagLocked.value.shouldBeTrue()

            // Paste an untagged X-address: the tag the previous X-address derived must not persist.
            addressFieldState.setTextAndPlaceCursorAtEnd(untagged)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            destinationTagFieldState.text.toString() shouldBe ""
            manager.destinationTagLocked.value.shouldBeFalse()
        }
}
