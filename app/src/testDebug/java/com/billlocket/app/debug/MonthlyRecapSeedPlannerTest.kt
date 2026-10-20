package com.billlocket.app.debug

import com.billlocket.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyRecapSeedPlannerTest {
    private val imageNames = (1..49).map { index -> "photo-${index.toString().padStart(2, '0')}.jpg" }

    @Test fun `plan creates two complete demo months deterministically`() {
        val plan = MonthlyRecapSeedPlanner.plan(imageNames.reversed())

        assertEquals(98, plan.transactions.size)
        assertEquals(98, plan.totalPhotosPlanned)
        assertEquals(49, plan.availableImagesCount)
        assertFalse(plan.isWarningFewImages)
        assertEquals(49, plan.transactions.count { it.localDate.monthValue == 9 })
        assertEquals(49, plan.transactions.count { it.localDate.monthValue == 10 })
        assertTrue(plan.transactions.all { it.localDate.year == 2026 })
        assertTrue(plan.transactions.all { it.occurred.zoneId == "Asia/Ho_Chi_Minh" })
        assertEquals(2, plan.transactions.count { it.type == TransactionType.INCOME })
        assertEquals("photo-01.jpg", plan.transactions.first().photos.single().fileName)
    }

    @Test fun `generated transactions have no note and use full Hanoi street addresses`() {
        val plan = MonthlyRecapSeedPlanner.plan(imageNames)
        val locations = plan.transactions.flatMap { transaction ->
            transaction.photos.map { photo -> photo.location.name }
        }

        assertTrue(plan.transactions.all { it.note == null })
        assertEquals(20, locations.toSet().size)
        assertTrue(locations.all { address ->
            address.any(Char::isDigit) && address.count { it == ',' } >= 2 && address.endsWith("Hà Nội")
        })
    }

    @Test fun `plan requires exactly the complete 49 image set`() {
        val emptyError = assertThrows(IllegalArgumentException::class.java) {
            MonthlyRecapSeedPlanner.plan(emptyList())
        }
        val incompleteError = assertThrows(IllegalArgumentException::class.java) {
            MonthlyRecapSeedPlanner.plan(imageNames.dropLast(1))
        }

        assertTrue(emptyError.message.orEmpty().contains("Không tìm thấy ảnh demo"))
        assertTrue(incompleteError.message.orEmpty().contains("đúng 49 ảnh"))
    }

    @Test fun `legacy marker detection remains available for cleaning old demo data`() {
        assertTrue(MonthlyRecapSeedPlanner.isDemoTransaction("[${MonthlyRecapSeedPlanner.DEMO_MARKER}]"))
        assertFalse(MonthlyRecapSeedPlanner.isDemoTransaction(null))
        assertFalse(MonthlyRecapSeedPlanner.isDemoTransaction("Giao dịch cá nhân"))
    }
}
