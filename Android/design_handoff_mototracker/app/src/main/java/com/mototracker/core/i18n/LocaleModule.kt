package com.mototracker.core.i18n

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module that binds the production [LocaleController] implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocaleModule {

    @Binds
    @Singleton
    abstract fun bindLocaleController(impl: AppCompatLocaleController): LocaleController
}
