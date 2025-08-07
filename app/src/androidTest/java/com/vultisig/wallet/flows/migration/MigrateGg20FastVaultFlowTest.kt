@file:OptIn(ExperimentalTestApi::class)

package com.vultisig.wallet.flows.migration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.rule.IntentsRule
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.fast.FastVaultModule
import com.vultisig.wallet.data.usecases.fast.VerifyFastVaultBackupCodeUseCase
import com.vultisig.wallet.ui.components.textField
import com.vultisig.wallet.ui.flows.FastVaultKeygenFlow
import com.vultisig.wallet.ui.flows.FastVaultKeygenFlow.Companion.TEST_VAULT_EMAIL
import com.vultisig.wallet.ui.flows.FastVaultKeygenFlow.Companion.TEST_VAULT_PASSWORD
import com.vultisig.wallet.ui.pages.home.VaultAccountsPage
import com.vultisig.wallet.ui.pages.keygen.BackupVaultPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultEmailPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultVerificationPage
import com.vultisig.wallet.ui.pages.onboarding.VaultBackupOnboardingPage
import com.vultisig.wallet.ui.screens.backup.BackupPasswordRequestScreenTags
import com.vultisig.wallet.ui.screens.migration.MigrationOnboardingScreenTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown
import com.vultisig.wallet.util.CleanTest
import com.vultisig.wallet.util.intendingBackup
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Ignore
@UninstallModules(FastVaultModule::class)
@HiltAndroidTest
class MigrateGg20FastVaultFlowTest : CleanTest() {

    @get:Rule(order = LAST_ORDER_INDEX + 1)
    val intents = IntentsRule()

    @Inject
    lateinit var secrets: SecretSettingsRepository

    @Inject
    lateinit var vaults: VaultRepository

    @BindValue
    @JvmField
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
    fun testMigrationFromGg20() {
        val vaultName = FastVaultKeygenFlow.TEST_VAULT_NAME

        // generate new gg20 fast vault
        isDklsEnabled = false
        FastVaultKeygenFlow(compose)
            .execute(vaultName = vaultName)

        // migrate vault
        val accounts = VaultAccountsPage(compose)

        accounts.waitUntilShown()
        accounts.migrate()

        compose.waitUntilShown(MigrationOnboardingScreenTags.NEXT)
        compose.click(MigrationOnboardingScreenTags.NEXT)
        compose.click(MigrationOnboardingScreenTags.NEXT)


        compose.waitUntilShown("InputPasswordScreen.password")
        compose.textField("InputPasswordScreen.password")
            .performTextInput(TEST_VAULT_PASSWORD)

        compose.click("InputPasswordScreen.next")

        val email = FastVaultEmailPage(compose)

        email.waitUntilShown()
        email.inputEmail(TEST_VAULT_EMAIL)
        email.next()


        // wait until migration is finished; 1 minute max
        val backupOnboarding = VaultBackupOnboardingPage(compose)

        backupOnboarding.waitUntilShown(60.seconds)
        backupOnboarding.skipMigration()

        // input code
        val verification = FastVaultVerificationPage(compose)

        verification.waitUntilShown()
        verification.inputCode("0000")

        // backup vault
        val backupVault = BackupVaultPage(compose)

        backupVault.waitUntilShown()
        backupVault.backupNow()

        intendingBackup()

        compose.waitUntilShown(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD)
        compose.click(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD)

        // assume it's home screens toolbar title with newly created vault
        compose.waitUntilAtLeastOneExists(hasText(vaultName), timeoutMillis = 10_000)

        val migratedVault = runBlocking {
            vaults.getAll()
        }.find { it.name == vaultName }

        // check if vault is migrated to dkls
        Assert.assertEquals(migratedVault?.libType, SigningLibType.DKLS)
    }

}