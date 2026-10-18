package com.billlocket.app.feature.transaction

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.feature.location.DeviceLocationPort
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.capture.*
import com.billlocket.app.feature.transaction.draft.TransactionDraft
import com.billlocket.app.feature.transaction.draft.TransactionDraftStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `permission lens flash and zoom actions update state`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.PermissionChanged(true))
        viewModel.onAction(CaptureAction.ToggleLens)
        viewModel.onAction(CaptureAction.ToggleFlash)
        viewModel.onAction(CaptureAction.ZoomChanged(0.75f))

        assertEquals(CameraPermissionState.GRANTED, viewModel.state.value.permission)
        assertEquals(CameraLens.FRONT, viewModel.state.value.lens)
        assertTrue(viewModel.state.value.flashEnabled)
        assertEquals(0.75f, viewModel.state.value.zoomLinear)
    }

    @Test
    fun `captured image is normalized persisted and continues once`() = runTest {
        val store = FakeDraftStore()
        val media = FakeMediaStore()
        val viewModel = viewModel(store, media)
        advanceUntilIdle()

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        viewModel.onAction(CaptureAction.DisableAutomaticLocation)
        advanceUntilIdle()
        val request = captureEffect.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.images.size)
        assertEquals(1, store.value?.images?.size)

        val navigation = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.Continue)
        advanceUntilIdle()
        assertEquals(DraftId("draft-1"), (navigation.await() as CaptureEffect.NavigateEditor).draftId)
    }

    @Test
    fun `imports at most ten images and ignores further capture requests`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.ImagesPicked((1..12).map { "content://media/import-$it" }))
        advanceUntilIdle()

        assertEquals(MAX_CAPTURE_IMAGES, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)

        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showLocationConsent)
        assertEquals(MAX_CAPTURE_IMAGES, viewModel.state.value.images.size)
    }

    @Test
    fun `enabled automatic location attaches current point without changing import policy`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val viewModel = viewModel(locationPreference = preference)
        advanceUntilIdle()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        val location = viewModel.state.value.images.single().confirmedLocation
        assertEquals(10.0, location?.point?.latitude ?: 0.0, 0.0)
        assertEquals(106.0, location?.point?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `warmed location already succeeded attaches immediately without delay`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = object : DeviceLocationPort {
                override suspend fun current() = Outcome.Success(GeoPoint(21.033, 105.771))
            },
        )
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        runCurrent()

        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        val img = viewModel.state.value.images.single()
        assertEquals(21.033, img.confirmedLocation?.point?.latitude ?: 0.0, 0.001)
        assertEquals(105.771, img.confirmedLocation?.point?.longitude ?: 0.0, 0.001)
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[img.id])
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(21.033, store.value?.images?.single()?.confirmedLocation?.point?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun `warm request in flight times out after background window without blocking thumbnail`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val slowLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                delay(CaptureViewModel.WARMED_LOCATION_TIMEOUT_MILLIS + 500)
                return Outcome.Success(GeoPoint(21.033, 105.771))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = slowLocation,
        )
        runCurrent()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        runCurrent()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        runCurrent()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        runCurrent()

        // Image and thumbnail are available immediately, isProcessing is cleared
        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        val imgId = viewModel.state.value.images.single().id
        assertNull(viewModel.state.value.images.single().confirmedLocation)
        assertEquals(CaptureLocationStatus.Resolving, viewModel.state.value.locationStatusByImageId[imgId])

        // The photo stays usable while the location warm-up continues in background.
        advanceTimeBy(CaptureViewModel.WARMED_LOCATION_TIMEOUT_MILLIS - 1)
        runCurrent()
        assertEquals(CaptureLocationStatus.Resolving, viewModel.state.value.locationStatusByImageId[imgId])
        assertNull(viewModel.state.value.images.single().confirmedLocation)

        // The bounded background window eventually exposes the recoverable fallback.
        advanceTimeBy(1)
        runCurrent()
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[imgId])
        assertEquals(1, viewModel.state.value.images.size)
        assertNull(viewModel.state.value.images.single().confirmedLocation)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `warm request completing inside background window attaches location to image`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val quickLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                delay(100)
                return Outcome.Success(GeoPoint(21.028, 105.804))
            }
        }
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = quickLocation,
        )
        runCurrent()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        runCurrent()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        runCurrent()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        runCurrent()

        val imgId = viewModel.state.value.images.single().id
        assertEquals(CaptureLocationStatus.Resolving, viewModel.state.value.locationStatusByImageId[imgId])
        assertNull(viewModel.state.value.images.single().confirmedLocation)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[imgId])
        val location = viewModel.state.value.images.single().confirmedLocation
        assertEquals(21.028, location?.point?.latitude ?: 0.0, 0.001)
        assertEquals(21.028, store.value?.images?.single()?.confirmedLocation?.point?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun `location provider failure does not emit capture error and keeps image`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val failingLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> =
                Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("gps failed"))
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = failingLocation,
        )
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        assertNull(viewModel.state.value.images.single().confirmedLocation)
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[viewModel.state.value.images.single().id])
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `user denies location permission keeps image intact without error`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.UNKNOWN)
        val viewModel = viewModel(locationPreference = preference)
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showLocationConsent)

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.autoLocationEnabled)

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        assertNull(viewModel.state.value.images.single().confirmedLocation)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[viewModel.state.value.images.single().id])
    }

    @Test
    fun `image removed before location returns is not recreated`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val slowLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                delay(100)
                return Outcome.Success(GeoPoint(21.033, 105.771))
            }
        }
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = slowLocation,
        )
        runCurrent()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        runCurrent()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        runCurrent()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        runCurrent()

        assertEquals(1, viewModel.state.value.images.size)
        val imageId = viewModel.state.value.images.single().id

        // User removes image while location is resolving at 50 ms
        advanceTimeBy(50)
        runCurrent()
        viewModel.onAction(CaptureAction.RemoveImage(imageId))
        runCurrent()

        assertEquals(0, viewModel.state.value.images.size)
        assertEquals(0, store.value?.images?.size ?: 0)

        // Location finishes at 100 ms
        advanceTimeBy(100)
        runCurrent()

        // Image must NOT be recreated and must leave no status
        assertEquals(0, viewModel.state.value.images.size)
        assertEquals(0, store.value?.images?.size ?: 0)
        assertFalse(viewModel.state.value.locationStatusByImageId.containsKey(imageId))
    }

    @Test
    fun `two rapid captures in quick succession attach results only to their own image IDs`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = object : DeviceLocationPort {
                override suspend fun current() = Outcome.Success(GeoPoint(10.776, 106.700))
            },
        )
        advanceUntilIdle()

        // Warm location completes
        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()

        // Photo 1
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

        // Photo 2
        val p2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p2.await())
        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req2.staged))
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.images.size)
        assertEquals(2, store.value?.images?.size)

        val id1 = req1.staged.imageId.value
        val id2 = req2.staged.imageId.value
        assertNotEquals(id1, id2)

        val img1 = viewModel.state.value.images.first { it.id == id1 }
        val img2 = viewModel.state.value.images.first { it.id == id2 }

        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[id1])
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[id2])
        assertNotNull(img1.confirmedLocation)
        assertNotNull(img2.confirmedLocation)
    }

    @Test
    fun `late location does not overwrite wrong image ID`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        var callCount = 0
        val sequentialLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                callCount++
                return if (callCount == 1) {
                    delay(CaptureViewModel.WARMED_LOCATION_TIMEOUT_MILLIS + 350)
                    Outcome.Success(GeoPoint(11.11, 111.11))
                } else {
                    Outcome.Success(GeoPoint(22.22, 122.22))
                }
            }
        }
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = sequentialLocation,
        )
        runCurrent()

        // Photo 1 captured while call 1 is running
        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        runCurrent()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())

        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        runCurrent()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(req1.staged))
        runCurrent()

        val id1 = req1.staged.imageId.value

        // Photo 1 reaches the bounded background timeout.
        advanceTimeBy(CaptureViewModel.WARMED_LOCATION_TIMEOUT_MILLIS)
        runCurrent()
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[id1])

        // The old request finishes after the timeout.
        advanceTimeBy(350)
        runCurrent()

        // id1 must not have been overwritten with a late attached result
        assertNull(viewModel.state.value.images.first { it.id == id1 }.confirmedLocation)
    }

    @Test
    fun `failure or timeout preserves image and allows Continue`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val failingLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                delay(200)
                return Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("error"))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = failingLocation,
        )
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        runCurrent()

        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        runCurrent()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        runCurrent()

        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        runCurrent()

        // Photo is present immediately
        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        assertNull(viewModel.state.value.errorMessage)

        // The provider reports failure without blocking the captured image.
        advanceTimeBy(200)
        runCurrent()

        assertEquals(1, viewModel.state.value.images.size)
        assertFalse(viewModel.state.value.isProcessing)
        assertNull(viewModel.state.value.errorMessage)

        // Continue works without blocking
        val navigation = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.Continue)
        advanceUntilIdle()
        assertEquals(DraftId("draft-1"), (navigation.await() as CaptureEffect.NavigateEditor).draftId)
    }

    @Test
    fun `no fresh deviceLocation current call from capture completion path`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        var locationRequests = 0
        val failingLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                locationRequests += 1
                return Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("failed"))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = failingLocation,
        )
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()
        assertEquals(1, locationRequests)

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val request = captureEffect.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        // Must NOT have retried location from capture completion path
        assertEquals(1, locationRequests)
        assertEquals(1, viewModel.state.value.images.size)
        assertNull(viewModel.state.value.images.single().confirmedLocation)
        assertEquals(CaptureLocationStatus.Unavailable, viewModel.state.value.locationStatusByImageId[viewModel.state.value.images.single().id])
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `two distinct location requests completing in reverse order attach to correct image IDs`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val loc1Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val loc2Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val deferredQueue = mutableListOf(loc1Deferred, loc2Deferred)
        val sequentialLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                val deferred = if (deferredQueue.isNotEmpty()) deferredQueue.removeAt(0) else CompletableDeferred()
                return deferred.await()
            }
        }
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = sequentialLocation,
        )
        advanceUntilIdle()

        // Stage Image 1: triggers warm request 1 (loc1Deferred)
        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())

        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto

        // Stage Image 2 before either job completes: triggers warm request 2 (loc2Deferred)
        val p2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p2.await())

        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto

        val point1 = GeoPoint(10.123, 106.123)
        val point2 = GeoPoint(20.456, 105.456)

        // Complete Job 2 FIRST (reverse order!)
        loc2Deferred.complete(Outcome.Success(point2))
        runCurrent()

        // Normalize Image 1 while Job 1 is still in flight
        viewModel.onAction(CaptureAction.PhotoCaptured(req1.staged))
        runCurrent()

        // Complete Job 1 SECOND
        loc1Deferred.complete(Outcome.Success(point1))
        runCurrent()

        // Normalize Image 2
        viewModel.onAction(CaptureAction.PhotoCaptured(req2.staged))
        advanceUntilIdle()

        val id1 = req1.staged.imageId.value
        val id2 = req2.staged.imageId.value

        val img1 = viewModel.state.value.images.first { it.id == id1 }
        val img2 = viewModel.state.value.images.first { it.id == id2 }

        assertEquals(point1, img1.confirmedLocation?.point)
        assertEquals(point2, img2.confirmedLocation?.point)
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[id1])
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[id2])
    }

    @Test
    fun `image removed during in-flight location leaves no status on timeout or failure`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val locDeferred = CompletableDeferred<Outcome<GeoPoint>>()
        val viewModel = viewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = object : DeviceLocationPort {
                override suspend fun current() = locDeferred.await()
            },
        )
        advanceUntilIdle()

        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())

        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req = cap1.await() as CaptureEffect.CapturePhoto

        viewModel.onAction(CaptureAction.PhotoCaptured(req.staged))
        runCurrent()

        val imageId = req.staged.imageId.value
        assertEquals(1, viewModel.state.value.images.size)

        // User removes image while location request is still in flight
        viewModel.onAction(CaptureAction.RemoveImage(imageId))
        runCurrent()

        // Now trigger failure / timeout after removal
        locDeferred.complete(Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("timeout")))
        advanceTimeBy(CaptureViewModel.WARMED_LOCATION_TIMEOUT_MILLIS + 10)
        advanceUntilIdle()

        // Assert: images does not contain ID, store does not contain ID, locationStatusByImageId does not contain ID
        assertTrue(viewModel.state.value.images.none { it.id == imageId })
        assertTrue(store.value?.images?.none { it.id == imageId } ?: true)
        assertFalse(viewModel.state.value.locationStatusByImageId.containsKey(imageId))
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `does not warm location before permission is confirmed`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        var currentCalls = 0
        val countingLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                currentCalls++
                return Outcome.Success(GeoPoint(10.0, 106.0))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = countingLocation,
        )
        advanceUntilIdle()

        // 1. Initialized with ENABLED preference, but no permission-confirmed action yet
        assertEquals(0, currentCalls)

        // 2. Permission-confirmed warm action sent
        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        advanceUntilIdle()

        // 3. Called exactly once
        assertEquals(1, currentCalls)
    }

    @Test
    fun `stageCapture failure cancels only its own warm job without affecting other snapshots`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        val loc1Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val loc2Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val deferredQueue = mutableListOf(loc1Deferred, loc2Deferred)
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                val d = if (deferredQueue.isNotEmpty()) deferredQueue.removeAt(0) else CompletableDeferred()
                return suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { d.cancel() }
                    d.invokeOnCompletion { cause ->
                        if (cause != null) cont.cancel(cause)
                        else cont.resumeWith(Result.success(d.getCompleted()))
                    }
                }
            }
        }
        var shouldFailStage = false
        val mediaStore = object : FakeMediaStore() {
            override suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId): Outcome<StagedMedia> {
                return if (shouldFailStage) {
                    Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("disk full"))
                } else {
                    super.stageCapture(operationId, imageId)
                }
            }
        }
        val viewModel = viewModel(
            store = store,
            media = mediaStore,
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        // 1. Stage Photo 1 successfully with in-flight loc1Deferred
        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())
        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        val id1 = req1.staged.imageId.value

        val job1 = viewModel.stagedLocationSnapshots[id1]?.job
        assertNotNull(job1)
        assertTrue(job1?.isActive == true)

        // 2. Prepare Photo 2 with failing stageCapture
        shouldFailStage = true
        val p2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p2.await())
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()

        // 3. Assert: Photo 2's warm job was cancelled, but Photo 1's job is still active!
        assertTrue(loc2Deferred.isCancelled)
        assertTrue(viewModel.lastWarmJob?.isCancelled == true)
        assertFalse(loc1Deferred.isCancelled)
        assertTrue(job1?.isActive == true)
        assertTrue(viewModel.stagedLocationSnapshots.containsKey(id1))
        assertEquals(1, viewModel.stagedLocationSnapshots.size)
    }

    @Test
    fun `CaptureFailed cancels snapshot job of the exact image ID`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val loc1Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val loc2Deferred = CompletableDeferred<Outcome<GeoPoint>>()
        val deferredQueue = mutableListOf(loc1Deferred, loc2Deferred)
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                val d = if (deferredQueue.isNotEmpty()) deferredQueue.removeAt(0) else CompletableDeferred()
                return d.await()
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = deviceLocation,
        )
        advanceUntilIdle()

        // Stage Photo 1
        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())
        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req1 = cap1.await() as CaptureEffect.CapturePhoto
        val id1 = req1.staged.imageId.value

        // Stage Photo 2
        val p2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p2.await())
        val cap2 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req2 = cap2.await() as CaptureEffect.CapturePhoto
        val id2 = req2.staged.imageId.value

        val job1 = viewModel.stagedLocationSnapshots[id1]?.job
        val job2 = viewModel.stagedLocationSnapshots[id2]?.job
        assertTrue(job1?.isActive == true)
        assertTrue(job2?.isActive == true)

        // Photo 1 capture fails
        viewModel.onAction(CaptureAction.CaptureFailed(req1.staged))
        advanceUntilIdle()

        // Assert: Job 1 is cancelled and removed, Job 2 remains active and preserved
        assertTrue(job1?.isCancelled == true)
        assertFalse(viewModel.stagedLocationSnapshots.containsKey(id1))
        assertTrue(job2?.isActive == true)
        assertTrue(viewModel.stagedLocationSnapshots.containsKey(id2))
    }

    @Test
    fun `discard cancels remaining staged snapshot jobs`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val locDeferred = CompletableDeferred<Outcome<GeoPoint>>()
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = object : DeviceLocationPort { override suspend fun current() = locDeferred.await() },
        )
        advanceUntilIdle()

        // Stage Photo
        val p1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, p1.await())
        val cap1 = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        advanceUntilIdle()
        val req = cap1.await() as CaptureEffect.CapturePhoto
        val id = req.staged.imageId.value

        val job = viewModel.stagedLocationSnapshots[id]?.job
        assertTrue(job?.isActive == true)

        // Discard draft
        viewModel.onAction(CaptureAction.DiscardDraft)
        advanceUntilIdle()

        assertTrue(job?.isCancelled == true)
        assertTrue(viewModel.stagedLocationSnapshots.isEmpty())
    }

    private fun viewModel(
        store: FakeDraftStore = FakeDraftStore(),
        media: FakeMediaStore = FakeMediaStore(),
        locationPreference: CaptureLocationPreference = FakeCaptureLocationPreference(),
        deviceLocation: DeviceLocationPort = object : DeviceLocationPort {
            override suspend fun current() = Outcome.Success(GeoPoint(10.0, 106.0))
        },
    ) = createTestViewModel(store, media, locationPreference, deviceLocation)
}

internal fun createTestViewModel(
    store: FakeDraftStore = FakeDraftStore(),
    media: FakeMediaStore = FakeMediaStore(),
    locationPreference: CaptureLocationPreference = FakeCaptureLocationPreference(),
    deviceLocation: DeviceLocationPort = object : DeviceLocationPort {
        override suspend fun current() = Outcome.Success(GeoPoint(10.0, 106.0))
    },
    draftId: String = "draft-1",
) = CaptureViewModel(
    SavedStateHandle(mapOf("draftId" to draftId)),
    store,
    media,
    CameraCaptureTarget { File("capture.jpg") },
    object : PhotoImportSource {
        override suspend fun stage(uri: String, operationId: MediaOperationId, imageId: MediaImageId) =
            media.stageImport(operationId, imageId, "image/jpeg", ByteArrayInputStream(byteArrayOf(1)))
    },
    deviceLocation,
    locationPreference,
    Clock { 1_000L },
)

internal class FakeCaptureLocationPreference(
    private var value: CaptureLocationChoice = CaptureLocationChoice.UNKNOWN,
) : CaptureLocationPreference {
    override fun get() = value
    override fun set(choice: CaptureLocationChoice) { value = choice }
}

internal class FakeDraftStore : TransactionDraftStore {
    var value: TransactionDraft? = null
    override suspend fun load(id: DraftId) = value
    override suspend fun save(draft: TransactionDraft) { value = draft }
    override suspend fun delete(id: DraftId) { value = null }
}

internal open class FakeMediaStore : MediaStorePort {
    override suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId): Outcome<StagedMedia> =
        Outcome.Success(StagedMedia(operationId, imageId))

    override suspend fun stageImport(
        operationId: MediaOperationId,
        imageId: MediaImageId,
        declaredMimeType: String?,
        input: InputStream,
    ) = Outcome.Success(StagedMedia(operationId, imageId))

    override suspend fun inspectDraft(staged: StagedMedia) = Outcome.Success(
        DraftInspection(100, 100, "image/jpeg", 0, false, null, null),
    )

    override suspend fun normalize(staged: StagedMedia) = Outcome.Success(
        NormalizedMedia(
            staged,
            "content://com.billlocket.app.media/images/${staged.imageId.value}",
            100,
            100,
            "image/jpeg",
            100,
            "a".repeat(64),
            null,
            null,
        ),
    )

    override fun previewUri(staged: StagedMedia) =
        "content://com.billlocket.app.media/drafts/${staged.operationId.value}/${staged.imageId.value}"

    override suspend fun discardStaged(staged: StagedMedia) = Outcome.Success(Unit)
    override suspend fun promote(media: NormalizedMedia) = Outcome.Success(media.canonicalUri)
    override suspend fun markCommitted(staged: StagedMedia) = Outcome.Success(Unit)
    override suspend fun exists(canonicalUri: String) = true
    override suspend fun openRead(canonicalUri: String): Outcome<InputStream> = Outcome.Success(ByteArrayInputStream(byteArrayOf(1)))
    override suspend fun moveToTrash(canonicalUri: String, operationId: MediaOperationId) = Outcome.Success(Unit)
    override suspend fun prepareShare(canonicalUri: String) = Outcome.Success(canonicalUri)
    override suspend fun reconcile(nowEpochMillis: Long) = Outcome.Success(MediaReconciliationReport(0, 0, 0, 0, 0))
    override suspend fun measureStorage() = Outcome.Success(MediaStorageUsage(0, 0, 0))
    override suspend fun deleteOwned(uris: List<String>) = Outcome.Success(Unit)
}
