package com.billlocket.app.feature.location

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.repository.toDomain
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.AccountRepository
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.DeleteTransaction
import com.billlocket.app.domain.usecase.FinalizeTransactionDeletion
import com.billlocket.app.domain.usecase.UndoDeleteTransaction
import com.billlocket.app.feature.transaction.MainDispatcherRule
import com.billlocket.app.feature.transaction.detail.TransactionDetailAction
import com.billlocket.app.feature.transaction.detail.TransactionDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LocationCorrectnessTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun automaticLocationCache_acceptsOnlyVeryRecentAccurateFixes() {
        assertTrue(isEligibleAutomaticLocationCache(ageMillis = 0L, accuracyMeters = 8f))
        assertTrue(isEligibleAutomaticLocationCache(ageMillis = 30_000L, accuracyMeters = 100f))
        assertFalse(isEligibleAutomaticLocationCache(ageMillis = 30_001L, accuracyMeters = 8f))
        assertFalse(isEligibleAutomaticLocationCache(ageMillis = 10 * 60_000L, accuracyMeters = 8f))
        assertFalse(isEligibleAutomaticLocationCache(ageMillis = 5_000L, accuracyMeters = 100.1f))
        assertFalse(isEligibleAutomaticLocationCache(ageMillis = 5_000L, accuracyMeters = null))
    }

    @Test
    fun freshProviderFix_rejectsStaleResultReturnedByCurrentLocationApi() {
        assertTrue(isEligibleFreshProviderFix(ageMillis = 0L, accuracyMeters = 12f))
        assertTrue(isEligibleFreshProviderFix(ageMillis = 30_000L, accuracyMeters = 500f))
        assertFalse(isEligibleFreshProviderFix(ageMillis = 30_001L, accuracyMeters = 12f))
        assertFalse(isEligibleFreshProviderFix(ageMillis = 10 * 60_000L, accuracyMeters = 12f))
        assertFalse(isEligibleFreshProviderFix(ageMillis = 1_000L, accuracyMeters = 500.1f))
        assertFalse(isEligibleFreshProviderFix(ageMillis = 1_000L, accuracyMeters = null))
    }

    // Test A: Known valid coordinate maps through entity -> domain unchanged
    @Test
    fun entityToDomain_preservesLatitudeAndLongitudeUnchanged() {
        val testLat = 21.033243
        val testLng = 105.771583
        val entity = TransactionImageEntity(
            id = "img-test-1",
            transactionId = "tx-test-1",
            contentUri = "content://media/test/1",
            position = 0,
            mimeType = "image/jpeg",
            widthPx = 1080,
            heightPx = 1920,
            sizeBytes = 204800L,
            capturedAt = 1791432616000L,
            latitude = testLat,
            longitude = testLng,
            accuracyMeters = 8.5,
            placeName = "Mỹ Đình",
            locationSource = "CURRENT",
            locationConfirmedAt = 1791432616000L,
            createdAt = 1791432616000L,
            updatedAt = 1791432616000L,
        )

        val domain = entity.toDomain()

        assertNotNull(domain.location)
        assertEquals(testLat, domain.location!!.point.latitude, 0.000001)
        assertEquals(testLng, domain.location!!.point.longitude, 0.000001)
        assertEquals(LocationSource.CURRENT, domain.location!!.source)
        assertEquals("Mỹ Đình", domain.location!!.placeName)
        assertEquals(8.5, domain.location!!.accuracyMeters!!, 0.001)
    }

    // Test B: Latitude and Longitude are never swapped
    @Test
    fun latitudeAndLongitude_areNotSwapped() {
        val lat = 21.033243
        val lng = 105.771583
        val point = GeoPoint(lat, lng)

        assertEquals(lat, point.latitude, 0.000001)
        assertEquals(lng, point.longitude, 0.000001)
        assertNotEquals(point.latitude, point.longitude)

        // LatLng mapping must maintain lat then lng
        val latLng = com.google.android.gms.maps.model.LatLng(point.latitude, point.longitude)
        assertEquals(lat, latLng.latitude, 0.000001)
        assertEquals(lng, latLng.longitude, 0.000001)
    }

    // Test C: Cache key differentiates (lat1, lng1) from (lat1, lng2) and accounts for place name
    @Test
    fun cacheKey_differentiatesCoordinatesAndPlaceNames() {
        val lat1 = 21.0332
        val lng1 = 105.7715
        val lng2 = 105.8542
        val lat2 = 20.9712

        fun cacheKey(p: GeoPoint, place: String? = null): String {
            val placeKey = place?.trim()?.takeIf { it.isNotEmpty() }?.let { ":$it" } ?: ""
            return String.format(Locale.US, "%.4f,%.4f%s", p.latitude, p.longitude, placeKey)
        }

        val key1 = cacheKey(GeoPoint(lat1, lng1))
        val key2 = cacheKey(GeoPoint(lat1, lng2))
        val key3 = cacheKey(GeoPoint(lat2, lng1))
        val key1WithPlace = cacheKey(GeoPoint(lat1, lng1), "Quán Cà Phê")

        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertNotEquals(key1, key1WithPlace)
    }

    // Test G: Invalid latitude/longitude is rejected safely
    @Test
    fun sanityCheck_rejectsInvalidCoordinates() {
        // Valid
        assertTrue(isValidCoordinate(21.0332, 105.7715))
        assertTrue(isValidCoordinate(-33.8688, 151.2093))
        assertTrue(isValidCoordinate(90.0, 180.0))
        assertTrue(isValidCoordinate(-90.0, -180.0))

        // Invalid: Out of bounds
        assertFalse(isValidCoordinate(90.001, 105.0))
        assertFalse(isValidCoordinate(-90.001, 105.0))
        assertFalse(isValidCoordinate(21.0, 180.001))
        assertFalse(isValidCoordinate(21.0, -180.001))

        // Invalid: NaN / Infinite
        assertFalse(isValidCoordinate(Double.NaN, 105.0))
        assertFalse(isValidCoordinate(21.0, Double.NaN))
        assertFalse(isValidCoordinate(Double.POSITIVE_INFINITY, 105.0))
        assertFalse(isValidCoordinate(21.0, Double.NEGATIVE_INFINITY))

        // Invalid: Null Island (0.0, 0.0)
        assertFalse(isValidCoordinate(0.0, 0.0))

        // Invalid: nulls
        assertFalse(isValidCoordinate(null, 105.0))
        assertFalse(isValidCoordinate(21.0, null))
    }

    // Test E & F: Multi-image transaction: primary image address uses primary image coordinates,
    // and detail map preview pin shares the same coordinate
    @Test
    fun multiImageTransaction_primaryImageLocationIsUsedForAddressAndMapPreview() = runTest {
        val locA = PhotoLocation(GeoPoint(21.0332, 105.7715), LocationSource.CURRENT, 1000L, placeName = "Place A")
        val locB = PhotoLocation(GeoPoint(20.9712, 105.7789), LocationSource.CURRENT, 1000L, placeName = "Place B")

        val imgA = TransactionImage(TransactionImageId("img-A"), "content://media/A", 0, "image/jpeg", 100, 100, 100L, 1000L, locA)
        val imgB = TransactionImage(TransactionImageId("img-B"), "content://media/B", 1, "image/jpeg", 100, 100, 100L, 1000L, locB)

        val txId = TransactionId("tx-1")
        val tx = createTestTransaction(txId, listOf(imgA, imgB))

        val resolvedCalls = mutableListOf<GeoPoint>()
        val fakeResolver = object : AddressResolver {
            override suspend fun resolve(point: GeoPoint, existingPlaceName: String?): ResolvedAddress {
                resolvedCalls.add(point)
                return ResolvedAddress(
                    shortAddress = "Address for ${point.latitude}, ${point.longitude}",
                    fullAddress = "Full Address for ${point.latitude}, ${point.longitude}",
                    detailLocation = existingPlaceName,
                    isFallback = false,
                )
            }
        }

        val viewModel = createViewModel(tx, fakeResolver)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(TransactionImageId("img-A"), state.selectedImageId)
        assertEquals(locA, state.selectedLocation)
        assertEquals("Full Address for 21.0332, 105.7715", state.address?.fullAddress)
        // Detail map preview uses state.selectedLocation.point, which must match what was resolved
        assertEquals(state.selectedLocation?.point, resolvedCalls.firstOrNull())

        // Test D: Switching/changing selected image updates selectedLocation and resolves new address
        viewModel.onAction(TransactionDetailAction.SelectImage(TransactionImageId("img-B")))
        advanceUntilIdle()

        val stateB = viewModel.state.value
        assertEquals(TransactionImageId("img-B"), stateB.selectedImageId)
        assertEquals(locB, stateB.selectedLocation)
        assertEquals("Full Address for 20.9712, 105.7789", stateB.address?.fullAddress)
        assertEquals(stateB.selectedLocation?.point, resolvedCalls.lastOrNull())
    }

    @Test
    fun multiImageTransaction_imageWithNoLocationDoesNotDisplayStaleAddressOrPreview() = runTest {
        val locA = PhotoLocation(GeoPoint(21.0332, 105.7715), LocationSource.CURRENT, 1000L, placeName = "Place A")
        val imgA = TransactionImage(TransactionImageId("img-A"), "content://media/A", 0, "image/jpeg", 100, 100, 100L, 1000L, locA)
        val imgNoLoc = TransactionImage(TransactionImageId("img-none"), "content://media/none", 1, "image/jpeg", 100, 100, 100L, 1000L, null)

        val txId = TransactionId("tx-2")
        val tx = createTestTransaction(txId, listOf(imgA, imgNoLoc))

        val fakeResolver = object : AddressResolver {
            override suspend fun resolve(point: GeoPoint, existingPlaceName: String?): ResolvedAddress {
                return ResolvedAddress("Resolved", "Resolved", null, false)
            }
        }

        val viewModel = createViewModel(tx, fakeResolver)
        advanceUntilIdle()

        // Switch to the photo without location
        viewModel.onAction(TransactionDetailAction.SelectImage(TransactionImageId("img-none")))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(TransactionImageId("img-none"), state.selectedImageId)
        assertNull(state.selectedLocation)
        assertNull(state.address)
        assertFalse(state.isResolvingAddress)
    }

    private fun createTestTransaction(id: TransactionId, images: List<TransactionImage>): TransactionDetails {
        return TransactionDetails(
            id = id,
            type = TransactionType.EXPENSE,
            amount = Money(100_000L, CurrencyCode.of("VND")),
            categoryId = CategoryId("cat-1"),
            accountId = AccountId("acc-1"),
            note = "Test note",
            occurred = InstantAtZone(1791432616000L, 20735L, "Asia/Ho_Chi_Minh"),
            images = images,
            deletedAt = null,
        )
    }

    private fun createViewModel(transaction: TransactionDetails, resolver: AddressResolver): TransactionDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("transactionId" to transaction.id.value))
        val fakeTxRepo = java.lang.reflect.Proxy.newProxyInstance(
            TransactionRepository::class.java.classLoader,
            arrayOf(TransactionRepository::class.java),
        ) { _, method, _ ->
            if (method.name.startsWith("observe")) {
                flowOf(transaction)
            } else {
                null
            }
        } as TransactionRepository

        val fakeAccountRepo = java.lang.reflect.Proxy.newProxyInstance(
            AccountRepository::class.java.classLoader,
            arrayOf(AccountRepository::class.java),
        ) { _, method, _ ->
            if (method.name.startsWith("observeAll")) {
                flowOf(listOf(Account(AccountId("acc-1"), "Cash", AccountType.CASH, Money(1000L, CurrencyCode.of("VND")), 0, false)))
            } else {
                null
            }
        } as AccountRepository

        val fakeCategoryRepo = java.lang.reflect.Proxy.newProxyInstance(
            CategoryRepository::class.java.classLoader,
            arrayOf(CategoryRepository::class.java),
        ) { _, method, _ ->
            if (method.name.startsWith("observe")) {
                flowOf(listOf(Category(CategoryId("cat-1"), "Food", TransactionType.EXPENSE, 0L, "food", 0, false, false)))
            } else {
                null
            }
        } as CategoryRepository

        val fakeMediaStore = java.lang.reflect.Proxy.newProxyInstance(
            MediaStorePort::class.java.classLoader,
            arrayOf(MediaStorePort::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "exists" -> true
                else -> null
            }
        } as MediaStorePort

        val fakeWidget = com.billlocket.app.domain.port.WidgetUpdatePort { Outcome.Success(Unit) }
        val fakeClock = Clock { 1000L }

        val deleteTx = DeleteTransaction(fakeTxRepo, fakeWidget, fakeClock)
        val undoDelete = UndoDeleteTransaction(fakeTxRepo, fakeWidget, fakeClock)
        val finalizeDelete = FinalizeTransactionDeletion(fakeTxRepo, fakeMediaStore)

        return TransactionDetailViewModel(
            savedStateHandle = savedStateHandle,
            transactions = fakeTxRepo,
            accounts = fakeAccountRepo,
            categories = fakeCategoryRepo,
            mediaStore = fakeMediaStore,
            deleteTransaction = deleteTx,
            undoDelete = undoDelete,
            finalizeDeletion = finalizeDelete,
            addressResolver = resolver,
        )
    }
}
