package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.models.v3.onboarding.StepState
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SendStepRulesTest {

    private val fresh = SendFormUiModel(expandedSection = SendSections.Asset)

    @Test
    fun `a fresh form can only stay on Asset`() {
        fresh.canNavigate(SendSections.Asset) shouldBe false
        fresh.canNavigate(SendSections.Address) shouldBe false
        fresh.canNavigate(SendSections.Amount) shouldBe false
    }

    @Test
    fun `Asset is always reachable from another step`() {
        val onAddress = fresh.copy(expandedSection = SendSections.Address)
        onAddress.canNavigate(SendSections.Asset) shouldBe true
    }

    @Test
    fun `Address opens once the asset step is complete`() {
        val chosen = fresh.copy(isAssetStepComplete = true)
        chosen.canNavigate(SendSections.Address) shouldBe true
        chosen.canNavigate(SendSections.Amount) shouldBe false
    }

    @Test
    fun `Amount needs a valid recipient as well`() {
        val valid =
            fresh.copy(
                expandedSection = SendSections.Address,
                isAssetStepComplete = true,
                isDstAddressValid = true,
            )
        valid.canNavigate(SendSections.Amount) shouldBe true
        valid.copy(isDstAddressValid = false).canNavigate(SendSections.Amount) shouldBe false
    }

    @Test
    fun `tapping the step on screen does nothing`() {
        val onAmount =
            fresh.copy(
                expandedSection = SendSections.Amount,
                isAssetStepComplete = true,
                isDstAddressValid = true,
            )
        onAmount.canNavigate(SendSections.Amount) shouldBe false
    }

    @Test
    fun `step states follow the shown step and what is done`() {
        val onAmount =
            fresh.copy(
                expandedSection = SendSections.Amount,
                isAssetStepComplete = true,
                isDstAddressValid = true,
            )
        onAmount.stepState(SendSections.Asset) shouldBe StepState.Done
        onAmount.stepState(SendSections.Address) shouldBe StepState.Done
        onAmount.stepState(SendSections.Amount) shouldBe StepState.InProgress

        val backOnAsset = onAmount.copy(expandedSection = SendSections.Asset)
        backOnAsset.stepState(SendSections.Asset) shouldBe StepState.InProgress
        backOnAsset.stepState(SendSections.Amount) shouldBe StepState.Inactive
    }

    @Test
    fun `an invalid recipient leaves Address not done`() {
        val state = fresh.copy(expandedSection = SendSections.Asset, isAssetStepComplete = true)
        state.stepState(SendSections.Address) shouldBe StepState.Inactive
    }

    @Test
    fun `Continue on Asset and Address ignores the fee gate`() {
        val coinless = fresh.copy(isGasFeeLoading = true)
        coinless.isStepContinueDisabled() shouldBe true

        val onAddress =
            fresh.copy(
                expandedSection = SendSections.Address,
                isGasFeeLoading = true,
                showGasFee = true,
            )
        onAddress.isStepContinueDisabled() shouldBe false
        onAddress.copy(isLoading = true).isStepContinueDisabled() shouldBe true
    }

    @Test
    fun `Continue on Address is disabled by a rejected recipient`() {
        val state =
            fresh.copy(
                expandedSection = SendSections.Address,
                dstAddressError = UiText.DynamicString("invalid"),
            )
        state.isStepContinueDisabled() shouldBe true
    }

    @Test
    fun `Continue on Amount keeps the fee gate`() {
        val onAmount =
            fresh.copy(
                expandedSection = SendSections.Amount,
                showGasFee = true,
                isGasFeeLoading = true,
            )
        onAmount.isStepContinueDisabled() shouldBe true
        onAmount.copy(isGasFeeLoading = false).isStepContinueDisabled() shouldBe false
    }

    @Test
    fun `Continue on Asset needs the selected coin among the rows shown`() {
        val eth = row(Coins.Ethereum.ETH)
        val btc = row(Coins.Bitcoin.BTC)
        val selectedEth = fresh.copy(selectedCoin = eth)

        selectedEth.copy(assetRows = listOf(eth, btc)).isStepContinueDisabled() shouldBe false
        // A Bitcoin chip (or a search) that hides the selected ETH leaves nothing to continue with.
        selectedEth.copy(assetRows = listOf(btc)).isStepContinueDisabled() shouldBe true
        // Rows not loaded yet, or an empty search.
        selectedEth.isStepContinueDisabled() shouldBe true
        fresh.copy(assetRows = listOf(eth, btc)).isStepContinueDisabled() shouldBe true
    }

    @Test
    fun `the selected coin matches its row whatever the id's letter case`() {
        val eth = row(Coins.Ethereum.ETH)
        val shouted = row(Coins.Ethereum.ETH.copy(ticker = "eth"))
        fresh.copy(selectedCoin = shouted, assetRows = listOf(eth)).isSelectedAssetListed shouldBe
            true
    }

    @Test
    fun `every step change drops focus unless the new step focuses its own field`() {
        val onAddress = fresh.copy(expandedSection = SendSections.Address)
        onAddress.dropsFocusLeaving(SendSections.Amount) shouldBe true
        onAddress.dropsFocusLeaving(SendSections.Asset) shouldBe true
        fresh.dropsFocusLeaving(SendSections.Address) shouldBe true
        fresh
            .copy(expandedSection = SendSections.Amount)
            .dropsFocusLeaving(SendSections.Address) shouldBe true

        onAddress
            .copy(pendingFocusField = SendFocusField.ADDRESS)
            .dropsFocusLeaving(SendSections.Amount) shouldBe false
        // A request for another step's field does not keep the keyboard.
        onAddress
            .copy(pendingFocusField = SendFocusField.AMOUNT)
            .dropsFocusLeaving(SendSections.Amount) shouldBe true
    }

    @Test
    fun `staying on a step drops nothing`() {
        fresh.dropsFocusLeaving(SendSections.Asset) shouldBe false
    }

    @Test
    fun `the Amount field takes focus only while Amount is the selected step`() {
        shouldFocusAmountField(isActive = true, isArrival = true, isRequested = false) shouldBe true
        shouldFocusAmountField(isActive = true, isArrival = false, isRequested = true) shouldBe true
        shouldFocusAmountField(isActive = true, isArrival = false, isRequested = false) shouldBe
            false
        // Left during the arrival delay, or requested while sliding out: never.
        shouldFocusAmountField(isActive = false, isArrival = true, isRequested = false) shouldBe
            false
        shouldFocusAmountField(isActive = false, isArrival = false, isRequested = true) shouldBe
            false
    }

    private fun row(coin: Coin): TokenBalanceUiModel {
        val account = Account(token = coin, tokenValue = null, fiatValue = null, price = null)
        return TokenBalanceUiModel(
            model =
                SendSrc(
                    address =
                        Address(chain = coin.chain, address = "addr", accounts = listOf(account)),
                    account = account,
                ),
            title = coin.ticker,
            balance = null,
            fiatValue = null,
            isNativeToken = coin.isNativeToken,
            isLayer2 = false,
            tokenStandard = null,
            tokenLogo = coin.logo,
            chainLogo = coin.chain.logo,
        )
    }
}
