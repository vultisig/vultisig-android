package com.vultisig.wallet.data.models

internal enum class AppLanguage(val mainName: String, val engName: String?) {
    EN("English UK", null),
    DE("Deutsch", "German"),
    ES("Espanol", "Spanish"),
    IT("Italiano", "Italian"),
    HR("Hrvatski", "Croatian"), ;

    companion object {
        fun String.fromName(): AppLanguage {
            return when (this) {
                "English UK" -> EN
                "Deutsch" -> DE
                "Espanol" -> ES
                "Italiano" -> IT
                "Hrvatski" -> HR
                else -> error("lang name not defined AppLanguage")
            }
        }
    }
}