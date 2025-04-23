@file:OptIn(ExperimentalTestApi::class)

package com.vultisig.wallet.ui.flows

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.pages.NameVaultPage
import com.vultisig.wallet.ui.pages.keygen.BackupVaultPage
import com.vultisig.wallet.ui.pages.keygen.ChooseVaultPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultEmailPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultHintPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultPasswordPage
import com.vultisig.wallet.ui.pages.keygen.FastVaultVerificationPage
import com.vultisig.wallet.ui.pages.onboarding.OnboardingPage
import com.vultisig.wallet.ui.pages.onboarding.StartPage
import com.vultisig.wallet.ui.pages.onboarding.SummaryPage
import com.vultisig.wallet.ui.pages.onboarding.VaultBackupOnboardingPage
import com.vultisig.wallet.ui.screens.backup.BackupPasswordRequestScreenTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown
import com.vultisig.wallet.util.intendingBackup
import kotlin.time.Duration.Companion.seconds

/**
 * Starts from a clean app and passes fast vault generation flow until home screen.
 */
class FastVaultKeygenFlow(
    private val compose: ComposeTestRule,
) {

    fun execute(
        vaultName: String = TEST_VAULT_NAME
    ) {
        ActivityScenario.launch(MainActivity::class.java)

        val start = StartPage(compose)

        start.createNewVault()

        val onboarding = OnboardingPage(compose)

        onboarding.waitUntilShown()
        onboarding.skip()

        val summary = SummaryPage(compose)

        summary.waitUntilShown()
        summary.toggleAgreement()
        summary.next()

        val chooseVault = ChooseVaultPage(compose)

        chooseVault.waitUntilShown()
        chooseVault.selectFastVault()
        chooseVault.next()

        val nameVault = NameVaultPage(compose)

        nameVault.waitUntilShown()
        nameVault.inputName(vaultName)
        nameVault.next()

        val email = FastVaultEmailPage(compose)

        email.waitUntilShown()
        email.inputEmail(TEST_VAULT_EMAIL)
        email.next()

        val password = FastVaultPasswordPage(compose)

        password.waitUntilShown()
        password.inputPassword(TEST_VAULT_PASSWORD)
        password.inputConfirmPassword(TEST_VAULT_PASSWORD)
        password.next()

        val hint = FastVaultHintPage(compose)

        hint.waitUntilShown()
        hint.skip()

        // wait until keygen is finished; 1.5 minute max
        val backupOnboarding = VaultBackupOnboardingPage(compose)

        backupOnboarding.waitUntilShown(90.seconds)
        backupOnboarding.skipFastVault()

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


        // agree & pass summary
        summary.waitUntilShown(10.seconds)

        summary.toggleAgreement()

        // required now for some reason (animation?)
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()

        summary.next()

        // assume it's home screens toolbar title with newly created vault
        compose.waitUntilAtLeastOneExists(hasText(vaultName), timeoutMillis = 5000)
    }

    companion object {
        const val TEST_VAULT_NAME = "aqa test vault 123"
        const val TEST_VAULT_EMAIL = "test@email.com"
        const val TEST_VAULT_PASSWORD = "password123"
    }

}