package com.vultisig.wallet.flows.keygen

import androidx.test.espresso.intent.rule.IntentsRule
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.usecases.fast.FastVaultModule
import com.vultisig.wallet.data.usecases.fast.VerifyFastVaultBackupCodeUseCase
import com.vultisig.wallet.ui.flows.FastVaultKeygenFlow
import com.vultisig.wallet.util.CleanTest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@Ignore
@UninstallModules(FastVaultModule::class)
@HiltAndroidTest
class FastVaultKeygenFlowTest : CleanTest() {

    @get:Rule(order = LAST_ORDER_INDEX + 1)
    val intents = IntentsRule()

    @Inject
    lateinit var secrets: SecretSettingsRepository

    @BindValue @JvmField
    val verifyFastVaultBackupCodeUseCase: VerifyFastVaultBackupCodeUseCase =
        VerifyFastVaultBackupCodeUseCase { _, _ -> true }

    private var isDklsEnabled = false
        set(value) {
            field = value
            runBlocking { secrets.setDklsEnabled(value) }
        }

    @Before
    override fun setUp() {
        super.setUp()
    }

    @Test
    fun testDklsKeygenStartingFromOnboarding() {
        isDklsEnabled = true

        FastVaultKeygenFlow(compose)
            .execute()
    }

    @Test
    fun testGg20KeygenStartingFromOnboarding() {
        isDklsEnabled = false

        FastVaultKeygenFlow(compose)
            .execute()
    }

}