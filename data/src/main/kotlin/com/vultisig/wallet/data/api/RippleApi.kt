package com.vultisig.wallet.data.api

import java.math.BigInteger



interface RippleApi{
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getBalance(address: String): BigInteger
}
//internal class SuiApiImpl @Inject constructor(
//    private val http: HttpClient,
//) : SuiApi {
//
internal class RippleApiImpl : RippleApi{
    //    static let rippleServiceRpc = "https://xrplcluster.com"
    private val rpcUrl = "https://xrplcluster.com"

    override suspend fun broadcastTransaction(tx: String): String? {
        return null
    }

    override suspend fun getBalance(address: String): BigInteger {
        return BigInteger.ZERO
    }
}