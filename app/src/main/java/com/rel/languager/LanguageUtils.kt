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
        val localeList = Locale.getAvailableLocales().toMutableList()

        // Filter out locales with empty language tags and sort by display name
        val filteredList = localeList.filter { it.toLanguageTag().isNotEmpty() && it.toLanguageTag() != "und" }
        val sortedList = filteredList.sortedBy { it.getDisplayName(Locale.getDefault()) }

        // Add default locale at the beginning
        val resultList = mutableListOf<Locale>()
        resultList.add(Locale.getDefault())
        resultList.addAll(sortedList)

        return resultList.distinct().toMutableList()
    }
}
