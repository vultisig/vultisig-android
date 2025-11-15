package com.vultisig.wallet.util

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.vultisig.wallet.data.common.provideFileUri
import com.vultisig.wallet.data.usecases.MIME_TYPE_VAULT
import kotlinx.coroutines.runBlocking
import java.io.File

fun intendingBackup() = runBlocking {
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
}