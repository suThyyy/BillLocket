/**
 * Copyright (c) 2025 lcaohoanq. All rights reserved.
 * This software is the confidential and proprietary information of lcaohoanq.
 * You shall not disclose such confidential information and shall use it only in
 * accordance with the terms of the license agreement you entered into with lcaohoanq.
 */
package com.billlocket.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.billlocket.app.media.MediaWorkScheduler
import com.billlocket.app.backup.AndroidPortableBackup
import okio.Path.Companion.toPath

@HiltAndroidApp
class MainApplication : Application() {

    @Inject lateinit var portableBackup: AndroidPortableBackup

    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate() {
        super.onCreate()

        // Method 1: Using setSafe (recommended)
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this@MainApplication)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(this@MainApplication, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(this@MainApplication.cacheDir.resolve("image_cache").absolutePath.toPath())
                        .maxSizeBytes(512L * 1024 * 1024) // 512MB
                        .build()
                }
                .crossfade(true)
                .build()
        }

        MediaWorkScheduler.schedule(this)
    }
}
