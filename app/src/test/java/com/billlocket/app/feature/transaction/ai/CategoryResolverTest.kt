package com.billlocket.app.feature.transaction.ai

import com.billlocket.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CategoryResolverTest {

    private fun mockCategory(
        idValue: String,
        name: String = "Test Category",
        type: TransactionType = TransactionType.EXPENSE,
        isArchived: Boolean = false,
    ): Category = Category(
        id = CategoryId(idValue),
        name = name,
        type = type,
        colorArgb = 0xFFFFCC00,
        iconKey = "default",
        sortOrder = 0,
        isDefault = false,
        isArchived = isArchived,
    )

    private val allActiveCategories = listOf(
        mockCategory(BuiltInCategoryIds.FOOD, "Different Name English"),
        mockCategory(BuiltInCategoryIds.CLOTHING, "Different Name Vietnamese"),
        mockCategory(BuiltInCategoryIds.TRANSPORT, "Transport"),
        mockCategory(BuiltInCategoryIds.HEALTH, "Health"),
        mockCategory(BuiltInCategoryIds.STUDY, "Study"),
        mockCategory(BuiltInCategoryIds.ENTERTAINMENT, "Entertainment"),
        mockCategory(BuiltInCategoryIds.HOME, "Household"),
    )

    @Test
    fun `resolves by stable UUID for all semantic keys regardless of display names`() {
        assertEquals(
            BuiltInCategoryIds.FOOD,
            CategoryResolver.resolve(SemanticCategoryKey.FOOD_DRINK, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.CLOTHING,
            CategoryResolver.resolve(SemanticCategoryKey.CLOTHING_SHOPPING, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.TRANSPORT,
            CategoryResolver.resolve(SemanticCategoryKey.TRANSPORT_FUEL, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.HEALTH,
            CategoryResolver.resolve(SemanticCategoryKey.HEALTH, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.STUDY,
            CategoryResolver.resolve(SemanticCategoryKey.STUDY_WORK, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.ENTERTAINMENT,
            CategoryResolver.resolve(SemanticCategoryKey.ENTERTAINMENT, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
        assertEquals(
            BuiltInCategoryIds.HOME,
            CategoryResolver.resolve(SemanticCategoryKey.HOME, allActiveCategories, TransactionType.EXPENSE)?.id?.value
        )
    }

    @Test
    fun `returns null if transaction type is INCOME`() {
        val resolved = CategoryResolver.resolve(
            semanticKey = SemanticCategoryKey.FOOD_DRINK,
            categories = allActiveCategories,
            currentType = TransactionType.INCOME,
        )
        assertNull(resolved)
    }

    @Test
    fun `returns null if target category is archived`() {
        val categoriesWithArchived = listOf(
            mockCategory(BuiltInCategoryIds.FOOD, isArchived = true)
        )
        val resolved = CategoryResolver.resolve(
            semanticKey = SemanticCategoryKey.FOOD_DRINK,
            categories = categoriesWithArchived,
            currentType = TransactionType.EXPENSE,
        )
        assertNull(resolved)
    }

    @Test
    fun `returns null if target category is marked as INCOME type`() {
        val categoriesWithIncome = listOf(
            mockCategory(BuiltInCategoryIds.FOOD, type = TransactionType.INCOME)
        )
        val resolved = CategoryResolver.resolve(
            semanticKey = SemanticCategoryKey.FOOD_DRINK,
            categories = categoriesWithIncome,
            currentType = TransactionType.EXPENSE,
        )
        assertNull(resolved)
    }

    @Test
    fun `returns null if target category is missing entirely`() {
        val emptyCategories = emptyList<Category>()
        val resolved = CategoryResolver.resolve(
            semanticKey = SemanticCategoryKey.FOOD_DRINK,
            categories = emptyCategories,
            currentType = TransactionType.EXPENSE,
        )
        assertNull(resolved)
    }

    @Test
    fun `resolves fallback target when preferred primary is missing`() {
        // CLOTHING_SHOPPING prefers CLOTHING, falls back to SHOPPING
        val onlyShopping = listOf(
            mockCategory(BuiltInCategoryIds.SHOPPING, "Shopping")
        )
        val resolved = CategoryResolver.resolve(
            semanticKey = SemanticCategoryKey.CLOTHING_SHOPPING,
            categories = onlyShopping,
            currentType = TransactionType.EXPENSE,
        )
        assertNotNull(resolved)
        assertEquals(BuiltInCategoryIds.SHOPPING, resolved?.id?.value)
    }
}
