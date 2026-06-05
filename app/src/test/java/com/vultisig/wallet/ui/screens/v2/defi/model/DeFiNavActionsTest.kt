package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.Coins
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class DeFiNavActionsTest {

    @Test
    fun `parseDepositType round-trips the wire type of every action`() {
        DeFiNavActions.entries.forEach { action ->
            assertEquals(action, parseDepositType(action.type), "wire type ${action.type}")
        }
    }

    @Test
    fun `parseDepositType maps each canonical wire string to its action`() {
        assertEquals(DeFiNavActions.BOND, parseDepositType("bond"))
        assertEquals(DeFiNavActions.UNBOND, parseDepositType("unbond"))
        assertEquals(DeFiNavActions.STAKE_RUJI, parseDepositType("stake_ruji"))
        assertEquals(DeFiNavActions.UNSTAKE_RUJI, parseDepositType("unstake_ruji"))
        assertEquals(DeFiNavActions.WITHDRAW_RUJI, parseDepositType("withdraw"))
        assertEquals(DeFiNavActions.STAKE_TCY, parseDepositType("stake_tcy"))
        assertEquals(DeFiNavActions.UNSTAKE_TCY, parseDepositType("unstake_tcy"))
        assertEquals(DeFiNavActions.MINT_YRUNE, parseDepositType("mint_yrune"))
        assertEquals(DeFiNavActions.REDEEM_YRUNE, parseDepositType("redeem_yrune"))
        assertEquals(DeFiNavActions.MINT_YTCY, parseDepositType("mint_ytcy"))
        assertEquals(DeFiNavActions.REDEEM_YTCY, parseDepositType("redeem_ytcy"))
        assertEquals(DeFiNavActions.STAKE_STCY, parseDepositType("stake_stcy"))
        assertEquals(DeFiNavActions.UNSTAKE_STCY, parseDepositType("unstake_stcy"))
        assertEquals(DeFiNavActions.DEPOSIT_USDC_CIRCLE, parseDepositType("deposit_usdc_circle"))
        assertEquals(DeFiNavActions.WITHDRAW_USDC_CIRCLE, parseDepositType("withdraw_usdc_circle"))
        assertEquals(DeFiNavActions.STAKE_CACAO, parseDepositType("stake_cacao"))
        assertEquals(DeFiNavActions.UNSTAKE_CACAO, parseDepositType("unstake_cacao"))
        assertEquals(DeFiNavActions.ADD_LP, parseDepositType("add_lp"))
        assertEquals(DeFiNavActions.REMOVE_LP, parseDepositType("remove_lp"))
        assertEquals(DeFiNavActions.FREEZE_TRX, parseDepositType("freeze_trx"))
        assertEquals(DeFiNavActions.UNFREEZE_TRX, parseDepositType("unfreeze_trx"))
    }

    @Test
    fun `parseDepositType accepts separator-free and alias spellings`() {
        assertEquals(DeFiNavActions.STAKE_RUJI, parseDepositType("stakeruji"))
        assertEquals(DeFiNavActions.MINT_YRUNE, parseDepositType("receive_yrune"))
        assertEquals(DeFiNavActions.REDEEM_YRUNE, parseDepositType("sell_yrune"))
        assertEquals(DeFiNavActions.UNFREEZE_TRX, parseDepositType("unfreezetrx"))
    }

    @Test
    fun `parseDepositType folds case for both the when-arm and valueOf-fallback paths`() {
        assertEquals(DeFiNavActions.BOND, parseDepositType("BOND"))
        assertEquals(DeFiNavActions.STAKE_RUJI, parseDepositType("Stake_Ruji"))
        assertEquals(DeFiNavActions.DEPOSIT_USDC_CIRCLE, parseDepositType("Deposit_USDC_Circle"))
    }

    @Test
    fun `parseDepositType resolves the underscored USDC circle wire strings via the valueOf fallback`() {
        // The underscored when-arms are unreachable (input is separator-stripped first), so these
        // resolve only via the valueOf fallback and their separator-free spelling returns null.
        assertEquals(DeFiNavActions.DEPOSIT_USDC_CIRCLE, parseDepositType("deposit_usdc_circle"))
        assertEquals(DeFiNavActions.WITHDRAW_USDC_CIRCLE, parseDepositType("withdraw_usdc_circle"))
        assertNull(parseDepositType("depositusdccircle"))
        assertNull(parseDepositType("withdrawusdccircle"))
    }

    @Test
    fun `parseDepositType returns null for unknown or absent type`() {
        assertNull(parseDepositType(null))
        assertNull(parseDepositType("not_a_real_action"))
    }

    @Test
    fun `getStakeDeFiNavAction maps each stakeable coin to its stake action`() {
        assertEquals(DeFiNavActions.STAKE_RUJI, Coins.ThorChain.RUJI.getStakeDeFiNavAction())
        assertEquals(DeFiNavActions.STAKE_TCY, Coins.ThorChain.TCY.getStakeDeFiNavAction())
        assertEquals(DeFiNavActions.MINT_YRUNE, Coins.ThorChain.yRUNE.getStakeDeFiNavAction())
        assertEquals(DeFiNavActions.MINT_YTCY, Coins.ThorChain.yTCY.getStakeDeFiNavAction())
        assertEquals(DeFiNavActions.STAKE_STCY, Coins.ThorChain.sTCY.getStakeDeFiNavAction())
        assertEquals(DeFiNavActions.STAKE_CACAO, Coins.MayaChain.CACAO.getStakeDeFiNavAction())
    }

    @Test
    fun `getUnstakeDeFiNavAction maps each stakeable coin to its unstake action`() {
        assertEquals(DeFiNavActions.UNSTAKE_RUJI, Coins.ThorChain.RUJI.getUnstakeDeFiNavAction())
        assertEquals(DeFiNavActions.UNSTAKE_TCY, Coins.ThorChain.TCY.getUnstakeDeFiNavAction())
        assertEquals(DeFiNavActions.REDEEM_YRUNE, Coins.ThorChain.yRUNE.getUnstakeDeFiNavAction())
        assertEquals(DeFiNavActions.REDEEM_YTCY, Coins.ThorChain.yTCY.getUnstakeDeFiNavAction())
        assertEquals(DeFiNavActions.UNSTAKE_STCY, Coins.ThorChain.sTCY.getUnstakeDeFiNavAction())
        assertEquals(DeFiNavActions.UNSTAKE_CACAO, Coins.MayaChain.CACAO.getUnstakeDeFiNavAction())
    }

    @Test
    fun `getStakeDeFiNavAction throws for an unsupported coin`() {
        assertFailsWith<IllegalStateException> { Coins.ThorChain.RUNE.getStakeDeFiNavAction() }
    }

    @Test
    fun `getUnstakeDeFiNavAction throws for an unsupported coin`() {
        assertFailsWith<IllegalStateException> { Coins.ThorChain.RUNE.getUnstakeDeFiNavAction() }
    }
}
