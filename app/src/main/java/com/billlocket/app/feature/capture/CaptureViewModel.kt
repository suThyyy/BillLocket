package com.billlocket.app.feature.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.port.MediaImageId
import com.billlocket.app.domain.port.MediaOperationId
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.port.StagedMedia
import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.model.PhotoLocation
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.feature.transaction.draft.TransactionDraft
import com.billlocket.app.feature.transaction.draft.TransactionDraftStore
import com.billlocket.app.feature.location.DeviceLocationPort
import com.billlocket.app.domain.model.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class CameraPermissionState { UNKNOWN, GRANTED, DENIED, DENIED_PERMANENTLY }
enum class CameraLens { BACK, FRONT }

data class CaptureState(
    val draftId: DraftId,
    val permission: CameraPermissionState = CameraPermissionState.UNKNOWN,
    val cameraAvailable: Boolean = true,
    val frontCameraAvailable: Boolean = true,
    val flashAvailable: Boolean = true,
    val lens: CameraLens = CameraLens.BACK,
    val flashEnabled: Boolean = false,
    val zoomLinear: Float = 0f,
    val images: List<DraftImage> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val showDiscardConfirmation: Boolean = false,
    val showLocationConsent: Boolean = false,
    val autoLocationEnabled: Boolean = false,
)

sealed interface CaptureAction {
    data class PermissionChanged(val granted: Boolean, val permanentlyDenied: Boolean = false) : CaptureAction
    data class CapabilitiesChanged(
        val cameraAvailable: Boolean,
        val frontCameraAvailable: Boolean,
        val flashAvailable: Boolean,
    ) : CaptureAction
    data object RequestCameraPermission : CaptureAction
    data object PickImages : CaptureAction
    data class ImagesPicked(val uris: List<String>) : CaptureAction
    data object TakePhoto : CaptureAction
    data object EnableAutomaticLocation : CaptureAction
    data object DisableAutomaticLocation : CaptureAction
    data object WarmAutomaticLocation : CaptureAction
    data class LocationPermissionChanged(val granted: Boolean) : CaptureAction
    data class PhotoCaptured(val staged: StagedMedia) : CaptureAction
    data class CaptureFailed(val staged: StagedMedia) : CaptureAction
    data object ToggleLens : CaptureAction
    data object ToggleFlash : CaptureAction
    data class ZoomChanged(val value: Float) : CaptureAction
    data class RemoveImage(val id: String) : CaptureAction
    data class MoveImage(val from: Int, val to: Int) : CaptureAction
    data object Continue : CaptureAction
    data object Back : CaptureAction
    data object KeepDraft : CaptureAction
    data object DiscardDraft : CaptureAction
}

sealed interface CaptureEffect {
    data object LaunchCameraPermission : CaptureEffect
    data object OpenAppSettings : CaptureEffect
    data object LaunchPhotoPicker : CaptureEffect
    data object LaunchLocationPermission : CaptureEffect
    data class CapturePhoto(val staged: StagedMedia, val output: File) : CaptureEffect
    data class NavigateEditor(val draftId: DraftId) : CaptureEffect
    data object NavigateBack : CaptureEffect
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftStore: TransactionDraftStore,
    private val mediaStore: MediaStorePort,
    private val captureTarget: CameraCaptureTarget,
    private val photoImportSource: PhotoImportSource,
    private val deviceLocation: DeviceLocationPort,
    private val locationPreference: CaptureLocationPreference,
    private val clock: Clock,
) : ViewModel() {
    private val draftId = DraftId(checkNotNull(savedStateHandle["draftId"]))
    private val _state = MutableStateFlow(CaptureState(draftId))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val draft = draftStore.load(draftId) ?: newDraft()
            val verified = draft.images.map { image ->
                val staged = image.stagedMedia()
                if (staged != null && mediaStore.inspectDraft(staged) is Outcome.Failure) image.copy(isMissing = true) else image
            }
            val restored = draft.copy(images = verified)
            draftStore.save(restored)
            _state.value = _state.value.copy(
                images = restored.images,
                autoLocationEnabled = locationPreference.get() == CaptureLocationChoice.ENABLED,
            )
        }
    }

    fun onAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.PermissionChanged -> _state.value = _state.value.copy(
                permission = when {
                    action.granted -> CameraPermissionState.GRANTED
                    action.permanentlyDenied -> CameraPermissionState.DENIED_PERMANENTLY
                    else -> CameraPermissionState.DENIED
                },
            )
            is CaptureAction.CapabilitiesChanged -> _state.value = _state.value.copy(
                cameraAvailable = action.cameraAvailable,
                frontCameraAvailable = action.frontCameraAvailable,
                flashAvailable = action.flashAvailable,
                lens = if (action.frontCameraAvailable) _state.value.lens else CameraLens.BACK,
                flashEnabled = _state.value.flashEnabled && action.flashAvailable,
            )
            CaptureAction.RequestCameraPermission -> emit(
                if (_state.value.permission == CameraPermissionState.DENIED_PERMANENTLY) {
                    CaptureEffect.OpenAppSettings
                } else CaptureEffect.LaunchCameraPermission,
            )
            CaptureAction.PickImages -> emit(CaptureEffect.LaunchPhotoPicker)
            is CaptureAction.ImagesPicked -> importImages(action.uris)
            CaptureAction.TakePhoto -> when (locationPreference.get()) {
                CaptureLocationChoice.UNKNOWN -> _state.value = _state.value.copy(showLocationConsent = true)
                CaptureLocationChoice.ENABLED -> emit(CaptureEffect.LaunchLocationPermission)
                CaptureLocationChoice.DISABLED -> prepareCapture()
            }
            CaptureAction.EnableAutomaticLocation -> {
                locationPreference.set(CaptureLocationChoice.ENABLED)
                _state.value = _state.value.copy(showLocationConsent = false, autoLocationEnabled = true)
                emit(CaptureEffect.LaunchLocationPermission)
            }
            CaptureAction.DisableAutomaticLocation -> {
                locationPreference.set(CaptureLocationChoice.DISABLED)
                pendingLocationJob?.cancel()
                pendingLocationJob = null
                pendingLocationResult = null
                pendingLocationStartedAtEpochMillis = null
                _state.value = _state.value.copy(showLocationConsent = false, autoLocationEnabled = false)
                prepareCapture()
            }
            CaptureAction.WarmAutomaticLocation -> warmAutomaticLocation()
            is CaptureAction.LocationPermissionChanged -> {
                if (!action.granted) {
                    locationPreference.set(CaptureLocationChoice.DISABLED)
                    _state.value = _state.value.copy(autoLocationEnabled = false, errorMessage = "Không có quyền vị trí; ảnh vẫn được lưu bình thường")
                }
                prepareCapture()
            }
            is CaptureAction.PhotoCaptured -> normalizeAndAdd(action.staged, attachCurrentLocation = locationPreference.get() == CaptureLocationChoice.ENABLED)
            is CaptureAction.CaptureFailed -> viewModelScope.launch {
                pendingLocationJob?.cancel()
                pendingLocationJob = null
                pendingLocationResult = null
                pendingLocationStartedAtEpochMillis = null
                mediaStore.discardStaged(action.staged)
                _state.value = _state.value.copy(isProcessing = false, errorMessage = "Không thể chụp ảnh")
            }
            CaptureAction.ToggleLens -> if (_state.value.frontCameraAvailable) _state.value = _state.value.copy(
                lens = if (_state.value.lens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK,
            )
            CaptureAction.ToggleFlash -> if (_state.value.flashAvailable) {
                _state.value = _state.value.copy(flashEnabled = !_state.value.flashEnabled)
            }
            is CaptureAction.ZoomChanged -> _state.value = _state.value.copy(zoomLinear = action.value.coerceIn(0f, 1f))
            is CaptureAction.RemoveImage -> removeImage(action.id)
            is CaptureAction.MoveImage -> moveImage(action.from, action.to)
            CaptureAction.Continue -> continueToEditor()
            CaptureAction.Back -> {
                if (_state.value.images.isEmpty()) discardAndNavigateBack()
                else _state.value = _state.value.copy(showDiscardConfirmation = true)
            }
            CaptureAction.KeepDraft -> _state.value = _state.value.copy(showDiscardConfirmation = false)
            CaptureAction.DiscardDraft -> discardAndNavigateBack()
        }
    }

    private var pendingLocationJob: kotlinx.coroutines.Deferred<Outcome<GeoPoint>>? = null
    private var pendingLocationResult: Outcome<GeoPoint>? = null
    private var pendingLocationStartedAtEpochMillis: Long? = null

    private fun warmAutomaticLocation() {
        if (locationPreference.get() != CaptureLocationChoice.ENABLED) return
        val now = clock.nowEpochMillis()
        val existing = pendingLocationJob
        val isFresh = existing != null &&
            !existing.isCancelled &&
            (existing.isActive || pendingLocationResult is Outcome.Success) &&
            pendingLocationStartedAtEpochMillis?.let { now - it <= LOCATION_PREFETCH_MAX_AGE_MILLIS } == true
        if (isFresh) return

        existing?.cancel()
        pendingLocationResult = null
        pendingLocationStartedAtEpochMillis = now
        pendingLocationJob = viewModelScope.async {
            deviceLocation.current().also { pendingLocationResult = it }
        }
    }

    private fun prepareCapture() = viewModelScope.launch {
        _state.value = _state.value.copy(isProcessing = true, errorMessage = null)
        if (locationPreference.get() == CaptureLocationChoice.ENABLED) {
            warmAutomaticLocation()
        }
        val staged = when (val result = mediaStore.stageCapture(newOperationId(), MediaImageId(UUID.randomUUID().toString()))) {
            is Outcome.Failure -> {
                pendingLocationJob?.cancel()
                pendingLocationJob = null
                pendingLocationResult = null
                pendingLocationStartedAtEpochMillis = null
                _state.value = _state.value.copy(isProcessing = false, errorMessage = "Không thể chuẩn bị ảnh")
                return@launch
            }
            is Outcome.Success -> result.value
        }
        effectChannel.send(CaptureEffect.CapturePhoto(staged, captureTarget.outputFile(staged)))
    }

    private fun importImages(uris: List<String>) = viewModelScope.launch {
        _state.value = _state.value.copy(isProcessing = true, errorMessage = null)
        uris.take(MAX_IMAGES - _state.value.images.size).forEach { raw ->
            val staged = when (val result = photoImportSource.stage(
                raw,
                newOperationId(),
                MediaImageId(UUID.randomUUID().toString()),
            )) {
                is Outcome.Failure -> return@forEach
                is Outcome.Success -> result.value
            }
            normalizeAndAddNow(staged, attachCurrentLocation = false)
        }
        _state.value = _state.value.copy(isProcessing = false)
    }

    private fun normalizeAndAdd(staged: StagedMedia, attachCurrentLocation: Boolean) = viewModelScope.launch {
        normalizeAndAddNow(staged, attachCurrentLocation)
        _state.value = _state.value.copy(isProcessing = false)
    }

    private suspend fun normalizeAndAddNow(staged: StagedMedia, attachCurrentLocation: Boolean) {
        when (val result = mediaStore.normalize(staged)) {
            is Outcome.Failure -> {
                mediaStore.discardStaged(staged)
                _state.value = _state.value.copy(errorMessage = "Ảnh không hợp lệ hoặc quá lớn")
            }
            is Outcome.Success -> {
                val locJob = pendingLocationJob
                pendingLocationJob = null
                pendingLocationResult = null
                pendingLocationStartedAtEpochMillis = null
                val locationOutcome = if (attachCurrentLocation) {
                    // The location port may probe fused, GPS and network providers before it
                    // returns a safe fix. Keep the normalized image in the processing state
                    // until that single acquisition finishes so the editor cannot persist the
                    // draft before its location arrives.
                    withTimeoutOrNull(LOCATION_CAPTURE_TIMEOUT_MILLIS) {
                        when (val prefetched = locJob?.await()) {
                            is Outcome.Success -> prefetched
                            else -> deviceLocation.current()
                        }
                    }
                } else null

                val confirmedLocation = when (locationOutcome) {
                    is Outcome.Success -> PhotoLocation(
                        point = locationOutcome.value,
                        source = LocationSource.CURRENT,
                        confirmedAtEpochMillis = clock.nowEpochMillis(),
                    )
                    else -> null
                }
                val image = DraftImage.fromNormalized(result.value, mediaStore.previewUri(staged))
                    .copy(confirmedLocation = confirmedLocation)
                updateImages(_state.value.images + image)
                if (attachCurrentLocation && confirmedLocation == null) {
                    _state.value = _state.value.copy(
                        errorMessage = "Không lấy được vị trí chính xác; ảnh vẫn được lưu bình thường",
                    )
                }
                if (attachCurrentLocation) warmAutomaticLocation()
            }
        }
    }

    private fun removeImage(id: String) = viewModelScope.launch {
        val removed = _state.value.images.firstOrNull { it.id == id }
        removed?.stagedMedia()?.let { mediaStore.discardStaged(it) }
        updateImages(_state.value.images.filterNot { it.id == id })
    }

    private fun moveImage(from: Int, to: Int) {
        val current = _state.value.images
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        viewModelScope.launch { updateImages(mutable) }
    }

    private fun continueToEditor() = viewModelScope.launch {
        persistImages(_state.value.images)
        effectChannel.send(CaptureEffect.NavigateEditor(draftId))
    }

    private fun discardAndNavigateBack() = viewModelScope.launch {
        _state.value.images.mapNotNull(DraftImage::stagedMedia).forEach { mediaStore.discardStaged(it) }
        draftStore.delete(draftId)
        effectChannel.send(CaptureEffect.NavigateBack)
    }

    private suspend fun updateImages(images: List<DraftImage>) {
        _state.value = _state.value.copy(images = images, errorMessage = null)
        persistImages(images)
    }

    private suspend fun persistImages(images: List<DraftImage>) {
        val current = draftStore.load(draftId) ?: newDraft()
        draftStore.save(current.copy(images = images, dirty = images.isNotEmpty(), updatedAtEpochMillis = System.currentTimeMillis()))
    }

    private fun emit(effect: CaptureEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private fun newOperationId() = MediaOperationId(UUID.randomUUID().toString())

    private fun newDraft(): TransactionDraft {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        return TransactionDraft(
            id = draftId,
            occurred = com.billlocket.app.domain.model.InstantAtZone(
                now.toEpochMilli(),
                now.atZone(zone).toLocalDate().toEpochDay(),
                zone.id,
            ),
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    private companion object {
        const val MAX_IMAGES = 10
        const val LOCATION_CAPTURE_TIMEOUT_MILLIS = 8_500L
        const val LOCATION_PREFETCH_MAX_AGE_MILLIS = 30_000L
    }
}
