package com.billlocket.app.locale

import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.domain.model.BuiltInCategoryIds
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialNameCatalogTest {
    @Test
    fun `cash account and all built-in category families have localization entries`() {
        assertNotNull(FinancialNameCatalog.account(DatabaseInitializer.INITIAL_ACCOUNT_ID))
        assertNotNull(FinancialNameCatalog.category(BuiltInCategoryIds.FOOD))
        assertNotNull(FinancialNameCatalog.category(BuiltInCategoryIds.CLOTHING))
        assertNotNull(FinancialNameCatalog.category(BuiltInCategoryIds.SALARY))
        assertNotNull(FinancialNameCatalog.category(BuiltInCategoryIds.OTHER_INCOME))
    }

    @Test
    fun `catalog canonical names cover both Vietnamese and English`() {
        val food = requireNotNull(FinancialNameCatalog.category(BuiltInCategoryIds.FOOD))
        val cash = requireNotNull(FinancialNameCatalog.account(DatabaseInitializer.INITIAL_ACCOUNT_ID))

        assertTrue("Ăn uống" in food.canonicalNames)
        assertTrue("Food & Drink" in food.canonicalNames)
        assertTrue("Tiền mặt" in cash.canonicalNames)
        assertTrue("Cash" in cash.canonicalNames)
    }

    @Test
    fun `unknown custom ids are never treated as built-ins`() {
        assertNull(FinancialNameCatalog.category("custom-category"))
        assertNull(FinancialNameCatalog.account("custom-account"))
    }
}
