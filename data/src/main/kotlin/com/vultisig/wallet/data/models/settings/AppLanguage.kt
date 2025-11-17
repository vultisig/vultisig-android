package com.vultisig.wallet.data.models.settings

enum class AppLanguage(val mainName: String, val engName: String?, val localeCode: String) {
    EN("English", "(UK)", "en-GB"),
    DE("Deutsch", "German", "de"),
    ES("Espanol", "Spanish", "es"),
    IT("Italiano", "Italian", "it"),
    HR("Hrvatski", "Croatian", "hr"),
    RU("Русский", "Russian", "ru"),
    NL("Nederlands", "Dutch", "nl"),
    PT("Português", "Portuguese", "pt"),
    ZH_CN("简体中文", "Chinese (Simplified)", "zh-CN");

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