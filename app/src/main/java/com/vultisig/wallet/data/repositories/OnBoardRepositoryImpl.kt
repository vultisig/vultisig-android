package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.OnBoardPage
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject


internal class OnBoardRepositoryImpl @Inject constructor(private val appDataStore: AppDataStore) :
    OnBoardRepository {


    override suspend fun saveOnBoardingState(completed: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onBoardingKey] = completed
        }
    }

    override fun readOnBoardingState() =
        appDataStore.readData(onBoardingKey, false)


    override fun onBoardPages() = getOnBoardingPages()


    private companion object PreferencesKey {
        val onBoardingKey = booleanPreferencesKey(name = "on_boarding_completed")
    }
}

private fun getOnBoardingPages() = listOf(
    OnBoardPage(
        image = R.drawable.intro1,
        title = R.string.onboard_intro1_title,
        description = R.string.onboard_intro1_desc
    ), OnBoardPage(
        image = R.drawable.intro2,
        title = R.string.onboard_intro2_title,
        description = R.string.onboard_intro2_desc,
    ), OnBoardPage(
        image = R.drawable.intro3,
        title = R.string.onboard_intro3_title,
        description = R.string.onboard_intro3_desc,
    ), OnBoardPage(
        image = R.drawable.intro4,
        title = R.string.onboard_intro4_title,
        description =R.string.onboard_intro4_desc,
    )
)
