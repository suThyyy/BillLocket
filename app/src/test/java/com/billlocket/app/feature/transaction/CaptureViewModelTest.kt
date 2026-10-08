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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    fun `slow safe location is attached before captured image becomes available`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val slowLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                delay(5_500)
                return Outcome.Success(GeoPoint(21.033243, 105.771583))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = slowLocation,
        )
        advanceUntilIdle()

        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        advanceUntilIdle()
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        val request = captureEffect.await() as CaptureEffect.CapturePhoto
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))
        advanceUntilIdle()

        val location = viewModel.state.value.images.single().confirmedLocation
        assertEquals(21.033243, location?.point?.latitude ?: 0.0, 0.0)
        assertEquals(105.771583, location?.point?.longitude ?: 0.0, 0.0)
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `prefetched location is reused when shutter is pressed`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        var locationRequests = 0
        val delayedLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                locationRequests += 1
                delay(2_000)
                return Outcome.Success(GeoPoint(21.028511, 105.804817))
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = delayedLocation,
        )
        advanceUntilIdle()

        viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, locationRequests)

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

        val location = viewModel.state.value.images.single().confirmedLocation
        assertEquals(21.028511, location?.point?.latitude ?: 0.0, 0.0)
        assertEquals(105.804817, location?.point?.longitude ?: 0.0, 0.0)
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `failed prefetch is retried when shutter is pressed`() = runTest {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        var locationRequests = 0
        val recoveringLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                locationRequests += 1
                return if (locationRequests == 1) {
                    Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.StorageUnavailable("temporary"))
                } else {
                    Outcome.Success(GeoPoint(21.033243, 105.771583))
                }
            }
        }
        val viewModel = viewModel(
            locationPreference = preference,
            deviceLocation = recoveringLocation,
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

        assertTrue(locationRequests >= 2)
        val location = viewModel.state.value.images.single().confirmedLocation
        assertEquals(21.033243, location?.point?.latitude ?: 0.0, 0.0)
        assertEquals(105.771583, location?.point?.longitude ?: 0.0, 0.0)
        assertNull(viewModel.state.value.errorMessage)
    }

    private fun viewModel(
        store: FakeDraftStore = FakeDraftStore(),
        media: FakeMediaStore = FakeMediaStore(),
        locationPreference: CaptureLocationPreference = FakeCaptureLocationPreference(),
        deviceLocation: DeviceLocationPort = object : DeviceLocationPort {
            override suspend fun current() = Outcome.Success(GeoPoint(10.0, 106.0))
        },
    ) = CaptureViewModel(
        SavedStateHandle(mapOf("draftId" to "draft-1")),
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
}

private class FakeCaptureLocationPreference(
    private var value: CaptureLocationChoice = CaptureLocationChoice.UNKNOWN,
) : CaptureLocationPreference {
    override fun get() = value
    override fun set(choice: CaptureLocationChoice) { value = choice }
}

private class FakeDraftStore : TransactionDraftStore {
    var value: TransactionDraft? = null
    override suspend fun load(id: DraftId) = value
    override suspend fun save(draft: TransactionDraft) { value = draft }
    override suspend fun delete(id: DraftId) { value = null }
}

private class FakeMediaStore : MediaStorePort {
    override suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId) =
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
