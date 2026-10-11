package com.billlocket.app.feature.transaction.ai

import com.billlocket.app.domain.model.BuiltInCategoryIds
import com.billlocket.app.domain.model.Category
import com.billlocket.app.domain.model.TransactionType

object CategoryResolver {
    private val PREFERRED_TARGETS: Map<SemanticCategoryKey, List<String>> = mapOf(
        SemanticCategoryKey.FOOD_DRINK to listOf(BuiltInCategoryIds.FOOD),
        SemanticCategoryKey.CLOTHING_SHOPPING to listOf(BuiltInCategoryIds.CLOTHING, BuiltInCategoryIds.SHOPPING),
        SemanticCategoryKey.TRANSPORT_FUEL to listOf(BuiltInCategoryIds.TRANSPORT, BuiltInCategoryIds.FUEL),
        SemanticCategoryKey.HEALTH to listOf(BuiltInCategoryIds.HEALTH, BuiltInCategoryIds.MEDICINE),
        SemanticCategoryKey.STUDY_WORK to listOf(BuiltInCategoryIds.STUDY, BuiltInCategoryIds.BOOKS, BuiltInCategoryIds.WORK),
        SemanticCategoryKey.ENTERTAINMENT to listOf(BuiltInCategoryIds.ENTERTAINMENT),
        SemanticCategoryKey.HOME to listOf(BuiltInCategoryIds.HOME),
    )

    fun resolve(
        semanticKey: SemanticCategoryKey,
        categories: List<Category>,
        currentType: TransactionType,
    ): Category? {
        // Never resolve an expense category if the transaction type is INCOME
        if (currentType != TransactionType.EXPENSE) return null

        val candidateIds = PREFERRED_TARGETS[semanticKey] ?: return null
        for (targetId in candidateIds) {
            val matched = categories.firstOrNull {
                it.id.value == targetId &&
                    it.type == TransactionType.EXPENSE &&
                    !it.isArchived
            }
            if (matched != null) return matched
        }
        return null
    }
}
