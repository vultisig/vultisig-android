package com.vultisig.wallet.data.models.settings

enum class AppLanguage(val mainName: String, val engName: String?) {
    EN("English", "(UK)"),
    DE("Deutsch", "German"),
    ES("Espanol", "Spanish"),
    IT("Italiano", "Italian"),
    HR("Hrvatski", "Croatian"),
    RU("Русский", "Russian"),
    NL("Nederlands", "Dutch"),
    PT("Português", "Portuguese"),
    ZH_CN("简体中文", "Chinese (Simplified)") {
        override fun toString(): String = "zh-CN"
    };

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
                "简体中文" -> ZH_CN
                else -> error("lang name not defined AppLanguage")
            }
        }
    }
}