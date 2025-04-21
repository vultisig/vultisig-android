package com.vultisig.wallet.util

import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.util.TestVaults
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

open class VaultTest : CleanTest() {

    @Inject
    lateinit var vaults: VaultRepository

    override fun setUp() {
        super.setUp()

        runBlocking {
            vaults.add(TestVaults.dkls)
        }
    }


}