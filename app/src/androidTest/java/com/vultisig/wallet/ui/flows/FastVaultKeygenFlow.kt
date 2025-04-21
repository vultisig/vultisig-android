@file:OptIn(ExperimentalTestApi::class)

package com.vultisig.wallet.ui.flows

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.common.provideFileUri
import com.vultisig.wallet.data.usecases.MIME_TYPE_VAULT
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
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Starts from a clean app and passes fast vault generation flow until home screen.
 */
class FastVaultKeygenFlow(
    private val compose: ComposeTestRule,
) {

    fun execute() {
        val vaultName = "aqa test vault 123"

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
        email.inputEmail("test@email.com")
        email.next()

        val password = FastVaultPasswordPage(compose)

        password.waitUntilShown()
        password.inputPassword("password123")
        password.inputConfirmPassword("password123")
        password.next()

        val hint = FastVaultHintPage(compose)

        hint.waitUntilShown()
        hint.skip()

        // wait until keygen is finished; 1 minute max
        val backupOnboarding = VaultBackupOnboardingPage(compose)

        backupOnboarding.waitUntilShown(60.seconds)
        backupOnboarding.skip()

        // input code
        val verification = FastVaultVerificationPage(compose)

        verification.waitUntilShown()
        verification.inputCode("0000")

        // backup vault
        val backupVault = BackupVaultPage(compose)

        backupVault.waitUntilShown()
        backupVault.backupNow()

        // create backup file & send respond with it to create document intent
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = context.provideFileUri(
            File(context.cacheDir, "backup.vult")
                .apply { createNewFile() }
        )

        val result = Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent().apply {
                setDataAndType(uri, MIME_TYPE_VAULT)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )

        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(result)

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

}