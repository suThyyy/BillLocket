package com.billlocket.app.di

import com.billlocket.app.feature.transaction.ai.AndroidMlKitImageCategorySuggestionEngine
import com.billlocket.app.feature.transaction.ai.ImageCategorySuggestionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindImageCategorySuggestionEngine(
        implementation: AndroidMlKitImageCategorySuggestionEngine,
    ): ImageCategorySuggestionEngine
}
