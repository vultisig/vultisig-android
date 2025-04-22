package com.vultisig.wallet.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File

fun clearAppData() {
    val context: Context = ApplicationProvider.getApplicationContext()

    // 1. Clear SharedPreferences
    val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
    if (sharedPrefsDir.exists()) {
        sharedPrefsDir.listFiles()?.forEach { it.delete() }
    }

    // 2. Clear app's internal databases
    val dbDir = File(context.applicationInfo.dataDir, "databases")
    if (dbDir.exists()) {
        dbDir.listFiles()?.forEach { dbFile ->
            if (dbFile.name.endsWith(".db")) {
                context.deleteDatabase(dbFile.name)
            } else {
                dbFile.delete()
            }
        }
    }

    // 3. Clear files directory
    context.filesDir?.deleteRecursively()

    // 4. Clear cache
    context.cacheDir?.deleteRecursively()

    // 5. Clear DataStore (preferences) if used
    val dataStoreFile = File(context.filesDir, "datastore")
    dataStoreFile.deleteRecursively()

    // 6. Clear external cache
    context.externalCacheDir?.deleteRecursively()
}