package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import junit.framework.TestCase.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.CosmosFee
import vultisig.keysign.v1.CosmosMsg
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Cosmos
import java.math.BigInteger


class CosmosAminoTest {
    @Test
    fun testAminoSigning() {
        val aminoPayload = SignAmino(
            fee = CosmosFee(
                amount = listOf(
                    CosmosCoin(
                        denom = "uatom",
                        amount = "5000"
                    )
                ),
                gas = "200000"
            ),
            msgs = listOf(
                CosmosMsg(
                    type = "osmosis/smartaccount/add-authenticator",
                    value = """ {
    "authenticator_type": "AllOf",
    "data": "W3sidHlwZSI6IlNpZ25hdHVyZVZlcmlmaWNhdGlvbiIsImNvbmZpZyI6IkF3ZTJjZEZtM1hqM0VVWEg0WTBpWDhGVTNGMElKNnV3R3F3TktsenVLSmFwIn0seyJ0eXBlIjoiQ29zbXdhc21BdXRoZW50aWNhdG9yVjEiLCJjb25maWciOiJleUpqYjI1MGNtRmpkQ0k2SUNKdmMyMXZNVEI0Y1hZNGNteHdhMlpzZVhkdE9USnJOWGRrYlhCc2VuazNhMmgwWVhOc09XTXlZekE0Y0hOdGRteDFOVFF6YXpjeU5ITjVPVFJyTnpRaUxDQWljR0Z5WVcxeklqb2dJbVY1U25OaFZ6RndaRU5KTmtscVZYZE5SRUYzVFVSQmQwMUVRV2xNUTBwNVdsaE9iR1JHT1hkYVdFcHdZakpSYVU5cFNtdFpXR3RwVEVOS01HRlhNV3hZTW5od1lsZHNNRWxxY0RkSmJWWjFXa05KTmtscVJUTk9hbU40VG5wUk0wMUVXWGROUkVGM1RVUkJkMDFFUVdsbVdEQTlJbjA9In0seyJ0eXBlIjoiQW55T2YiLCJjb25maWciOiJXM3NpZEhsd1pTSTZJazFsYzNOaFoyVkdhV3gwWlhJaUxDSmpiMjVtYVdjaU9pSmxlVXBCWkVoc2QxcFRTVFpKYVRsMll6SXhkbU15YkhwTWJrSjJZako0ZEZsWE5XaGFNbFo1VEc1WmVGbHRWakJaVkVWMVZGaE9ibFV6WkdoalJWWTBXVmRPTUZGWE1YWmtWelV3VTFjMGFXWlJQVDBpZlN4N0luUjVjR1VpT2lKTlpYTnpZV2RsUm1sc2RHVnlJaXdpWTI5dVptbG5Jam9pWlhsS1FXUkliSGRhVTBrMlNXazVkbU15TVhaak1teDZURzVDZG1JeWVIUlpWelZvV2pKV2VVeHVXWGhaYlZZd1dWUkZkVlJZVG01Vk0wSnpZVmhTVTJJelZqQmFWazR6V1ZoQ1JtVkhSbXBrUlVaMFlqTldkV1JGYkhWSmJqQTlJbjBzZXlKMGVYQmxJam9pVFdWemMyRm5aVVpwYkhSbGNpSXNJbU52Ym1acFp5STZJbVY1U2tGa1NHeDNXbE5KTmtscE9YWmpNakYyWXpKc2VreHVRblppTW5oMFdWYzFhRm95Vm5sTWJsbDRXVzFXTUZsVVJYVlVXRTV1VlROa2FHTkZWalJaVjA0d1VWY3hkbVJYTlRCVU0xWXdTVzR3UFNKOUxIc2lkSGx3WlNJNklrMWxjM05oWjJWR2FXeDBaWElpTENKamIyNW1hV2NpT2lKbGVVcEJaRWhzZDFwVFNUWkphVGwyWXpJeGRtTXliSHBNYmtKMllqSjRkRmxYTldoYU1sWjVURzVaZUZsdFZqQlpWRVYxVkZoT2JsVXpRbk5oV0ZKVFlqTldNRnBXVGpOWldFSkdaVWRHYW1SRlJuUmlNMVoxWkVVNU1XUkRTamtpZlN4N0luUjVjR1VpT2lKTlpYTnpZV2RsUm1sc2RHVnlJaXdpWTI5dVptbG5Jam9pWlhsS1FXUkliSGRhVTBrMlNXazVkbU15TVhaak1teDZURzFPZG1KdFRteGlibEo1V1ZoU2JGcEhlSEJqV0Zad1drZHNNR1ZUTlRKTlYwcHNaRWRGZUV4ck1YcGFNV1J3WkVkb2EyTnRSak5WUnpsNllWaFNjR0l5TkdsbVVUMDlJbjBzZXlKMGVYQmxJam9pVFdWemMyRm5aVVpwYkhSbGNpSXNJbU52Ym1acFp5STZJbVY1U2tGa1NHeDNXbE5KTmtscE9YWmpNakYyWXpKc2VreHVXbWhpU0U1c1pFaENlVnBYV1hWa2FrWnBXbGhTYUUxVE5VNWpNbVJVV2xoU1YxbFhlSEJhUjBZd1lqTktWRnBZVWxGamJWWnRXbGhLYkdKdFRteEpiakE5SW4xZCJ9XQ==",
    "sender": "osmo1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqxf3l5h"
  }"""
                )
            )
        )
    }
}