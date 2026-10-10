package com.billlocket.app.feature.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.billlocket.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.map.OpenPointPicker
import com.billlocket.app.feature.transaction.draft.TransactionDraftStore
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface DeviceLocationPort { suspend fun current(): Outcome<GeoPoint> }

internal const val AUTOMATIC_LOCATION_CACHE_MAX_AGE_MILLIS = 30_000L
internal const val AUTOMATIC_LOCATION_CACHE_MAX_ACCURACY_METERS = 100f
internal const val FRESH_PROVIDER_FIX_MAX_AGE_MILLIS = 30_000L
internal const val FRESH_PROVIDER_FIX_MAX_ACCURACY_METERS = 500f

internal fun isEligibleAutomaticLocationCache(
    ageMillis: Long,
    accuracyMeters: Float?,
): Boolean = ageMillis in 0..AUTOMATIC_LOCATION_CACHE_MAX_AGE_MILLIS &&
    accuracyMeters != null &&
    accuracyMeters.isFinite() &&
    accuracyMeters in 0f..AUTOMATIC_LOCATION_CACHE_MAX_ACCURACY_METERS

internal fun isEligibleFreshProviderFix(
    ageMillis: Long,
    accuracyMeters: Float?,
): Boolean = ageMillis in 0..FRESH_PROVIDER_FIX_MAX_AGE_MILLIS &&
    accuracyMeters != null &&
    accuracyMeters.isFinite() &&
    accuracyMeters in 0f..FRESH_PROVIDER_FIX_MAX_ACCURACY_METERS

@Singleton
class AndroidDeviceLocationPort @Inject constructor(@ApplicationContext private val context: Context) : DeviceLocationPort {
    companion object {
        private const val FRESH_REQUEST_TIMEOUT_PER_PROVIDER_MS = 4_000L
    }

    override suspend fun current(): Outcome<GeoPoint> {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return Outcome.Failure(DomainFailure.PermissionDenied("location", false))

        return try {
            val manager = context.getSystemService(LocationManager::class.java)
                ?: return Outcome.Failure(DomainFailure.StorageUnavailable("location_unavailable"))

            val enabledProviders = manager.getProviders(true)
            if (enabledProviders.isEmpty()) {
                return Outcome.Failure(DomainFailure.StorageUnavailable("location_disabled"))
            }

            // 1. Fast path: check for very recent, high-accuracy fix (within 30s, accuracy <= 100m)
            val fastRecent = enabledProviders
                .mapNotNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
                .filter { loc ->
                    isValidLocation(loc) &&
                    isEligibleAutomaticLocationCache(
                        ageMillis = getLocationAgeMillis(loc),
                        accuracyMeters = loc.accuracy.takeIf { loc.hasAccuracy() },
                    )
                }
                .minByOrNull { it.accuracy }

            if (fastRecent != null) {
                return Outcome.Success(GeoPoint(fastRecent.latitude, fastRecent.longitude))
            }

            // 2. Determine provider priority: FUSED (API 31+) -> GPS -> NETWORK.
            // Never prioritize NETWORK over GPS/FUSED.
            val candidateProviders = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                candidateProviders.add(LocationManager.FUSED_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                candidateProviders.add(LocationManager.GPS_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                candidateProviders.add(LocationManager.NETWORK_PROVIDER)
            }

            if (candidateProviders.isEmpty()) {
                return Outcome.Failure(DomainFailure.StorageUnavailable("location_disabled"))
            }

            // 3. Ask all available providers concurrently. Waiting for them sequentially made
            // the camera pay this timeout once per provider. We still rank the valid results
            // fused -> GPS -> network after one shared acquisition window.
            val freshLocation = coroutineScope {
                candidateProviders.map { provider ->
                    async {
                        withTimeoutOrNull(FRESH_REQUEST_TIMEOUT_PER_PROVIDER_MS) {
                            requestFreshLocationUpdate(manager, provider)
                        }
                    }
                }.awaitAll()
            }
                .filterNotNull()
                .filter { candidate ->
                    isValidLocation(candidate) &&
                        isEligibleFreshProviderFix(
                            ageMillis = getLocationAgeMillis(candidate),
                            accuracyMeters = candidate.accuracy.takeIf { candidate.hasAccuracy() },
                        )
                }
                .sortedWith(
                    compareBy<Location> { it.accuracy }
                        .thenBy { providerPriority(it.provider) }
                        .thenByDescending { it.time }
                )
                .firstOrNull()

            if (freshLocation != null) {
                return Outcome.Success(GeoPoint(freshLocation.latitude, freshLocation.longitude))
            }

            // Do not attach an older last-known fallback. A missing location is safer than
            // assigning a photo to a place the user left several minutes ago.
            Outcome.Failure(DomainFailure.StorageUnavailable("location_unavailable"))
        } catch (_: SecurityException) {
            Outcome.Failure(DomainFailure.PermissionDenied("location", false))
        }
    }

    private fun providerPriority(provider: String?): Int = when (provider) {
        "fused" -> 0
        LocationManager.GPS_PROVIDER -> 1
        LocationManager.NETWORK_PROVIDER -> 2
        else -> 3
    }

    private fun getLocationAgeMillis(location: Location): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val elapsedNanos = android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            (elapsedNanos / 1_000_000L).coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
        }
    }

    private fun isValidLocation(location: Location): Boolean {
        return isValidCoordinate(location.latitude, location.longitude)
    }

    @Suppress("DEPRECATION")
    private suspend fun requestFreshLocationUpdate(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val isFreshAndUsable = isValidLocation(location) &&
                        isEligibleFreshProviderFix(
                            ageMillis = getLocationAgeMillis(location),
                            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                        )
                    if (isFreshAndUsable && continuation.isActive) {
                        manager.removeUpdates(this)
                        continuation.resume(location)
                    }
                }
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
        }
}

data class ImageLocationState(
    val draftId: DraftId,
    val imageId: TransactionImageId,
    val point: GeoPoint? = null,
    val candidate: GeoPoint? = null,
    val source: LocationSource = LocationSource.MANUAL,
    val placeName: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
sealed interface ImageLocationAction {
    data object ConfirmCandidate : ImageLocationAction
    data class PointChanged(val point: GeoPoint, val source: LocationSource = LocationSource.MANUAL) : ImageLocationAction
    data class LatitudeChanged(val value: String) : ImageLocationAction
    data class LongitudeChanged(val value: String) : ImageLocationAction
    data class PlaceChanged(val value: String) : ImageLocationAction
    data object UseCurrent : ImageLocationAction
    data object ResolveCurrent : ImageLocationAction
    data object Save : ImageLocationAction
    data object Remove : ImageLocationAction
}
sealed interface ImageLocationEffect { data object RequestPermission : ImageLocationEffect; data object Saved : ImageLocationEffect }

@HiltViewModel
class ImageLocationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val drafts: TransactionDraftStore,
    private val deviceLocation: DeviceLocationPort,
    private val clock: Clock,
) : ViewModel() {
    private val draftId = DraftId(checkNotNull(savedStateHandle["draftId"]))
    private val imageId = TransactionImageId(checkNotNull(savedStateHandle["imageId"]))
    private val _state = MutableStateFlow(ImageLocationState(draftId, imageId))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<ImageLocationEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init { viewModelScope.launch {
        val image = drafts.load(draftId)?.images?.firstOrNull { it.id == imageId.value }
        if (image == null) _state.value = _state.value.copy(isLoading = false, errorMessage = "Không tìm thấy ảnh trong bản nháp")
        else {
            val candidate = image.locationCandidate?.let { GeoPoint(it.latitude, it.longitude) }
            val confirmed = image.confirmedLocation
            val point = confirmed?.point
            _state.value = _state.value.copy(
                point = point, candidate = candidate, source = confirmed?.source ?: LocationSource.MANUAL,
                placeName = confirmed?.placeName.orEmpty(), latitudeText = point?.latitude?.toString().orEmpty(),
                longitudeText = point?.longitude?.toString().orEmpty(), isLoading = false,
            )
        }
    } }

    fun onAction(action: ImageLocationAction) {
        when (action) {
            ImageLocationAction.ConfirmCandidate -> _state.value.candidate?.let { setPoint(it, LocationSource.EXIF) }
            is ImageLocationAction.PointChanged -> setPoint(action.point, action.source)
            is ImageLocationAction.LatitudeChanged -> { _state.value = _state.value.copy(latitudeText = action.value); parseTypedPoint() }
            is ImageLocationAction.LongitudeChanged -> { _state.value = _state.value.copy(longitudeText = action.value); parseTypedPoint() }
            is ImageLocationAction.PlaceChanged -> if (action.value.length <= 256) _state.value = _state.value.copy(placeName = action.value)
            ImageLocationAction.UseCurrent -> viewModelScope.launch { effectChannel.send(ImageLocationEffect.RequestPermission) }
            ImageLocationAction.ResolveCurrent -> viewModelScope.launch {
                when (val result = deviceLocation.current()) {
                    is Outcome.Success -> setPoint(result.value, LocationSource.CURRENT)
                    is Outcome.Failure -> _state.value = _state.value.copy(errorMessage = "Không lấy được vị trí hiện tại; bạn vẫn có thể chọn thủ công")
                }
            }
            ImageLocationAction.Save -> persist(remove = false)
            ImageLocationAction.Remove -> persist(remove = true)
        }
    }

    private fun setPoint(point: GeoPoint, source: LocationSource) {
        _state.value = _state.value.copy(point = point, source = source, latitudeText = point.latitude.toString(), longitudeText = point.longitude.toString(), errorMessage = null)
    }

    private fun parseTypedPoint() {
        val latitude = _state.value.latitudeText.toDoubleOrNull()
        val longitude = _state.value.longitudeText.toDoubleOrNull()
        if (latitude != null && longitude != null && isValidCoordinate(latitude, longitude)) {
            runCatching { GeoPoint(latitude, longitude) }.getOrNull()?.let { setPoint(it, LocationSource.MANUAL) }
        }
    }

    private fun persist(remove: Boolean) = viewModelScope.launch {
        val draft = drafts.load(draftId) ?: return@launch
        val point = _state.value.point
        if (!remove && point == null) { _state.value = _state.value.copy(errorMessage = "Hãy chọn một vị trí"); return@launch }
        val location = if (remove) null else PhotoLocation(point!!, _state.value.source, clock.nowEpochMillis(), placeName = _state.value.placeName.trim().takeIf(String::isNotEmpty))
        drafts.save(draft.copy(images = draft.images.map { if (it.id == imageId.value) it.copy(confirmedLocation = location) else it }, dirty = true, updatedAtEpochMillis = clock.nowEpochMillis()))
        effectChannel.send(ImageLocationEffect.Saved)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImageLocationScreen(onBack: () -> Unit, viewModel: ImageLocationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.onAction(ImageLocationAction.ResolveCurrent)
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ImageLocationEffect.RequestPermission -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onAction(ImageLocationAction.ResolveCurrent)
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            ImageLocationEffect.Saved -> onBack()
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.image_location_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.candidate?.let { candidate -> AssistChip(onClick = { viewModel.onAction(ImageLocationAction.ConfirmCandidate) }, label = { Text(stringResource(R.string.image_location_exif_hint, candidate.latitude, candidate.longitude)) }) }
            OpenPointPicker(state.point, { viewModel.onAction(ImageLocationAction.PointChanged(it)) }, Modifier.fillMaxWidth().weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.latitudeText, { viewModel.onAction(ImageLocationAction.LatitudeChanged(it)) }, label = { Text(stringResource(R.string.image_location_latitude)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(state.longitudeText, { viewModel.onAction(ImageLocationAction.LongitudeChanged(it)) }, label = { Text(stringResource(R.string.image_location_longitude)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(state.placeName, { viewModel.onAction(ImageLocationAction.PlaceChanged(it)) }, label = { Text(stringResource(R.string.image_location_place_name)) }, modifier = Modifier.fillMaxWidth())
            state.errorMessage?.let { msg ->
                val text = when (msg) {
                    "Không tìm thấy ảnh trong bản nháp" -> stringResource(R.string.location_error_no_image_in_draft)
                    "Không lấy được vị trí hiện tại; bạn vẫn có thể chọn thủ công",
                    "Không lấy được vị trí hiện tại; bạn vẫn có thể chọn trên bản đồ" -> stringResource(R.string.location_error_current_location_fallback)
                    "Hãy chọn một vị trí" -> stringResource(R.string.location_error_select_location)
                    else -> msg
                }
                Text(text, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.onAction(ImageLocationAction.UseCurrent) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.MyLocation, null); Text(" " + stringResource(R.string.image_location_current)) }
                OutlinedButton(onClick = { viewModel.onAction(ImageLocationAction.Remove) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.LocationOff, null); Text(" " + stringResource(R.string.image_location_remove)) }
                Button(onClick = { viewModel.onAction(ImageLocationAction.Save) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.image_location_confirm)) }
            }
        }
    }
}
