package com.rel.languager

import com.rel.languager.Constants.DEFAULT_LANGUAGE

import java.util.Locale
import android.content.SharedPreferences

object LanguageUtils {
    fun getLanguageForPackage(packageName: String, prefs: SharedPreferences): String {
        return prefs.getString(packageName, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun getAllLanguageMappings(prefs: SharedPreferences): Map<String, String> {
        val map = mutableMapOf<String, String>()

        for ((key, value) in prefs.all) {
            if (value is String && key != Constants.PREF_APP_LANGUAGE_MAP) {
                map[key] = value
            }
        }

        return map
    }

    fun getAvailableLanguages(): List<Locale> {
        val localeList = Locale.getAvailableLocales()

        // Filter out locales with empty language tags and sort by display name
        val filteredList = localeList.filter { it.toLanguageTag().isNotEmpty() && it.toLanguageTag() != "und" }
        val sortedList = filteredList.distinct().sortedBy { it.getDisplayName(Locale.getDefault()) }
        return sortedList.toMutableList().apply {
            sortBy {
                when (it) {
                    Locale.ENGLISH -> 1
                    Locale.CHINESE -> 2
                    Locale.FRENCH -> 3
                    Locale.JAPANESE -> 4
                    Locale.GERMAN -> 5
                    Locale.KOREAN -> 6
                    Locale.ITALIAN -> 7
                    else -> 100
                }
            }
            add(0, Locale.getDefault())
        }
    }
}
