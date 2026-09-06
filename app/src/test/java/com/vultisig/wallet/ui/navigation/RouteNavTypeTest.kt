package com.vultisig.wallet.ui.navigation

import android.net.Uri
import android.os.Bundle
import com.vultisig.wallet.data.models.SendDeeplinkData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.URLDecoder
import java.net.URLEncoder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `Uri` is unavailable in plain JVM unit tests, so the percent-encoding pair is stood in with the
 * JDK's own encoder — an implementation independent of the one under test, which is what makes the
 * round-trip assertions meaningful.
 */
internal class RouteNavTypeTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.encode(any<String>()) } answers
            {
                URLEncoder.encode(firstArg<String>(), Charsets.UTF_8).replace("+", "%20")
            }
        every { Uri.decode(any<String>()) } answers
            {
                URLDecoder.decode(firstArg<String>(), Charsets.UTF_8)
            }
    }

    @AfterEach fun tearDown() = unmockkStatic(Uri::class)

    @Test
    fun `serializeAsValue leaves no character that would reshape the route`() {
        val openType = deepLink(memo = "=:ETH.ETH:0xAbC123:0/1/0 #stream?x=1")

        val serialized = VaultListOpenTypeNavType.serializeAsValue(openType)

        // RouteBuilder splices this straight into the path as "/$value", and the path pattern
        // matches a single segment, so any of these would leave the route unmatchable.
        serialized shouldNotContain "/"
        serialized shouldNotContain "?"
        serialized shouldNotContain "#"
        serialized shouldNotContain " "
    }

    @Test
    fun `a memo carrying path separators survives the route round trip`() {
        val openType = deepLink(memo = "=:ETH.ETH:0xAbC123:0/1/0")

        val restored = roundTrip(openType)

        restored shouldBe openType
    }

    @Test
    fun `a payload with no reserved characters round trips unchanged`() {
        val openType = Route.VaultList.OpenType.Home(vaultId = "vault-1")

        roundTrip(openType) shouldBe openType
    }

    @Test
    fun `parseValue does not decode a second time`() {
        // The framework has already decoded by the time parseValue runs, so a percent sequence
        // reaching it is literal payload — decoding again would turn this memo into "0/1/0".
        val openType = deepLink(memo = "0%2F1%2F0")

        val restored = roundTrip(openType)

        (restored as Route.VaultList.OpenType.DeepLink).sendDeepLinkData.memo shouldBe "0%2F1%2F0"
    }

    @Test
    fun `get returns null for an absent key instead of throwing`() {
        val bundle = mockk<Bundle>()
        every { bundle.getString("openType") } returns null

        VaultListOpenTypeNavType[bundle, "openType"].shouldBeNull()
    }

    @Test
    fun `parseValue tolerates a key written by a newer version of the route`() {
        val openType = deepLink(memo = "swap")
        val withUnknownKey =
            Uri.decode(VaultListOpenTypeNavType.serializeAsValue(openType))
                .replace("\"memo\":", "\"unknownKey\":true,\"memo\":")

        VaultListOpenTypeNavType.parseValue(withUnknownKey) shouldBe openType
    }

    /** Mirrors the framework: the matched route segment is `Uri.decode`d before `parseValue`. */
    private fun roundTrip(openType: Route.VaultList.OpenType): Route.VaultList.OpenType {
        val routeSegment = VaultListOpenTypeNavType.serializeAsValue(openType)
        return VaultListOpenTypeNavType.parseValue(Uri.decode(routeSegment))
    }

    private fun deepLink(memo: String) =
        Route.VaultList.OpenType.DeepLink(
            sendDeepLinkData =
                SendDeeplinkData(
                    assetChain = "THORChain",
                    assetTicker = "RUNE",
                    toAddress = "thor12a9rpf9u2ulwuezxkh6uas4au7xnde8umdua5t",
                    amount = "0.01",
                    memo = memo,
                )
        )
}
