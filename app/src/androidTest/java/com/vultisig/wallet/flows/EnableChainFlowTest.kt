package com.vultisig.wallet.flows

import com.vultisig.wallet.ui.pages.home.SelectChainsPage
import com.vultisig.wallet.ui.pages.home.VaultAccountsPage
import com.vultisig.wallet.ui.utils.back
import com.vultisig.wallet.util.VaultTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@HiltAndroidTest
@Ignore
class EnableChainFlowTest : VaultTest() {

    @Before
    override fun setUp() {
        super.setUp()
    }

    @Test
    fun testEnableThenDisableChain() {

        val scenario = launchMainActivity()

        val chainName = "Cosmos"

        // enable chain
        val accounts = VaultAccountsPage(compose)

        accounts.waitUntilShown()
        accounts.chooseChains()

        val selectChains = SelectChainsPage(compose)

        selectChains.waitUntilShown()
        selectChains.toggleChain(chainName)

        scenario.back()

        accounts.waitChain(chainName)

        // disable chain
        accounts.chooseChains()

        selectChains.waitUntilShown()
        selectChains.toggleChain(chainName)

        scenario.back()

        accounts.waitUntilShown()
        accounts.assertNotExist(chainName)
    }

}