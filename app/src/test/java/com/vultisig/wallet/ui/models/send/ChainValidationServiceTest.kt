package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

internal class ChainValidationServiceTest {

    private val service = ChainValidationService()

    @Test
    fun `validateSlippage - null returns required error`() {
        assertTrue(service.validateSlippage(null) is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - blank returns required error`() {
        assertTrue(service.validateSlippage("  ") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - valid value returns null`() {
        assertNull(service.validateSlippage("1.0"))
    }

    @Test
    fun `validateSlippage - value above 100 returns invalid error`() {
        assertTrue(service.validateSlippage("101") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - negative value returns invalid error`() {
        assertTrue(service.validateSlippage("-1") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - non-numeric returns format error`() {
        assertTrue(service.validateSlippage("abc") is UiText.StringResource)
    }

    @Test
    fun `formatSlippage - converts 1 percent to decimal`() {
        assertEquals("0.01", service.formatSlippage("1.0"))
    }

    @Test
    fun `formatSlippage - invalid input returns default`() {
        assertEquals("0.01", service.formatSlippage("invalid"))
    }

    private val dotCoin =
        Coin(
            chain = Chain.Polkadot,
            ticker = "DOT",
            logo = "",
            address = "",
            decimal = 10,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `checkIsReapable - null account returns null`() {
        val gasFee = TokenValue(BigInteger.ONE, "DOT", 10)
        assertNull(service.checkIsReapable(null, dotCoin, "1.0", gasFee))
    }

    @Test
    fun `checkIsReapable - polkadot sufficient balance returns null`() {
        // 20 DOT balance, sending 5 DOT, 0.1 DOT fee → 14.9 DOT remaining > 1 DOT threshold
        val balance = BigInteger.valueOf(200_000_000_000L) // 20 DOT (10 decimals)
        val account = Account(dotCoin, TokenValue(balance, "DOT", 10), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(1_000_000_000L), "DOT", 10) // 0.1 DOT
        assertNull(service.checkIsReapable(account, dotCoin, "5.0", gasFee))
    }

    @Test
    fun `checkIsReapable - polkadot balance below existential deposit returns warning`() {
        // 11 DOT balance, sending 10 DOT, 0.5 DOT fee → 0.5 DOT remaining < 1 DOT threshold
        val balance = BigInteger.valueOf(110_000_000_000L) // 11 DOT
        val account = Account(dotCoin, TokenValue(balance, "DOT", 10), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(5_000_000_000L), "DOT", 10) // 0.5 DOT
        val result = service.checkIsReapable(account, dotCoin, "10.0", gasFee)
        assertEquals(
            R.string.send_form_polka_reaping_warning,
            (result as UiText.StringResource).resId,
        )
    }

    @Test
    fun `checkIsReapable - non-reaping chain returns null`() {
        val ethCoin = dotCoin.copy(chain = Chain.Ethereum, ticker = "ETH", decimal = 18)
        val account = Account(ethCoin, TokenValue(BigInteger.ONE, "ETH", 18), null, null)
        val gasFee = TokenValue(BigInteger.ONE, "ETH", 18)
        assertNull(service.checkIsReapable(account, ethCoin, "0.001", gasFee))
    }

    @Test
    fun `formatSlippage - non-integer percentage truncates to 2 decimal places`() {
        // "1.555" → setScale(2, DOWN) → "1.55" → divide by 100 → "0.0155"
        assertEquals("0.0155", service.formatSlippage("1.555"))
    }

    @Test
    fun `formatSlippage - half percent converts correctly`() {
        assertEquals("0.005", service.formatSlippage("0.5"))
    }

    // validateCardanoUTXORequirements tests

    @Test
    fun `validateCardanoUTXORequirements - amount below dust threshold throws`() {
        val exception =
            assertThrows(InvalidTransactionDataException::class.java) {
                service.validateCardanoUTXORequirements(
                    sendAmount = BigInteger.valueOf(1_000_000), // 1 ADA < 1.4 ADA dust
                    totalBalance = BigInteger.valueOf(10_000_000),
                    estimatedFee = BigInteger.valueOf(200_000),
                )
            }
        assertEquals(
            R.string.minimum_send_amount_is_ada,
            (exception.text as UiText.FormattedText).resId,
        )
    }

    @Test
    fun `validateCardanoUTXORequirements - insufficient balance throws`() {
        val exception =
            assertThrows(InvalidTransactionDataException::class.java) {
                service.validateCardanoUTXORequirements(
                    sendAmount = BigInteger.valueOf(5_000_000), // 5 ADA
                    totalBalance = BigInteger.valueOf(4_000_000), // 4 ADA (less than send + fee)
                    estimatedFee = BigInteger.valueOf(200_000),
                )
            }
        assertEquals(
            R.string.insufficient_balance_try_send,
            (exception.text as UiText.FormattedText).resId,
        )
    }

    @Test
    fun `validateCardanoUTXORequirements - change below minUTXO throws`() {
        // Send 8.5 ADA from 10 ADA with 0.2 ADA fee → 1.3 ADA remaining < 1.4 ADA dust
        val exception =
            assertThrows(InvalidTransactionDataException::class.java) {
                service.validateCardanoUTXORequirements(
                    sendAmount = BigInteger.valueOf(8_500_000), // 8.5 ADA
                    totalBalance = BigInteger.valueOf(10_000_000), // 10 ADA
                    estimatedFee = BigInteger.valueOf(200_000), // 0.2 ADA
                )
            }
        assertEquals(
            R.string.this_amount_would_leave_too_little_change,
            (exception.text as UiText.FormattedText).resId,
        )
    }

    @Test
    fun `validateCardanoUTXORequirements - valid amount does not throw`() {
        // Send 3 ADA from 10 ADA with 0.2 ADA fee → 6.8 ADA remaining > 1.4 ADA
        service.validateCardanoUTXORequirements(
            sendAmount = BigInteger.valueOf(3_000_000), // 3 ADA
            totalBalance = BigInteger.valueOf(10_000_000), // 10 ADA
            estimatedFee = BigInteger.valueOf(200_000), // 0.2 ADA
        )
        // no exception means success
    }

    @Test
    fun `validateCardanoUTXORequirements - exact zero change does not throw`() {
        // Send exactly total - fee so remaining is 0 (not > 0 && < dust)
        service.validateCardanoUTXORequirements(
            sendAmount = BigInteger.valueOf(9_800_000), // 9.8 ADA
            totalBalance = BigInteger.valueOf(10_000_000), // 10 ADA
            estimatedFee = BigInteger.valueOf(200_000), // 0.2 ADA
        )
    }

    // checkIsReapable Ripple tests

    private val xrpCoin =
        Coin(
            chain = Chain.Ripple,
            ticker = "XRP",
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `checkIsReapable - xrp sufficient balance returns null`() {
        // 20 XRP balance, sending 5 XRP, 0.1 XRP fee → 14.9 XRP remaining > 1 XRP threshold
        val balance = BigInteger.valueOf(20_000_000L) // 20 XRP (6 decimals)
        val account = Account(xrpCoin, TokenValue(balance, "XRP", 6), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(100_000L), "XRP", 6) // 0.1 XRP
        assertNull(service.checkIsReapable(account, xrpCoin, "5.0", gasFee))
    }

    @Test
    fun `checkIsReapable - xrp balance below existential deposit returns ripple reaping warning`() {
        // 11 XRP balance, sending 10 XRP, 0.5 XRP fee → 0.5 XRP remaining < 1 XRP threshold
        val balance = BigInteger.valueOf(11_000_000L) // 11 XRP
        val account = Account(xrpCoin, TokenValue(balance, "XRP", 6), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(500_000L), "XRP", 6) // 0.5 XRP
        val result = service.checkIsReapable(account, xrpCoin, "10.0", gasFee)
        assertEquals(
            R.string.send_form_ripple_reaping_warning,
            (result as UiText.StringResource).resId,
        )
    }

    // validateBtcLikeAmount tests

    @Test
    fun `validateBtcLikeAmount - null plan with above-dust amount throws insufficient utxos error`() {
        val exception =
            assertThrows(InvalidTransactionDataException::class.java) {
                service.validateBtcLikeAmount(
                    tokenAmountInt = BigInteger.valueOf(10_000L),
                    chain = Chain.Bitcoin,
                    plan = null,
                )
            }
        assertEquals(
            R.string.insufficient_utxos_error,
            (exception.text as UiText.StringResource).resId,
        )
    }

    @Test
    fun `validateBtcLikeAmount - amount below Bitcoin dust throws FormattedText with minimum send amount message`() {
        try {
            service.validateBtcLikeAmount(
                tokenAmountInt = BigInteger.valueOf(100L),
                chain = Chain.Bitcoin,
                plan = null,
            )
            fail("Expected InvalidTransactionDataException to be thrown")
        } catch (e: InvalidTransactionDataException) {
            assertEquals(
                R.string.send_form_minimum_send_amount_is_requires_this,
                (e.text as UiText.FormattedText).resId,
            )
        } catch (e: Throwable) {
            if (
                e is UnsatisfiedLinkError ||
                    e is ExceptionInInitializerError ||
                    e is NoClassDefFoundError
            ) {
                assumeTrue(false, "WalletCore JNI not available: ${e.message}")
            } else throw e
        }
    }

    // selectUtxosIfNeeded tests (non-UTXO pass-through and null plan)

    @Test
    fun `selectUtxosIfNeeded - non-UTXO chain returns specific unchanged`() {
        val specific =
            BlockChainSpecificAndUtxo(
                blockChainSpecific =
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ONE,
                        priorityFeeWei = BigInteger.ONE,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.valueOf(21000),
                    )
            )
        val result = service.selectUtxosIfNeeded(Chain.Ethereum, specific, null)
        assertEquals(specific, result)
    }

    @Test
    fun `selectUtxosIfNeeded - UTXO chain with null plan returns specific unchanged`() {
        val specific =
            BlockChainSpecificAndUtxo(
                blockChainSpecific =
                    BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = false)
            )
        val result = service.selectUtxosIfNeeded(Chain.Bitcoin, specific, null)
        assertEquals(specific, result)
    }
}
