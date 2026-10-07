package com.billlocket.app.di

import com.billlocket.app.domain.port.WidgetUpdatePort
import com.billlocket.app.domain.port.NotificationPort
import com.billlocket.app.notification.AndroidNotificationPort
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.widget.AndroidWidgetUpdatePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeferredFeaturePortsModule {
    @Provides
    @Singleton
    fun provideWidgetUpdatePort(implementation: AndroidWidgetUpdatePort): WidgetUpdatePort = implementation

    @Provides
    @Singleton
    fun provideNotificationPort(implementation: AndroidNotificationPort): NotificationPort = implementation
}
