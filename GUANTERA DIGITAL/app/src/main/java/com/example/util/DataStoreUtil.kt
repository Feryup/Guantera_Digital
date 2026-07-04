package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        private val HAS_ACCEPTED_TERMS = booleanPreferencesKey("has_accepted_terms")
        private val USER_NAME = stringPreferencesKey("user_name")
    }

    val hasAcceptedTermsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_ACCEPTED_TERMS] ?: false
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: ""
    }

    suspend fun setHasAcceptedTerms(accepted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_ACCEPTED_TERMS] = accepted
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }
}
