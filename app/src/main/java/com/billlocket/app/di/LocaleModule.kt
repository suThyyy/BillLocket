package com.billlocket.app.di

import com.billlocket.app.locale.AppLocaleController
import com.billlocket.app.locale.AppLocaleControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** BL-045 — Binds the locale controller implementation to its interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocaleModule {

    @Binds
    @Singleton
    abstract fun bindLocaleController(impl: AppLocaleControllerImpl): AppLocaleController
}
