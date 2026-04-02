package com.androidutil.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

class Messages(locale: Locale) {

    private val bundle: ResourceBundle = ResourceBundle.getBundle("messages", locale)

    operator fun get(key: String): String = try {
        bundle.getString(key)
    } catch (_: Exception) {
        key
    }

    fun get(key: String, vararg args: Any): String = try {
        MessageFormat.format(bundle.getString(key), *args)
    } catch (_: Exception) {
        key
    }

    companion object {
        val SUPPORTED_LOCALES = mapOf(
            "tr" to Locale("tr"),
            "en" to Locale("en")
        )

        fun forLanguage(lang: String): Messages {
            val locale = SUPPORTED_LOCALES[lang.lowercase()] ?: SUPPORTED_LOCALES["tr"]!!
            return Messages(locale)
        }
    }
}
