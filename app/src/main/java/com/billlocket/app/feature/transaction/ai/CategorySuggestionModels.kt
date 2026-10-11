package com.billlocket.app.feature.transaction.ai

import com.billlocket.app.domain.model.CategoryId

enum class SemanticCategoryKey {
    FOOD_DRINK,
    CLOTHING_SHOPPING,
    TRANSPORT_FUEL,
    HEALTH,
    STUDY_WORK,
    ENTERTAINMENT,
    HOME,
}

data class CategorySuggestion(
    val semanticKey: SemanticCategoryKey,
    val confidence: Float,
    val supportingLabels: List<String>,
)

data class RecognizedLabel(
    val text: String,
    val confidence: Float,
)

interface ImageCategorySuggestionEngine {
    suspend fun suggest(imageUri: String): CategorySuggestion?
}

sealed interface CategorySuggestionState {
    data object NotRequested : CategorySuggestionState
    data class Analyzing(val imageKey: String) : CategorySuggestionState
    data class Suggested(
        val imageKey: String,
        val suggestion: CategorySuggestion,
        val resolvedCategoryId: CategoryId,
    ) : CategorySuggestionState
    data class NoSuggestion(val imageKey: String) : CategorySuggestionState
    data class Dismissed(val imageKey: String) : CategorySuggestionState
    data class Failed(val imageKey: String) : CategorySuggestionState
}
