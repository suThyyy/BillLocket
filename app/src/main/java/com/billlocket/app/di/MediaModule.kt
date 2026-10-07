package com.billlocket.app.di

import com.billlocket.app.domain.port.MediaCleanupPort
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.port.BackupPort
import com.billlocket.app.backup.AndroidPortableBackup
import com.billlocket.app.media.AndroidMediaStore
import com.billlocket.app.feature.capture.AndroidPhotoImportSource
import com.billlocket.app.feature.capture.CameraCaptureTarget
import com.billlocket.app.feature.capture.PhotoImportSource
import com.billlocket.app.feature.capture.CaptureLocationPreference
import com.billlocket.app.feature.capture.SharedPreferencesCaptureLocationPreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds @Singleton abstract fun bindMediaStore(implementation: AndroidMediaStore): MediaStorePort
    @Binds @Singleton abstract fun bindPhotoImportSource(implementation: AndroidPhotoImportSource): PhotoImportSource
    @Binds @Singleton abstract fun bindCaptureLocationPreference(implementation: SharedPreferencesCaptureLocationPreference): CaptureLocationPreference
    @Binds @Singleton abstract fun bindBackup(implementation: AndroidPortableBackup): BackupPort

    companion object {
        @dagger.Provides
        @Singleton
        fun provideMediaCleanup(mediaStore: MediaStorePort): MediaCleanupPort = mediaStore

        @dagger.Provides
        @Singleton
        fun provideCameraCaptureTarget(mediaStore: AndroidMediaStore): CameraCaptureTarget =
            CameraCaptureTarget(mediaStore::captureOutputFile)
    }
}
