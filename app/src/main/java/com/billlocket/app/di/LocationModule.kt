package com.billlocket.app.di

import com.billlocket.app.feature.location.AddressResolver
import com.billlocket.app.feature.location.AndroidAddressResolver
import com.billlocket.app.feature.location.AndroidDeviceLocationPort
import com.billlocket.app.feature.location.DeviceLocationPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds @Singleton abstract fun bindDeviceLocation(implementation: AndroidDeviceLocationPort): DeviceLocationPort
    @Binds @Singleton abstract fun bindAddressResolver(implementation: AndroidAddressResolver): AddressResolver
}
