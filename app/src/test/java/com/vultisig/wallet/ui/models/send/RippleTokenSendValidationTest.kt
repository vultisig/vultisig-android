package com.vultisig.wallet.ui.models.send

import RippleBroadcastSuccessResponseJson
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.RippleAccountInfoResponseAccountDataJson
import com.vultisig.wallet.data.api.RippleAccountInfoResponseJson
import com.vultisig.wallet.data.api.RippleAccountInfoResponseResultJson
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleServerStateResponseJson
import com.vultisig.wallet.data.api.RippleTrustLineJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.models.rippleTokenContractAddress
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Destination checks an XRPL issued-currency send has to pass: the destination-tag gate, re-derived
 * for a coin that is not native XRP, and the trust-line gate this change adds (issue #5212).
 */
internal class RippleTokenSendValidationTest {

    // lsfRequireDestTag: the destination rejects any payment that arrives without a tag.
    private val requireDestTagFlags = 0x00020000L

    private class FakeRippleApi(
        private val flags: Long? = null,
        private val lines: List<RippleTrustLineJson> = emptyList(),
        private val linesError: Exception? = null,
    ) : RippleApi {
        var accountLinesCalls = 0
            private set

        var accountsInfoCalls = 0
            private set

        override suspend fun broadcastTransaction(tx: String): String? = null

        override suspend fun getBalance(coin: Coin): BigInteger = BigInteger.ZERO

        override suspend fun getTokenBalance(coin: Coin): BigInteger = BigInteger.ZERO

        override suspend fun fetchAccountLines(walletAddress: String): List<RippleTrustLineJson> {
            accountLinesCalls++
            linesError?.let { throw it }
            return lines
        }

        override suspend fun fetchAccountsInfo(
            walletAddress: String
        ): RippleAccountInfoResponseJson {
            accountsInfoCalls++
            return RippleAccountInfoResponseJson(
                result =
                    RippleAccountInfoResponseResultJson(
                        accountData = RippleAccountInfoResponseAccountDataJson(flags = flags)
                    )
            )
        }

        override suspend fun fetchIssuedCurrencies(issuer: String): Set<String> =
            error("not used by these tests")

        override suspend fun fetchServerState(): RippleServerStateResponseJson =
            error("not used by these tests")

        override suspend fun getTsStatus(txHash: String): RippleBroadcastSuccessResponseJson? = null
    }

    // The flag lives on the destination account, not on the asset, so an untagged token deposit to
    // an exchange is credited to nobody exactly as an untagged XRP one is.
    @Test
    fun `an untagged token send to a tag-requiring destination is blocked`() = runTest {
        val service = ChainValidationService(FakeRippleApi(flags = requireDestTagFlags))

        val error =
            shouldThrow<InvalidTransactionDataException> {
                service.validateRippleDestinationTag(rlusd, DESTINATION, destinationTag = null)
            }

        (error.text as UiText.StringResource).resId shouldBe
            R.string.send_error_xrp_destination_tag_required
    }

    // A tag the user already supplied answers the question the lookup would ask.
    @Test
    fun `a tagged token send is allowed without asking the ledger`() = runTest {
        val api = FakeRippleApi(flags = requireDestTagFlags)

        ChainValidationService(api).validateRippleDestinationTag(rlusd, DESTINATION, 42u)

        api.accountsInfoCalls shouldBe 0
    }

    @Test
    fun `a token send to a destination holding the trust line is allowed`() = runTest {
        val service = ChainValidationService(FakeRippleApi(lines = listOf(line(RLUSD_HEX, ISSUER))))

        service.validateRippleDestinationTrustLine(rlusd, DESTINATION)
    }

    // A trust line is the (currency, issuer) pair: the same ticker from another issuer is a
    // different asset and cannot receive this one.
    @Test
    fun `a line for the same currency from another issuer does not count`() = runTest {
        val service =
            ChainValidationService(FakeRippleApi(lines = listOf(line(RLUSD_HEX, OTHER_ISSUER))))

        val error =
            shouldThrow<InvalidTransactionDataException> {
                service.validateRippleDestinationTrustLine(rlusd, DESTINATION)
            }

        (error.text as UiText.FormattedText).resId shouldBe
            R.string.send_error_xrp_destination_no_trust_line
    }

    // An unfunded account answers actNotFound with no lines, which is evidence of absence.
    @Test
    fun `a destination holding no lines at all is blocked`() = runTest {
        val service = ChainValidationService(FakeRippleApi(lines = emptyList()))

        shouldThrow<InvalidTransactionDataException> {
            service.validateRippleDestinationTrustLine(rlusd, DESTINATION)
        }
    }

    // Paying an obligation back to the account that issued it redeems the balance, so the issuer
    // needs no line of its own — and no lookup is worth spending on it.
    @Test
    fun `sending back to the issuer needs no trust line and no lookup`() = runTest {
        val api = FakeRippleApi(lines = emptyList())

        ChainValidationService(api).validateRippleDestinationTrustLine(rlusd, ISSUER)

        api.accountLinesCalls shouldBe 0
    }

    // The lookup is a read the send never needed before this guard existed, so a node blip must not
    // start rejecting sends that used to work.
    @Test
    fun `an unreadable trust-line lookup lets the send through`() = runTest {
        val service =
            ChainValidationService(
                FakeRippleApi(
                    linesError =
                        NetworkException(
                            httpStatusCode = 0,
                            message = "no route to host",
                            kind = NetworkErrorKind.NoConnectivity,
                        )
                )
            )

        service.validateRippleDestinationTrustLine(rlusd, DESTINATION)
    }

    @Test
    fun `native XRP holds no trust line and is not looked up`() = runTest {
        val api = FakeRippleApi(lines = emptyList())

        ChainValidationService(api).validateRippleDestinationTrustLine(xrp, DESTINATION)

        api.accountLinesCalls shouldBe 0
    }

    private fun line(currency: String, issuer: String) =
        RippleTrustLineJson(account = issuer, currency = currency, balance = "0")

    private val xrp =
        Coin(
            chain = Chain.Ripple,
            ticker = "XRP",
            logo = "xrp",
            address = "rMwdVSrJte3z8zJsdDySGSgBq27xWqt9VW",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "ripple",
            contractAddress = "",
            isNativeToken = true,
        )

    private val rlusd =
        xrp.copy(
            ticker = "RLUSD",
            decimal = RIPPLE_TOKEN_DECIMALS,
            contractAddress = rippleTokenContractAddress(RLUSD_HEX, ISSUER),
            isNativeToken = false,
        )

    private companion object {
        const val RLUSD_HEX = "524C555344000000000000000000000000000000"
        const val ISSUER = "rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De"
        const val OTHER_ISSUER = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
        const val DESTINATION = "rhZF88o88oc29XzEahzYyPtBwYF7insUSF"
    }
}
