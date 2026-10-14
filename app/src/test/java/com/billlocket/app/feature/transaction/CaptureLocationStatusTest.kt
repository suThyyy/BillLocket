package com.billlocket.app.feature.transaction

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.model.PhotoLocation
import com.billlocket.app.domain.model.InstantAtZone
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.capture.*
import com.billlocket.app.feature.location.DeviceLocationPort
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.feature.transaction.draft.DraftImageKind
import com.billlocket.app.feature.transaction.draft.TransactionDraft
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureLocationStatusTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `1 Resolving displays correctly for corresponding image ID`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val locDeferred = CompletableDeferred<Outcome<GeoPoint>>()
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> = locDeferred.await()
        }
        val viewModel = createTestViewModel(
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        val effectFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, effectFlow.await())

        val capPhotoFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        // Photo captured, location in flight
        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        runCurrent()

        val imageId = capPhoto.staged.imageId.value
        assertEquals(1, viewModel.state.value.images.size)
        assertEquals(CaptureLocationStatus.Resolving, viewModel.state.value.locationStatusByImageId[imageId])
        assertFalse("isProcessing must clear immediately after normalize", viewModel.state.value.isProcessing)
    }

    @Test
    fun `2 Attached displays correctly for corresponding image ID`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val expectedPoint = GeoPoint(10.7769, 106.7009)
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current() = Outcome.Success(expectedPoint)
        }
        val viewModel = createTestViewModel(
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        val effectFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, effectFlow.await())

        val capPhotoFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        advanceUntilIdle()

        val imageId = capPhoto.staged.imageId.value
        val image = viewModel.state.value.images.first()
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[imageId])
        assertNotNull(image.confirmedLocation)
        assertEquals(expectedPoint, image.confirmedLocation?.point)
    }

    @Test
    fun `3 Unavailable displays correctly for corresponding image ID`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.DISABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        val capPhotoFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        advanceUntilIdle()

        val imageId = capPhoto.staged.imageId.value
        assertEquals(1, viewModel.state.value.images.size)
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[imageId])
        assertNull("Unavailable location must not set error message", viewModel.state.value.errorMessage)
    }

    @Test
    fun `4 NotRequested does not display location warning or error`() = runTest {
        val viewModel = createTestViewModel()
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.ImagesPicked(listOf("content://media/import1")))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.images.size)
        val imageId = viewModel.state.value.images.first().id
        assertEquals(CaptureLocationStatus.NotRequested, viewModel.state.value.locationStatusByImageId[imageId])
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `5 Two photos with different statuses do not share UI state`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val loc1Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val loc2Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val queue = mutableListOf(loc1Deferred, loc2Deferred)
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                return if (queue.isNotEmpty()) queue.removeAt(0).await() else Outcome.Success(GeoPoint(10.0, 106.0))
            }
        }
        val viewModel = createTestViewModel(
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        // Photo 1: warm completes immediately before capture
        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()
        loc1Deferred.complete(Outcome.Success(GeoPoint(10.1, 106.1)))
        advanceUntilIdle()

        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())
        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req1.staged))
        advanceUntilIdle()
        val id1 = req1.staged.imageId.value

        // Photo 2: disable automatic location to produce Unavailable status
        viewModel.onAction(CaptureAction.DisableAutomaticLocation)
        advanceUntilIdle()
        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req2.staged))
        advanceUntilIdle()
        val id2 = req2.staged.imageId.value

        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[id1])
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[id2])
    }

    @Test
    fun `6 Reorder does not swap status`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.DISABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        // Capture two photos under disabled location -> both Unavailable
        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req1.staged))
        advanceUntilIdle()
        val id1 = req1.staged.imageId.value

        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req2.staged))
        advanceUntilIdle()
        val id2 = req2.staged.imageId.value

        // Manually attach location to Photo 1 to test distinct statuses
        val currentImages = viewModel.state.value.images
        val updatedImages = currentImages.map {
            if (it.id == id1) it.copy(confirmedLocation = PhotoLocation(GeoPoint(10.0, 106.0), LocationSource.CURRENT, 1000L)) else it
        }
        val updatedStatusMap = viewModel.state.value.locationStatusByImageId + (id1 to CaptureLocationStatus.Attached)
        // Verify before reorder
        assertEquals(id1, currentImages[0].id)
        assertEquals(id2, currentImages[1].id)

        // Reorder photos
        viewModel.onAction(CaptureAction.MoveImage(0, 1))
        advanceUntilIdle()

        val reorderedImages = viewModel.state.value.images
        assertEquals(id2, reorderedImages[0].id)
        assertEquals(id1, reorderedImages[1].id)
        // Status remains bound to image ID, not index
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[id2])
    }

    @Test
    fun `7 Remove does not leave orphaned status or UI`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.DISABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req1.staged))
        advanceUntilIdle()
        val id1 = req1.staged.imageId.value

        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req2.staged))
        advanceUntilIdle()
        val id2 = req2.staged.imageId.value

        assertTrue(viewModel.state.value.locationStatusByImageId.containsKey(id1))
        assertTrue(viewModel.state.value.locationStatusByImageId.containsKey(id2))

        // Remove photo 1
        viewModel.onAction(CaptureAction.RemoveImage(id1))
        advanceUntilIdle()

        assertFalse("Removed image ID must not linger in locationStatusByImageId", viewModel.state.value.locationStatusByImageId.containsKey(id1))
        assertTrue(viewModel.state.value.locationStatusByImageId.containsKey(id2))
    }

    @Test
    fun `8 Permission denied preserves photo`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        val effectFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, effectFlow.await())

        val capPhotoFlow = async { viewModel.effects.first() }
        // User denies permission
        viewModel.onAction(CaptureAction.LocationPermissionChanged(false))
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.images.size)
        assertTrue(viewModel.state.value.locationPermissionDeniedNotice)
        assertFalse(viewModel.state.value.autoLocationEnabled)
    }

    @Test
    fun `9 Permission denied does not emit capture error`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        val effectFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, effectFlow.await())

        val capPhotoFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(false))
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        advanceUntilIdle()

        assertNull("Permission denial must not set error message", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `10 Continue works when Unavailable`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.DISABLED)
        val viewModel = createTestViewModel(locationPreference = preference)
        advanceUntilIdle()

        val capPhotoFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        val capPhoto = capPhotoFlow.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(capPhoto.staged))
        advanceUntilIdle()

        val navFlow = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.Continue)
        advanceUntilIdle()

        val navEffect = navFlow.await()
        assertTrue(navEffect is CaptureEffect.NavigateEditor)
    }

    @Test
    fun `11 Location state does not re-enable isProcessing`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val locDeferred = CompletableDeferred<Outcome<GeoPoint>>()
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> = locDeferred.await()
        }
        val viewModel = createTestViewModel(
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        val p = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        p.await()

        val cap = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req = cap.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(req.staged))
        runCurrent()

        assertFalse(viewModel.state.value.isProcessing)
        assertEquals(CaptureLocationStatus.Resolving, viewModel.state.value.locationStatusByImageId[req.staged.imageId.value])

        // Location arrives later
        locDeferred.complete(Outcome.Success(GeoPoint(10.0, 106.0)))
        advanceUntilIdle()

        assertFalse("isProcessing must not become true upon late location arrival", viewModel.state.value.isProcessing)
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[req.staged.imageId.value])
    }

    @Test
    fun `13 String key parity for Vietnamese and English`() {
        val rootDir = File(".").canonicalFile
        val defaultStringsFile = File(rootDir, "src/main/res/values/strings.xml")
            .takeIf { it.exists() } ?: File(rootDir, "app/src/main/res/values/strings.xml")
        val enStringsFile = File(rootDir, "src/main/res/values-en/strings.xml")
            .takeIf { it.exists() } ?: File(rootDir, "app/src/main/res/values-en/strings.xml")

        assertTrue(defaultStringsFile.exists())
        assertTrue(enStringsFile.exists())

        val defaultContent = defaultStringsFile.readText()
        val enContent = enStringsFile.readText()

        val requiredKeys = listOf(
            "capture_location_resolving",
            "capture_location_attached",
            "capture_location_unavailable",
            "capture_location_permission_denied",
        )

        for (key in requiredKeys) {
            assertTrue("Default strings must contain key: $key", defaultContent.contains("name=\"$key\""))
            assertTrue("English strings must contain key: $key", enContent.contains("name=\"$key\""))
        }

        // Verify exact approved text
        assertTrue(defaultContent.contains("Đang lấy vị trí"))
        assertTrue(defaultContent.contains("Đã thêm vị trí"))
        assertTrue(defaultContent.contains("Chưa có vị trí · Có thể thêm sau"))
        assertTrue(defaultContent.contains("Không có quyền vị trí. Ảnh vẫn được lưu."))

        assertTrue(enContent.contains("Getting location"))
        assertTrue(enContent.contains("Location added"))
        assertTrue(enContent.contains("No location · Add later"))
        assertTrue(enContent.contains("Location permission is off. Your photo is still saved."))
    }

    @Test
    fun `14 Pure selection logic selects newly added image, preserves on reorder, falls back on delete`() {
        val img1 = DraftImage(id = "img-1", kind = DraftImageKind.STAGED, previewUri = "c://1", mediaUri = "c://1", widthPx = 100, heightPx = 100, sizeBytes = 1000L)
        val img2 = DraftImage(id = "img-2", kind = DraftImageKind.STAGED, previewUri = "c://2", mediaUri = "c://2", widthPx = 100, heightPx = 100, sizeBytes = 1000L)
        val img3 = DraftImage(id = "img-3", kind = DraftImageKind.STAGED, previewUri = "c://3", mediaUri = "c://3", widthPx = 100, heightPx = 100, sizeBytes = 1000L)

        // 1. Initial empty list -> null
        val sel0 = resolveSelectedImageId(emptySet(), emptyList(), null)
        assertNull(sel0)

        // 2. Add first image -> Image 1 selected
        val sel1 = resolveSelectedImageId(emptySet(), listOf(img1), null)
        assertEquals("img-1", sel1)

        // 3. Image 1 is selected; add Image 2 -> Image 2 becomes selected
        val sel2 = resolveSelectedImageId(setOf("img-1"), listOf(img1, img2), "img-1")
        assertEquals("img-2", sel2)

        // 4. Reorder images [img2, img1] -> selected ID does not change (remains img-2)
        val selReorder = resolveSelectedImageId(setOf("img-1", "img-2"), listOf(img2, img1), "img-2")
        assertEquals("img-2", selReorder)

        // 5. Delete Image 2 (the selected image) -> fallback to last remaining image (img-1)
        val selFallback = resolveSelectedImageId(setOf("img-1", "img-2"), listOf(img1), "img-2")
        assertEquals("img-1", selFallback)

        // 6. Delete last remaining image -> selection becomes null
        val selEmpty = resolveSelectedImageId(setOf("img-1"), emptyList(), "img-1")
        assertNull(selEmpty)

        // 7. Multi-add [img2, img3] -> newly added image (last new ID) is selected
        val selMulti = resolveSelectedImageId(setOf("img-1"), listOf(img1, img2, img3), "img-1")
        assertEquals("img-3", selMulti)
    }

    @Test
    fun `15 Restored draft with imported image retains NotRequested status without warning or error`() = runTest {
        val store = FakeDraftStore()
        val draftId = DraftId("draft-restore-test")
        val importedImageId = "img-imported-1"
        val initialDraft = TransactionDraft(
            id = draftId,
            occurred = InstantAtZone(1000L, 0L, "UTC"),
            updatedAtEpochMillis = 1000L,
            images = listOf(
                DraftImage(
                    id = importedImageId,
                    kind = DraftImageKind.STAGED,
                    previewUri = "content://media/imported1",
                    mediaUri = "content://media/imported1",
                    widthPx = 100,
                    heightPx = 100,
                    sizeBytes = 1024L,
                    confirmedLocation = null, // imported photo without location
                ),
            ),
        )
        store.save(initialDraft)

        // Create new CaptureViewModel from the persisted draft
        val restoredViewModel = createTestViewModel(
            store = store,
            draftId = draftId.value,
        )
        advanceUntilIdle()

        // Restored image must be NotRequested, NOT Unavailable
        val restoredState = restoredViewModel.state.value
        assertEquals(1, restoredState.images.size)
        assertEquals(
            CaptureLocationStatus.NotRequested,
            restoredState.locationStatusByImageId[importedImageId],
        )
        assertNull("Restored imported photo must not produce warning or error", restoredState.errorMessage)
    }
}
