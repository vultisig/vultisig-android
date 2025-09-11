package com.vultisig.wallet.data.models.settings

enum class AppLanguage(val mainName: String, val engName: String?) {
    EN("English UK", null),
    DE("Deutsch", "German"),
    ES("Espanol", "Spanish"),
    IT("Italiano", "Italian"),
    HR("Hrvatski", "Croatian"),
    RU("Русский", "Russian"),
    NL("Nederlands", "Dutch"),
    PT("Português", "Portuguese"),;

    companion object {
        fun String.fromName(): AppLanguage {
            return when (this) {
                "English UK" -> EN
                "English" -> EN
                "Deutsch" -> DE
                "Espanol" -> ES
                "Italiano" -> IT
                "Hrvatski" -> HR
                "Русский" -> RU
                "Nederlands" -> NL
                "Português" -> PT
                else -> error("lang name not defined AppLanguage")
            }
        }
    }
}