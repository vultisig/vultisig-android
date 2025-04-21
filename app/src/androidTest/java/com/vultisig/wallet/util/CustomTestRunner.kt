package com.vultisig.wallet.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.vultisig.wallet.app.VsBaseApplication
import dagger.hilt.android.testing.CustomTestApplication

class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, VultisigTestApplication_Application::class.java.name, context)
    }

}

@CustomTestApplication(VsBaseApplication::class)
interface VultisigTestApplication