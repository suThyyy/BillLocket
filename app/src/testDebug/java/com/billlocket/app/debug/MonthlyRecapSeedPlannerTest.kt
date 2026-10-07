package com.billlocket.app.debug

import com.billlocket.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonthlyRecapSeedPlannerTest {

    private val sampleImages = (1..30).map { "image_%02d.jpg".format(it) }

    @Test
    fun plan_containsOnlySeptember2026Dates() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        assertTrue("Plan should contain transactions", plan.transactions.isNotEmpty())
        plan.transactions.forEach { tx ->
            assertEquals(2026, tx.localDate.year)
            assertEquals(9, tx.localDate.monthValue)
            assertEquals("Asia/Ho_Chi_Minh", tx.occurred.zoneId)
            assertTrue(tx.localDate.dayOfMonth in 1..30)
        }
    }

    @Test
    fun plan_designatesSeptember20AsMostActiveDay() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        assertEquals(LocalDate.of(2026, 9, 20), plan.mostActiveDate)
        assertEquals(3, plan.mostActiveDateTransactionCount)

        val transactionsPerDay = plan.transactions.groupBy { it.localDate }
        val daysWithThreeTransactions = transactionsPerDay.filter { it.value.size >= 3 }

        assertEquals(1, daysWithThreeTransactions.size)
        assertEquals(LocalDate.of(2026, 9, 20), daysWithThreeTransactions.keys.first())
    }

    @Test
    fun plan_categoryAndTypeCompatibility() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        val incomeTransactions = plan.transactions.filter { it.type == TransactionType.INCOME }
        val expenseTransactions = plan.transactions.filter { it.type == TransactionType.EXPENSE }

        assertTrue("Should have at least 1 income transaction", incomeTransactions.isNotEmpty())
        assertTrue("Should have multiple expense transactions", expenseTransactions.size >= 10)

        incomeTransactions.forEach { tx ->
            assertEquals("Lương", tx.preferredCategoryName)
            assertTrue(tx.amountMinor > 0)
        }

        expenseTransactions.forEach { tx ->
            assertTrue(tx.amountMinor > 0)
        }
    }

    @Test
    fun plan_foodIsLeadingExpenseCategory() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        val expenseByCategory = plan.transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.preferredCategoryName }
            .mapValues { entry -> entry.value.sumOf { it.amountMinor } }

        val topCategory = expenseByCategory.maxByOrNull { it.value }

        assertEquals("Ăn uống", topCategory?.key)
        assertTrue(topCategory!!.value >= 3_000_000L)
    }

    @Test
    fun plan_rotatesTenDistinctFakeHanoiLocations() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        val allPhotos = plan.transactions.flatMap { it.photos }
        val uniqueLocations = allPhotos.map { it.location.name }.toSet()

        assertEquals(10, uniqueLocations.size)
        MonthlyRecapSeedPlanner.FAKE_LOCATIONS.forEach { loc ->
            assertTrue(uniqueLocations.contains(loc.name))
        }

        // Verify specific coordinates
        val hhk = allPhotos.first { it.location.name == "Hồ Hoàn Kiếm" }.location
        assertEquals(21.0285, hhk.latitude, 0.0001)
        assertEquals(105.8542, hhk.longitude, 0.0001)

        val tayHo = allPhotos.first { it.location.name == "Tây Hồ" }.location
        assertEquals(21.0583, tayHo.latitude, 0.0001)
        assertEquals(105.8120, tayHo.longitude, 0.0001)
    }

    @Test
    fun plan_sortsFilenamesDeterministically() {
        val unsorted = listOf("z_photo.jpg", "a_photo.jpg", "m_photo.jpg", "b_photo.jpg")
        val plan = MonthlyRecapSeedPlanner.plan(unsorted)

        val assignedFilenames = plan.transactions.flatMap { it.photos }.map { it.fileName }

        assertEquals(listOf("a_photo.jpg", "b_photo.jpg", "m_photo.jpg", "z_photo.jpg"), assignedFilenames)
    }

    @Test
    fun plan_includesIdempotencyMarkerInAllNotes() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        plan.transactions.forEach { tx ->
            assertTrue(
                "Note must contain demo marker: ${tx.note}",
                MonthlyRecapSeedPlanner.isDemoTransaction(tx.note),
            )
        }

        assertFalse(MonthlyRecapSeedPlanner.isDemoTransaction("User transaction without marker"))
        assertFalse(MonthlyRecapSeedPlanner.isDemoTransaction(null))
    }

    @Test
    fun plan_handlesFewerThanSixImagesWithWarning() {
        val smallBatch = listOf("photo1.jpg", "photo2.jpg", "photo3.jpg")
        val plan = MonthlyRecapSeedPlanner.plan(smallBatch)

        assertTrue(plan.isWarningFewImages)
        assertEquals(3, plan.totalPhotosPlanned)
        assertEquals(20, plan.transactions.size)
    }

    @Test
    fun plan_failsSafelyOnEmptyImages() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            MonthlyRecapSeedPlanner.plan(emptyList())
        }
        assertTrue(exception.message!!.contains("Không tìm thấy ảnh demo"))
    }

    @Test
    fun plan_capsImagesAtMaximumBound() {
        val largeBatch = (1..100).map { "img_%03d.jpg".format(it) }
        val plan = MonthlyRecapSeedPlanner.plan(largeBatch)

        assertEquals(MonthlyRecapSeedPlanner.MAX_IMAGE_BOUND, plan.totalPhotosPlanned)
        assertEquals(22, plan.totalPhotosPlanned)
    }

    @Test
    fun plan_containsMultiPhotoAndNoPhotoTransactions() {
        val plan = MonthlyRecapSeedPlanner.plan(sampleImages)

        val noPhotoTxs = plan.transactions.filter { it.photos.isEmpty() }
        val multiPhotoTxs = plan.transactions.filter { it.photos.size > 1 }

        assertTrue("Must have at least 2 transactions without photos (found ${noPhotoTxs.size})", noPhotoTxs.size >= 2)
        assertTrue("Must have at least 1 multi-photo transaction (found ${multiPhotoTxs.size})", multiPhotoTxs.isNotEmpty())

        val sept20Txs = plan.transactions.filter { it.localDate == LocalDate.of(2026, 9, 20) }
        val maxPhotosOnSept20 = sept20Txs.maxOf { it.photos.size }
        assertEquals(3, maxPhotosOnSept20)
    }
}
