package com.example.practica3.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

object Keys {
    val THEME = stringPreferencesKey("theme") // "guinda" | "azul"
    val DARK  = booleanPreferencesKey("dark") // true/false
}

suspend fun Context.setThemeName(name: String) {
    dataStore.edit { it[Keys.THEME] = name }
}
suspend fun Context.setDark(enabled: Boolean) {
    dataStore.edit { it[Keys.DARK] = enabled }
}
suspend fun Context.getThemeName(): String =
    dataStore.data.map { it[Keys.THEME] ?: "guinda" }.first()
suspend fun Context.isDark(): Boolean =
    dataStore.data.map { it[Keys.DARK] ?: false }.first()
