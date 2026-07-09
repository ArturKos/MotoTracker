package com.mototracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * Hilt module providing the [DataStore]<[Preferences]> singleton used by
 * [com.mototracker.data.settings.SettingsDataStore].
 *
 * The `preferencesDataStore` delegate on [Context] guarantees that only one
 * DataStore instance is ever created per process for the "settings" file.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    /**
     * Provides the singleton [DataStore]<[Preferences]> backed by the "settings"
     * preferences file in internal storage.
     *
     * @param context Application context (injected by Hilt).
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
