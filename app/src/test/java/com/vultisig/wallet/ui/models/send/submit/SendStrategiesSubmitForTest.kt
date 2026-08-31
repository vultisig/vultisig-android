package com.vultisig.wallet.ui.models.send.submit

import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Pins the [SendStrategies.submitFor] routing: every [DeFiNavActions] entry (and `null`) must run
 * at most one strategy's `submit()`, and only the expected one. The existing submit tests all run
 * with `defiType == null`, so a misrouted DeFi action would otherwise pass the suite silently.
 */
internal class SendStrategiesSubmitForTest {

    private enum class Target {
        DEFAULT,
        STAKE,
        UNSTAKE,
        MINT,
        REDEEM,
        WITHDRAW_USDC_CIRCLE,
        UNSUPPORTED,
    }

    private val default = mockk<DefaultSendStrategy>(relaxed = true)
    private val stake = mockk<StakeStrategy>(relaxed = true)
    private val unstake = mockk<UnstakeStrategy>(relaxed = true)
    private val mint = mockk<MintStrategy>(relaxed = true)
    private val redeem = mockk<RedeemStrategy>(relaxed = true)
    private val withdrawUsdcCircle = mockk<WithdrawUsdcCircleStrategy>(relaxed = true)
    private var unsupportedDefiType: DeFiNavActions? = null

    private val strategies =
        SendStrategies(
            default = default,
            stake = stake,
            unstake = unstake,
            mint = mint,
            redeem = redeem,
            withdrawUsdcCircle = withdrawUsdcCircle,
            onUnsupportedDefiType = { unsupportedDefiType = it },
        )

    @ParameterizedTest(name = "{0}")
    @EnumSource(DeFiNavActions::class)
    fun `submitFor routes each DeFi action to exactly its strategy`(action: DeFiNavActions) {
        strategies.submitFor(action)
        verifyOnly(expectedTarget.getValue(action), action)
    }

    @Test
    fun `submitFor routes null to the default send strategy`() {
        strategies.submitFor(null)
        verifyOnly(Target.DEFAULT, null)
    }

    private fun verifyOnly(expected: Target, action: DeFiNavActions?) {
        verify(exactly = expected.count(Target.DEFAULT)) { default.submit() }
        verify(exactly = expected.count(Target.STAKE)) { stake.submit() }
        verify(exactly = expected.count(Target.UNSTAKE)) { unstake.submit() }
        verify(exactly = expected.count(Target.MINT)) { mint.submit() }
        verify(exactly = expected.count(Target.REDEEM)) { redeem.submit() }
        verify(exactly = expected.count(Target.WITHDRAW_USDC_CIRCLE)) {
            withdrawUsdcCircle.submit()
        }
        assertEquals(if (expected == Target.UNSUPPORTED) action else null, unsupportedDefiType)
    }

    private fun Target.count(candidate: Target) = if (this == candidate) 1 else 0

    private companion object {
        val expectedTarget =
            mapOf(
                // Bond/Unbond/Stake-Cacao/Unstake-Cacao/Remove-LP only submit through the Deposit
                // flow now — a Route.Send carrying one of these must fail closed.
                DeFiNavActions.BOND to Target.UNSUPPORTED,
                DeFiNavActions.UNBOND to Target.UNSUPPORTED,
                DeFiNavActions.STAKE_CACAO to Target.UNSUPPORTED,
                DeFiNavActions.UNSTAKE_CACAO to Target.UNSUPPORTED,
                DeFiNavActions.REMOVE_LP to Target.UNSUPPORTED,
                DeFiNavActions.WITHDRAW_RUJI to Target.UNSTAKE,
                DeFiNavActions.STAKE_RUJI to Target.STAKE,
                DeFiNavActions.UNSTAKE_RUJI to Target.UNSTAKE,
                DeFiNavActions.STAKE_SRUJI to Target.STAKE,
                DeFiNavActions.UNSTAKE_SRUJI to Target.UNSTAKE,
                DeFiNavActions.STAKE_TCY to Target.STAKE,
                DeFiNavActions.UNSTAKE_TCY to Target.UNSTAKE,
                DeFiNavActions.MINT_YRUNE to Target.MINT,
                DeFiNavActions.REDEEM_YRUNE to Target.REDEEM,
                DeFiNavActions.MINT_YTCY to Target.MINT,
                DeFiNavActions.REDEEM_YTCY to Target.REDEEM,
                DeFiNavActions.STAKE_STCY to Target.STAKE,
                DeFiNavActions.UNSTAKE_STCY to Target.UNSTAKE,
                DeFiNavActions.STAKE_YBRUNE to Target.STAKE,
                DeFiNavActions.UNSTAKE_YBRUNE to Target.UNSTAKE,
                DeFiNavActions.WITHDRAW_USDC_CIRCLE to Target.WITHDRAW_USDC_CIRCLE,
                DeFiNavActions.ADD_LP to Target.DEFAULT,
                DeFiNavActions.FREEZE_TRX to Target.DEFAULT,
                DeFiNavActions.UNFREEZE_TRX to Target.DEFAULT,
                DeFiNavActions.STAKE_TON to Target.DEFAULT,
                DeFiNavActions.UNSTAKE_TON to Target.DEFAULT,
            )
    }
}
