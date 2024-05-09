package com.vultisig.wallet.service

import com.google.gson.Gson
import org.junit.Test
class THORChainServiceTest {

    @Test
    fun getAccountNumber() {
        val thorChainService = THORChainService(Gson())
        val address = "thor1ztuh7d5kjt3pp7hxl8gg9yxv3tg544yhdrav4m"
        val result = thorChainService.getAccountNumber(address)

    }
}