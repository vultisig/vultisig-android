package com.vultisig.wallet.data.api.chains.ton

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TonApiJettonWalletTest {

    @Test
    fun `getJettonWallet filters by owner and jetton_address`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            TonApiImpl(
                MockHttpClient.capturingRequest(
                    status = HttpStatusCode.OK,
                    body =
                        """{"jetton_wallets":[{"address":"0:wallet","jetton":"0:master","balance":"87259388006"}]}""",
                    capture = capture,
                )
            )

        val response = api.getJettonWallet(address = "UQowner", contract = "EQmaster")

        assertEquals("/ton/v3/jetton/wallets", capture.lastPath)
        assertEquals(listOf("owner_address=UQowner&jetton_address=EQmaster"), capture.queries)
        assertEquals("87259388006", response.matchingWallet("0:master")?.balance)
    }
}
