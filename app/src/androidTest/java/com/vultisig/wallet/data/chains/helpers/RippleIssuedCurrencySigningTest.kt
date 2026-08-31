@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.rippleTokenContractAddress
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.proto.Ripple

/**
 * Signing input for an XRPL issued-currency Payment.
 *
 * Runs instrumented because [RippleHelper.getPreSignedInputData] resolves the vault key through
 * WalletCore's `PublicKey` JNI; the currency-code and value conversions it feeds have pure-JVM
 * coverage in `RippleIssuedCurrencyTest`.
 */
class RippleIssuedCurrencySigningTest {

    /**
     * The serialized `SigningInput` iOS freezes for this exact payload
     * (`RippleIssuedCurrencySigningTests.swift`). A co-signing committee only completes when every
     * device builds the same bytes, so the parity is asserted against the other platform's literal
     * rather than against a value this repo generated for itself.
     */
    private val iosTokenPaymentSigningInput =
        "080a106318cec2f10522227250564d68574273664639694d58596a3361417a4a566b504454464e537957644b" +
            "794254122e0a035553441203312e351a2272486239434a4157794234726a39315652576e3936446b756b" +
            "4734627764747954681a2272456238544b336742676b3561755a6b77633673486e777247564a48384475" +
            "614c687a210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    /**
     * The TrustSet counterpart of the literal above, from the same iOS test. A trust-line
     * activation is the one operation a mixed-version committee has to agree on byte for byte: a
     * signer predating the discriminator infers TrustSet from the coin alone and must land here.
     */
    private val iosTrustSetSigningInput =
        "080a106318cec2f10522227250564d68574273664639694d58596a3361417a4a566b504454464e537957644b" +
            "793a300a2e0a035553441203312e351a2272486239434a4157794234726a39315652576e3936446b756b" +
            "4734627764747954687a210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8" +
            "1798"

    @Test
    fun trustSetMatchesTheSigningInputIosFreezes() {
        val payload =
            tokenPayload(transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET)
                .copy(toAddress = "")

        assertEquals(
            iosTrustSetSigningInput,
            RippleHelper.getPreSignedInputData(payload).toHexString(),
        )
    }

    // The keysign amount IS the trust line's limit, and a TrustSet names no destination at all —
    // so it must be built before the guard that rejects an empty toAddress.
    @Test
    fun trustSetCarriesTheLimitAndNoPayment() {
        val payload =
            tokenPayload(transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET)
                .copy(toAddress = "")

        val input = Ripple.SigningInput.parseFrom(RippleHelper.getPreSignedInputData(payload))

        assertTrue(input.hasOpTrustSet())
        assertEquals("USD", input.opTrustSet.limitAmount.currency)
        assertEquals(ISSUER, input.opTrustSet.limitAmount.issuer)
        assertEquals("1.5", input.opTrustSet.limitAmount.value)
        assertFalse(input.hasOpPayment())
        assertEquals(0L, input.flags)
    }

    // The fail-safe that lets a mixed-version token send abort instead of opening a trust line.
    @Test
    fun aTrustSetAndATokenPaymentOverTheSameCoinSignDifferentBytes() {
        val trustSet =
            RippleHelper.getPreSignedInputData(
                tokenPayload(transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET)
                    .copy(toAddress = "")
            )

        assertNotEquals(
            trustSet.toHexString(),
            RippleHelper.getPreSignedInputData(tokenPayload()).toHexString(),
        )
    }

    @Test
    fun tokenPaymentMatchesTheSigningInputIosFreezes() {
        val inputData = RippleHelper.getPreSignedInputData(tokenPayload())

        assertEquals(iosTokenPaymentSigningInput, inputData.toHexString())
    }

    // An issued currency travels as a decimal value string against its (currency, issuer) pair.
    // Leaving the drops `amount` set as well would present the ledger with a native XRP transfer of
    // the token's base units.
    @Test
    fun tokenPaymentCarriesACurrencyAmountAndNoDropsAmount() {
        val input =
            Ripple.SigningInput.parseFrom(RippleHelper.getPreSignedInputData(tokenPayload()))

        val payment = input.opPayment
        assertEquals(
            Ripple.OperationPayment.AmountOneofCase.CURRENCY_AMOUNT,
            payment.amountOneofCase,
        )
        assertEquals("USD", payment.currencyAmount.currency)
        assertEquals(ISSUER, payment.currencyAmount.issuer)
        assertEquals("1.5", payment.currencyAmount.value)
        assertEquals(0L, payment.amount)
        assertFalse(input.hasOpTrustSet())
        assertTrue(input.rawJson.isEmpty())
    }

    // The only issued currency the catalog ships is stored in the 160-bit form, which takes the
    // other arm of the code normalization than the 3-character case above.
    @Test
    fun aHexCurrencyCodeReachesTheWireVerbatim() {
        val payload = tokenPayload(currency = RLUSD_HEX)

        val input = Ripple.SigningInput.parseFrom(RippleHelper.getPreSignedInputData(payload))

        assertEquals(RLUSD_HEX, input.opPayment.currencyAmount.currency)
        assertEquals("1.5", input.opPayment.currencyAmount.value)
    }

    // WalletCore uppercases a 3-byte code before encoding it while the ledger compares those bytes
    // case-sensitively, so signing would move a currency the reviewer never saw.
    @Test
    fun aCurrencyCodeTheSignerWouldAlterIsRefused() {
        val payload = tokenPayload(currency = "usd")

        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.getPreSignedInputData(payload)
        }
    }

    // The two halves of a relayed coin decide the amount encoding, so they have to agree.
    @Test
    fun aCoinContradictingItsOwnTokenIdIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.getPreSignedInputData(
                tokenPayload().let { it.copy(coin = it.coin.copy(contractAddress = "")) }
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.getPreSignedInputData(
                tokenPayload().let { it.copy(coin = it.coin.copy(isNativeToken = true)) }
            )
        }
    }

    // The hand-built memo JSON encodes Amount as drops, so a token reaching it would sign the
    // token's base units away as native XRP.
    @Test
    fun aTokenPaymentCannotCarryAnOnChainMemo() {
        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.getPreSignedInputData(tokenPayload().copy(memo = "hello"))
        }
    }

    // Another chain's discriminator relayed onto a Ripple payload describes an operation none of
    // the branches build, so it is refused rather than signed as a Payment.
    @Test
    fun anUnsupportedOperationDiscriminatorIsRefused() {
        val payload = tokenPayload(transactionType = TransactionType.TRANSACTION_TYPE_TON_DEPOSIT)

        assertThrows(IllegalStateException::class.java) {
            RippleHelper.getPreSignedInputData(payload)
        }
    }

    // A TrustSet is meaningless for native XRP: there is no issuer to trust.
    @Test
    fun aTrustSetOnNativeXrpIsRefused() {
        val payload =
            tokenPayload(transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET).let {
                it.copy(coin = it.coin.copy(contractAddress = "", isNativeToken = true))
            }

        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.getPreSignedInputData(payload)
        }
    }

    // A destination tag is a property of the destination account, so it rides a token Payment
    // exactly as it rides a native one.
    @Test
    fun aTokenPaymentCarriesItsDestinationTag() {
        val payload = tokenPayload(destinationTag = 42u)

        val input = Ripple.SigningInput.parseFrom(RippleHelper.getPreSignedInputData(payload))

        assertEquals(42L, input.opPayment.destinationTag)
        assertEquals(
            Ripple.OperationPayment.AmountOneofCase.CURRENCY_AMOUNT,
            input.opPayment.amountOneofCase,
        )
    }

    private fun tokenPayload(
        currency: String = "USD",
        destinationTag: UInt? = null,
        transactionType: TransactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
    ) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ripple,
                    ticker = currency,
                    logo = "",
                    address = ACCOUNT,
                    decimal = RIPPLE_TOKEN_DECIMALS,
                    hexPublicKey = PUBLIC_KEY,
                    priceProviderID = "",
                    contractAddress = rippleTokenContractAddress(currency, ISSUER),
                    isNativeToken = false,
                ),
            toAddress = DESTINATION,
            toAmount = BigInteger("1500000000000000"),
            blockChainSpecific =
                BlockChainSpecific.Ripple(
                    sequence = 99UL,
                    gas = 10UL,
                    lastLedgerSequence = 12_345_678UL,
                    destinationTag = destinationTag,
                    transactionType = transactionType,
                ),
            vaultPublicKeyECDSA = PUBLIC_KEY,
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val ACCOUNT = "rPVMhWBsfF9iMXYj3aAzJVkPDTFNSyWdKy"
        const val ISSUER = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
        const val DESTINATION = "rEb8TK3gBgk5auZkwc6sHnwrGVJH8DuaLh"
        const val RLUSD_HEX = "524C555344000000000000000000000000000000"
        const val PUBLIC_KEY = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    }
}
