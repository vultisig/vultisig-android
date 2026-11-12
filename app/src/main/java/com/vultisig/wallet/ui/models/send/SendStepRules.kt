package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.ui.models.v3.onboarding.StepState

/**
 * Whether the plain-send step header may jump to [step]: every earlier step is complete and [step]
 * is not the one already shown. Asset is always reachable, Address once an asset is chosen, Amount
 * once the recipient is valid.
 */
internal fun SendFormUiModel.canNavigate(step: SendSections): Boolean {
    if (step == expandedSection) return false
    return when (step) {
        SendSections.Asset -> true
        SendSections.Address -> isAssetStepComplete
        SendSections.Amount -> isAssetStepComplete && isDstAddressValid
    }
}

/**
 * The header icon state for [step]: the shown step is in progress, a finished one is done, the rest
 * inactive. Amount never reads as done, since leaving it means opening the review.
 */
internal fun SendFormUiModel.stepState(step: SendSections): StepState {
    if (step == expandedSection) return StepState.InProgress
    val isDone =
        when (step) {
            SendSections.Asset -> isAssetStepComplete
            SendSections.Address -> isAssetStepComplete && isDstAddressValid
            SendSections.Amount -> false
        }
    return if (isDone) StepState.Done else StepState.Inactive
}

/**
 * Whether the plain-send Continue is disabled on the step shown. Asset and Address only need what
 * they move on with, so the fee gate of [isContinueDisabled] applies on Amount alone; Asset needs
 * the selected coin among the rows shown.
 */
internal fun SendFormUiModel.isStepContinueDisabled(): Boolean =
    when (expandedSection) {
        SendSections.Asset -> isLoading || !isSelectedAssetListed
        SendSections.Address -> isLoading || isDstAddressBlocking
        SendSections.Amount -> isContinueDisabled()
    }

/**
 * Whether the selected coin is among the asset rows on screen, so Continue carries on with the coin
 * the user sees; a chain chip or search that hides it leaves nothing to continue with.
 */
internal val SendFormUiModel.isSelectedAssetListed: Boolean
    get() {
        val selectedId = selectedCoin?.model?.account?.token?.id ?: return false
        return assetRows.any { it.model.account.token.id.equals(selectedId, ignoreCase = true) }
    }

/**
 * Whether arriving on the step shown from [previous] drops keyboard focus: on every step change, so
 * a pane sliding out never keeps the keyboard, unless the shown step is about to focus its own
 * field for [SendFormUiModel.pendingFocusField].
 */
internal fun SendFormUiModel.dropsFocusLeaving(previous: SendSections): Boolean =
    previous != expandedSection && pendingFocusField?.step != expandedSection

/**
 * Whether the Amount pane focuses its field: only while Amount is the step selected right now
 * ([isActive]), on the pane's first arrival or on an explicit request.
 */
internal fun shouldFocusAmountField(isActive: Boolean, isArrival: Boolean, isRequested: Boolean) =
    isActive && (isArrival || isRequested)

private val SendFocusField.step: SendSections
    get() =
        when (this) {
            SendFocusField.ADDRESS -> SendSections.Address
            SendFocusField.AMOUNT -> SendSections.Amount
        }
