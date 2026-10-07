package com.billlocket.app.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.location.DeviceLocationPort
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.navigation.RoutePattern
import com.billlocket.app.feature.location.AddressResolver
import com.billlocket.app.feature.location.ResolvedAddress

const val BILLLOCKET_DARK_MAP_STYLE_JSON = """
[
  {
    "elementType": "geometry",
    "stylers": [{"color": "#171f28"}]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#a8bccd"}]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#121820"}, {"weight": 3}]
  },
  {
    "featureType": "administrative",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#2c3b4d"}]
  },
  {
    "featureType": "administrative.locality",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#c5d7e8"}]
  },
  {
    "featureType": "administrative.neighborhood",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#9cb2c5"}]
  },
  {
    "featureType": "landscape.man_made",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#1b2531"}]
  },
  {
    "featureType": "landscape.natural",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#16202a"}]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{"color": "#1e2936"}]
  },
  {
    "featureType": "poi.business",
    "stylers": [{"visibility": "off"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#162b27"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#528c7f"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#233344"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#192430"}]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#8fa8be"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#2d445c"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#1f3041"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#a1bbd2"}]
  },
  {
    "featureType": "transit",
    "elementType": "geometry",
    "stylers": [{"color": "#1e2b38"}]
  },
  {
    "featureType": "water",
    "elementType": "geometry.fill",
    "stylers": [{"color": "#1c3c58"}]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#5b84a8"}]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#102334"}]
  }
]
"""

enum class MapProviderState { LOADING, READY, UNAVAILABLE }
enum class MapSheetState { COLLAPSED, HALF_EXPANDED, EXPANDED }
enum class MapThemeMode(val title: String) {
    DARK("Tối"),
    LIGHT("Sáng"),
    SYSTEM("Theo hệ thống"),
}

data class MapState(
    val photos: List<MapPhoto> = emptyList(),
    val selected: MapPhoto? = null,
    val addresses: Map<TransactionImageId, ResolvedAddress> = emptyMap(),
    val provider: MapProviderState = MapProviderState.LOADING,
    val themeMode: MapThemeMode = MapThemeMode.DARK,
    val month: YearMonth = YearMonth.now(),
    val type: TransactionType? = null,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val amountVisibility: AmountVisibility = AmountVisibility.SHOWN,
    val errorMessage: String? = null,
    val currentFocus: GeoPoint? = null,
)

sealed interface MapAction {
    data class ProviderChanged(val available: Boolean) : MapAction
    data class SelectPhoto(val value: MapPhoto?) : MapAction
    data class SetThemeMode(val value: MapThemeMode) : MapAction
    data class TypeChanged(val value: TransactionType?) : MapAction
    data class AccountChanged(val value: AccountId?) : MapAction
    data class CategoryChanged(val value: CategoryId?) : MapAction
    data object PreviousMonth : MapAction
    data object NextMonth : MapAction
    data object UseCurrentLocation : MapAction
    data object ResolveCurrentLocation : MapAction
}

sealed interface MapEffect {
    data object RequestLocationPermission : MapEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val deviceLocation: DeviceLocationPort,
    private val addressResolver: AddressResolver,
) : androidx.lifecycle.ViewModel() {
    private val initialTxId = savedStateHandle.get<String>(RoutePattern.ARG_TRANSACTION_ID)?.let(::TransactionId)
    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow<TransactionType?>(null)
    private val account = MutableStateFlow<AccountId?>(null)
    private val category = MutableStateFlow<CategoryId?>(null)
    private val _state = MutableStateFlow(MapState())
    val state = _state.asStateFlow()
    private val effectChannel = kotlinx.coroutines.channels.Channel<MapEffect>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (initialTxId != null) {
            viewModelScope.launch {
                transactions.observe(initialTxId).firstOrNull()?.let { tx ->
                    val txMonth = YearMonth.from(
                        Instant.ofEpochMilli(tx.occurred.epochMillis)
                            .atZone(ZoneId.of(tx.occurred.zoneId))
                    )
                    month.value = txMonth
                    val img = tx.images.firstOrNull { it.location != null }
                    if (img?.location != null) {
                        _state.update { it.copy(currentFocus = img.location.point) }
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(accounts.observeAll(), categories.observe(), settings.settings) { accountList, categoryList, config -> Triple(accountList, categoryList, config) }
                .collect { (accountList, categoryList, config) -> _state.update { it.copy(accounts = accountList, categories = categoryList, amountVisibility = config.amountVisibility) } }
        }
        viewModelScope.launch {
            combine(month, type, account, category) { m, t, a, c -> arrayOf(m, t, a, c) }
                .flatMapLatest { values ->
                    val selectedMonth = values[0] as YearMonth
                    val selectedType = values[1] as TransactionType?
                    val selectedAccount = values[2] as AccountId?
                    val selectedCategory = values[3] as CategoryId?
                    _state.update { it.copy(month = selectedMonth, type = selectedType, accountId = selectedAccount, categoryId = selectedCategory) }
                    transactions.observeMapPhotos(TransactionFilter(DateRange(selectedMonth.atDay(1).toEpochDay(), selectedMonth.atEndOfMonth().toEpochDay()), selectedType, selectedAccount, selectedCategory))
                }
                .catch { _state.update { it.copy(errorMessage = "Không thể tải ảnh có vị trí") } }
                .collect { photos ->
                    _state.update { current ->
                        val targetFromArg = initialTxId?.let { txId -> photos.firstOrNull { it.transactionId == txId } }
                        val preservedSelection = targetFromArg
                            ?: current.selected?.takeIf { selected -> photos.any { it.imageId == selected.imageId } }
                        current.copy(
                            photos = photos,
                            selected = preservedSelection ?: photos.firstOrNull(),
                            currentFocus = targetFromArg?.location?.point ?: current.currentFocus,
                        )
                    }
                    resolveAddresses(photos)
                }
        }
    }

    private fun resolveAddresses(photos: List<MapPhoto>) {
        viewModelScope.launch {
            photos.forEach { photo ->
                if (!_state.value.addresses.containsKey(photo.imageId)) {
                    val resolved = addressResolver.resolve(photo.location.point, photo.location.placeName)
                    _state.update { current ->
                        current.copy(addresses = current.addresses + (photo.imageId to resolved))
                    }
                }
            }
        }
    }

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.ProviderChanged -> _state.update { it.copy(provider = if (action.available) MapProviderState.READY else MapProviderState.UNAVAILABLE) }
            is MapAction.SelectPhoto -> _state.update { it.copy(selected = action.value) }
            is MapAction.SetThemeMode -> _state.update { it.copy(themeMode = action.value) }
            is MapAction.TypeChanged -> type.value = action.value
            is MapAction.AccountChanged -> account.value = action.value
            is MapAction.CategoryChanged -> category.value = action.value
            MapAction.PreviousMonth -> month.value = month.value.minusMonths(1)
            MapAction.NextMonth -> month.value = month.value.plusMonths(1)
            MapAction.UseCurrentLocation -> viewModelScope.launch { effectChannel.send(MapEffect.RequestLocationPermission) }
            MapAction.ResolveCurrentLocation -> viewModelScope.launch {
                when (val result = deviceLocation.current()) {
                    is Outcome.Success -> _state.update { it.copy(currentFocus = result.value, errorMessage = null) }
                    is Outcome.Failure -> _state.update { it.copy(errorMessage = "Không lấy được vị trí hiện tại") }
                }
            }
        }
    }
}

data class MapCluster(val id: String, val center: GeoPoint, val photos: List<MapPhoto>)

fun clusterMapPhotos(photos: List<MapPhoto>, cellDegrees: Double = 0.01): List<MapCluster> =
    photos.groupBy { photo ->
        (photo.location.point.latitude / cellDegrees).roundToInt() to (photo.location.point.longitude / cellDegrees).roundToInt()
    }.map { (_, group) ->
        val sorted = group.sortedBy { it.imageId.value }
        MapCluster(
            id = sorted.joinToString("|") { it.imageId.value },
            center = GeoPoint(sorted.map { it.location.point.latitude }.average(), sorted.map { it.location.point.longitude }.average()),
            photos = sorted,
        )
    }

@Composable
fun OpenPhotoMap(
    photos: List<MapPhoto>,
    selectedPhoto: MapPhoto?,
    focus: GeoPoint?,
    themeMode: MapThemeMode = MapThemeMode.DARK,
    onPhotoSelected: (MapPhoto) -> Unit,
    onSetThemeMode: (MapThemeMode) -> Unit = {},
    onProviderChanged: (Boolean) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clusters = remember(photos) { clusterMapPhotos(photos) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val initialTarget = remember { focus ?: selectedPhoto?.location?.point ?: clusters.firstOrNull()?.center ?: GeoPoint(10.8231, 106.6297) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(initialTarget.latitude, initialTarget.longitude),
            if (clusters.isEmpty() && focus == null) 11f else 13.5f,
        )
    }

    val zoomTier = mapMarkerZoomTier(cameraPositionState.position.zoom)

    val fallbackMarkers = remember(context, clusters, selectedPhoto?.imageId, zoomTier) {
        clusters.associate { cluster ->
            val isSelected = selectedPhoto != null && cluster.photos.any { it.imageId == selectedPhoto.imageId }
            cluster.id to PhotoMarkerBitmapFactory.fallback(context, cluster, isSelected, zoomTier)
        }
    }

    val markerBitmaps by produceState(fallbackMarkers, context, clusters, selectedPhoto?.imageId, zoomTier) {
        value = fallbackMarkers
        value = withContext(Dispatchers.IO) {
            clusters.associate { cluster ->
                val isSelected = selectedPhoto != null && cluster.photos.any { it.imageId == selectedPhoto.imageId }
                cluster.id to PhotoMarkerBitmapFactory.representative(context, cluster, isSelected, zoomTier)
            }
        }
    }

    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val isDarkTheme = isSystemInDarkTheme()
    val resolvedMapStyle = remember(themeMode, isDarkTheme) {
        when (themeMode) {
            MapThemeMode.DARK -> MapStyleOptions(BILLLOCKET_DARK_MAP_STYLE_JSON)
            MapThemeMode.LIGHT -> null
            MapThemeMode.SYSTEM -> if (isDarkTheme) MapStyleOptions(BILLLOCKET_DARK_MAP_STYLE_JSON) else null
        }
    }

    val properties = remember(hasLocationPermission, resolvedMapStyle) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = resolvedMapStyle,
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        )
    }

    var lastClusterSignature by remember { mutableStateOf<String?>(null) }
    val signature = remember(clusters) { clusters.joinToString(";") { it.id } }
    LaunchedEffect(signature) {
        if (signature != lastClusterSignature) {
            if (clusters.isNotEmpty() && selectedPhoto == null) {
                val initial = clusters.first().center
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(initial.latitude, initial.longitude),
                        13f,
                    ),
                )
            }
            lastClusterSignature = signature
        }
    }

    LaunchedEffect(focus) {
        focus?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f),
            )
        }
    }

    // Subtly animate toward selected photo when selection changes from carousel
    var lastSelectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedPhoto?.imageId?.value) {
        val selId = selectedPhoto?.imageId?.value
        if (selId != null && selId != lastSelectedId) {
            lastSelectedId = selId
            val pt = selectedPhoto.location.point
            val currentTarget = cameraPositionState.position.target
            val latDiff = Math.abs(currentTarget.latitude - pt.latitude)
            val lngDiff = Math.abs(currentTarget.longitude - pt.longitude)
            // Only move if marker is meaningfully away from current viewport
            if (latDiff > 0.005 || lngDiff > 0.005) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLng(LatLng(pt.latitude, pt.longitude)),
                )
            }
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = properties,
            uiSettings = uiSettings,
            onMapLoaded = { onProviderChanged(true) },
        ) {
            clusters.forEach { cluster ->
                val isSelected = selectedPhoto != null && cluster.photos.any { it.imageId == selectedPhoto.imageId }
                val bitmap = markerBitmaps[cluster.id] ?: fallbackMarkers.getValue(cluster.id)
                val descriptor = remember(bitmap) {
                    try {
                        BitmapDescriptorFactory.fromBitmap(bitmap)
                    } catch (_: Exception) {
                        null
                    }
                }
                val markerState = rememberMarkerState(
                    key = cluster.id,
                    position = LatLng(cluster.center.latitude, cluster.center.longitude),
                )
                LaunchedEffect(cluster.center) {
                    markerState.position = LatLng(cluster.center.latitude, cluster.center.longitude)
                }

                Marker(
                    state = markerState,
                    icon = descriptor,
                    zIndex = if (isSelected) 2f else 1f,
                    anchor = Offset(0.5f, 1.0f),
                    title = if (cluster.photos.size == 1) cluster.photos.first().categoryName else "${cluster.photos.size} ảnh giao dịch",
                    snippet = cluster.photos.firstOrNull()?.location?.placeName,
                    onClick = {
                        onPhotoSelected(cluster.photos.first())
                        if (cluster.photos.size > 1) {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(cluster.center.latitude, cluster.center.longitude),
                                        cameraPositionState.position.zoom + 1.5f,
                                    ),
                                )
                            }
                        }
                        true
                    },
                )
            }
        }

        // Floating pill on map (top-left) matching Image C
        Surface(
            onClick = onUseCurrentLocation,
            shape = RoundedCornerShape(26.dp),
            color = Color(0xEB1A1916),
            border = BorderStroke(1.dp, Color(0x3DFFB800)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB800)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF141311),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(-45f),
                    )
                }
                Column {
                    Text(
                        "Vị trí hiện tại",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        if (focus != null) "Đang hiển thị quanh đây" else "Nhấn để định vị",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF9E9D98),
                    )
                }
            }
        }

        // Floating controls on map (top-right): Recenter and Map Style
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Recenter button
            Surface(
                onClick = onUseCurrentLocation,
                shape = CircleShape,
                color = Color(0xEB1A1916),
                border = BorderStroke(1.dp, Color(0x3DFFB800)),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "Định vị hiện tại",
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Map Style selector button
            Box {
                var showStyleMenu by remember { mutableStateOf(false) }

                Surface(
                    onClick = { showStyleMenu = true },
                    shape = CircleShape,
                    color = Color(0xEB1A1916),
                    border = BorderStroke(1.dp, Color(0x3DFFB800)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = "Kiểu bản đồ",
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = showStyleMenu,
                    onDismissRequest = { showStyleMenu = false },
                    modifier = Modifier.background(Color(0xFF1E1D1A)),
                ) {
                    MapThemeMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.title,
                                    color = if (themeMode == mode) Color(0xFFFFB800) else Color.White,
                                    fontWeight = if (themeMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        MapThemeMode.DARK -> Icons.Filled.DarkMode
                                        MapThemeMode.LIGHT -> Icons.Filled.LightMode
                                        MapThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                    },
                                    contentDescription = null,
                                    tint = if (themeMode == mode) Color(0xFFFFB800) else Color(0xFF9E9D98),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                onSetThemeMode(mode)
                                showStyleMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OpenPointPicker(
    point: GeoPoint?,
    onPointChanged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = point ?: GeoPoint(10.8231, 106.6297)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(initial.latitude, initial.longitude),
            if (point != null) 15f else 12f,
        )
    }
    val context = LocalContext.current
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val properties = remember(hasLocationPermission) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = MapStyleOptions(BILLLOCKET_DARK_MAP_STYLE_JSON),
        )
    }
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = true,
            mapToolbarEnabled = false,
        )
    }

    var lastAnimatedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(point) {
        if (point != null && point != lastAnimatedPoint) {
            val currentTarget = cameraPositionState.position.target
            val latDiff = Math.abs(currentTarget.latitude - point.latitude)
            val lngDiff = Math.abs(currentTarget.longitude - point.longitude)
            if (latDiff > 0.0001 || lngDiff > 0.0001) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude),
                        cameraPositionState.position.zoom.coerceAtLeast(14f),
                    ),
                )
            }
            lastAnimatedPoint = point
        }
    }

    val markerState = rememberMarkerState(key = "selected_point")
    LaunchedEffect(point) {
        point?.let {
            markerState.position = LatLng(it.latitude, it.longitude)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { latLng ->
            onPointChanged(GeoPoint(latLng.latitude, latLng.longitude))
        },
        onMapLongClick = { latLng ->
            onPointChanged(GeoPoint(latLng.latitude, latLng.longitude))
        },
    ) {
        point?.let {
            Marker(
                state = markerState,
                title = "Vị trí đã chọn",
            )
        }
    }
}

@Composable
fun MapScreen(
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) viewModel.onAction(MapAction.ResolveCurrentLocation)
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            MapEffect.RequestLocationPermission -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onAction(MapAction.ResolveCurrentLocation)
                else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E0C)),
    ) {
        // Top section with Month switcher & compact filter rows
        MapHeaderAndFilters(
            state = state,
            onAction = viewModel::onAction,
        )

        // Main Map + Draggable Bottom Sheet
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val collapsedHeightPx = with(LocalDensity.current) { 90.dp.toPx() }
            val halfExpandedHeightPx = (totalHeightPx * 0.28f).coerceIn(
                with(LocalDensity.current) { 195.dp.toPx() },
                with(LocalDensity.current) { 230.dp.toPx() },
            )
            val expandedHeightPx = (totalHeightPx * 0.68f).coerceIn(
                with(LocalDensity.current) { 420.dp.toPx() },
                with(LocalDensity.current) { 560.dp.toPx() },
            )

            val sheetHeightAnim = remember { Animatable(halfExpandedHeightPx) }

            // Ensure anim target stays bounded if screen resizes
            LaunchedEffect(halfExpandedHeightPx) {
                if (sheetHeightAnim.value == 0f) {
                    sheetHeightAnim.snapTo(halfExpandedHeightPx)
                }
            }

            // Map canvas occupies the area
            when (state.provider) {
                MapProviderState.LOADING, MapProviderState.READY -> OpenPhotoMap(
                    photos = state.photos,
                    selectedPhoto = state.selected,
                    focus = state.currentFocus,
                    themeMode = state.themeMode,
                    onPhotoSelected = { photo ->
                        viewModel.onAction(MapAction.SelectPhoto(photo))
                    },
                    onSetThemeMode = { mode ->
                        viewModel.onAction(MapAction.SetThemeMode(mode))
                    },
                    onProviderChanged = { viewModel.onAction(MapAction.ProviderChanged(it)) },
                    onUseCurrentLocation = { viewModel.onAction(MapAction.UseCurrentLocation) },
                    modifier = Modifier.fillMaxSize(),
                )
                MapProviderState.UNAVAILABLE -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Map, null, tint = Color(0xFFFFB800))
                        Spacer(Modifier.height(8.dp))
                        Text("Không tải được bản đồ", color = Color.White)
                        TextButton(onClick = { viewModel.onAction(MapAction.ProviderChanged(true)) }) {
                            Text("Thử lại", color = Color(0xFFFFB800))
                        }
                    }
                }
            }

            if (state.photos.isEmpty() && state.provider != MapProviderState.LOADING) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                ) {
                    BillLocketEmptyState(
                        icon = Icons.Filled.LocationOff,
                        title = "Chưa có ảnh trên bản đồ",
                        message = "Các ảnh giao dịch có vị trí đã xác nhận sẽ xuất hiện tại đây.",
                    )
                }
            }

            // Draggable Bottom Panel matching Image C
            if (state.photos.isNotEmpty()) {
                val currentSheetHeightDp = with(LocalDensity.current) { sheetHeightAnim.value.toDp() }
                val isExpanded = sheetHeightAnim.value > (halfExpandedHeightPx + expandedHeightPx) / 2f
                val isCollapsed = sheetHeightAnim.value < (collapsedHeightPx + halfExpandedHeightPx) / 2f

                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFF141311),
                    border = BorderStroke(1.dp, Color(0xFF262520)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(currentSheetHeightDp),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Drag handle area
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            val current = sheetHeightAnim.value
                                            val target = when {
                                                current < (collapsedHeightPx + halfExpandedHeightPx) / 2f -> collapsedHeightPx
                                                current < (halfExpandedHeightPx + expandedHeightPx) / 2f -> halfExpandedHeightPx
                                                else -> expandedHeightPx
                                            }
                                            coroutineScope.launch {
                                                sheetHeightAnim.animateTo(
                                                    target,
                                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                                                )
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            coroutineScope.launch {
                                                val next = (sheetHeightAnim.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                                                sheetHeightAnim.snapTo(next)
                                            }
                                        },
                                    )
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .width(38.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF45433E)),
                            )
                        }

                        // Sheet content based on current height
                        if (isCollapsed) {
                            // Collapsed preview: compact row
                            state.selected?.let { photo ->
                                CollapsedTransactionRow(
                                    photo = photo,
                                    resolvedAddress = state.addresses[photo.imageId],
                                    currentFocus = state.currentFocus,
                                    amountVisibility = state.amountVisibility,
                                    onOpen = { onOpenTransaction(photo.transactionId) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            // Carousel of cards matching Image 1
                            val selectedIndex = state.photos.indexOfFirst { it.imageId == state.selected?.imageId }.coerceAtLeast(0)
                            val pagerState = rememberPagerState(
                                initialPage = selectedIndex,
                            ) { state.photos.size }

                            // Synchronize map selection to carousel scroll
                            LaunchedEffect(state.selected?.imageId) {
                                val idx = state.photos.indexOfFirst { it.imageId == state.selected?.imageId }
                                if (idx >= 0 && idx != pagerState.currentPage) {
                                    pagerState.animateScrollToPage(idx)
                                }
                            }

                            // Synchronize carousel swipe to map selection
                            LaunchedEffect(pagerState.settledPage) {
                                if (pagerState.settledPage in state.photos.indices) {
                                    val photo = state.photos[pagerState.settledPage]
                                    if (photo.imageId != state.selected?.imageId) {
                                        viewModel.onAction(MapAction.SelectPhoto(photo))
                                    }
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(136.dp),
                            ) {
                                val cardWidth = maxWidth * 0.84f
                                HorizontalPager(
                                    state = pagerState,
                                    pageSize = PageSize.Fixed(cardWidth),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    pageSpacing = 10.dp,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    val photo = state.photos[page]
                                    val isCardSelected = photo.imageId == state.selected?.imageId
                                    CarouselTransactionCard(
                                        photo = photo,
                                        isSelected = isCardSelected,
                                        resolvedAddress = state.addresses[photo.imageId],
                                        amountVisibility = state.amountVisibility,
                                        onClick = {
                                            if (isCardSelected) {
                                                onOpenTransaction(photo.transactionId)
                                            } else {
                                                viewModel.onAction(MapAction.SelectPhoto(photo))
                                            }
                                        },
                                        onMoreClick = { onOpenTransaction(photo.transactionId) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            // If Expanded: reveal vertical list below the carousel
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFF262520), thickness = 1.dp)
                                LazyColumn(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(state.photos, key = { it.imageId.value }) { photo ->
                                        val isRowSelected = photo.imageId == state.selected?.imageId
                                        ExpandedTransactionRow(
                                            photo = photo,
                                            isSelected = isRowSelected,
                                            resolvedAddress = state.addresses[photo.imageId],
                                            currentFocus = state.currentFocus,
                                            amountVisibility = state.amountVisibility,
                                            onClick = {
                                                viewModel.onAction(MapAction.SelectPhoto(photo))
                                            },
                                            onOpen = { onOpenTransaction(photo.transactionId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapHeaderAndFilters(
    state: MapState,
    onAction: (MapAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Month switcher row: < Tháng M/yyyy ˇ > [Calendar]
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onAction(MapAction.PreviousMonth) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "Tháng trước",
                    tint = Color(0xFFFFB800),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Tháng ${state.month.monthValue}/${state.month.year}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { onAction(MapAction.NextMonth) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Tháng sau",
                        tint = Color(0xFFFFB800),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33FFB800),
                    border = BorderStroke(1.dp, Color(0x66FFB800)),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = "Lịch",
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Filter Row 1: All / Expense / Income / Accounts
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            item {
                CustomFilterChip(
                    label = "Tất cả",
                    selected = state.type == null,
                    onClick = { onAction(MapAction.TypeChanged(null)) },
                )
            }
            item {
                CustomFilterChip(
                    label = "Chi",
                    selected = state.type == TransactionType.EXPENSE,
                    onClick = { onAction(MapAction.TypeChanged(TransactionType.EXPENSE)) },
                )
            }
            item {
                CustomFilterChip(
                    label = "Thu",
                    selected = state.type == TransactionType.INCOME,
                    onClick = { onAction(MapAction.TypeChanged(TransactionType.INCOME)) },
                )
            }
            items(state.accounts, key = { it.id.value }) { account ->
                val isSelected = state.accountId == account.id
                CustomFilterChip(
                    label = account.name,
                    selected = isSelected,
                    onClick = {
                        onAction(MapAction.AccountChanged(account.id.takeUnless { it == state.accountId }))
                    },
                )
            }
        }

        // Filter Row 2: All categories / specific category
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            item {
                CustomFilterChip(
                    label = "Mọi danh mục",
                    selected = state.categoryId == null,
                    leadingIcon = Icons.Filled.GridView,
                    onClick = { onAction(MapAction.CategoryChanged(null)) },
                )
            }
            items(state.categories.filter { !it.isArchived }, key = { it.id.value }) { category ->
                val isSelected = state.categoryId == category.id
                CustomFilterChip(
                    label = category.name,
                    selected = isSelected,
                    onClick = {
                        onAction(MapAction.CategoryChanged(category.id.takeUnless { it == state.categoryId }))
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFFFB800) else Color(0xFF1E1D1A),
        contentColor = if (selected) Color(0xFF121212) else Color(0xFFC8C6C0),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFF2E2C28)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) Color(0xFF121212) else Color(0xFFFFB800),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            )
        }
    }
}

@Composable
private fun CarouselTransactionCard(
    photo: MapPhoto,
    isSelected: Boolean,
    resolvedAddress: ResolvedAddress?,
    amountVisibility: AmountVisibility,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF141311),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFFFFB800) else Color(0xFF262520),
        ),
        modifier = modifier.fillMaxHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Photo thumbnail matching Image 1
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF22211D)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = photo.mediaUri,
                    contentDescription = photo.categoryName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Info column matching Image 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top row: Category name and '•••' more button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = photo.categoryName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "Chi tiết",
                            tint = Color(0xFFB5B3AC),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Amount
                Text(
                    text = if (amountVisibility == AmountVisibility.SHOWN) {
                        NumberFormat.getNumberInstance(Locale("vi", "VN")).format(photo.amount.minorUnits) + " đ"
                    } else "••••",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    color = Color(0xFFFFB800),
                    maxLines = 1,
                )

                // Date & Time row: 🕒 05/10 08:18
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF9E9D98),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = formatDateShort(photo.occurred),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color(0xFF9E9D98),
                    )
                }

                // Address row: 📍 Nguyễn Hoàng Tôn, Xuân Đỉnh,\nBắc Từ Liêm, Hà Nội
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = Color(0xFF9E9D98),
                        modifier = Modifier
                            .size(13.dp)
                            .padding(top = 2.dp),
                    )
                    val addressText = when {
                        resolvedAddress != null -> resolvedAddress.shortAddress
                        photo.location.placeName != null && photo.location.placeName.isNotBlank() && !isCoordinateString(photo.location.placeName) -> photo.location.placeName
                        else -> "Đang xác định địa chỉ…"
                    }
                    Text(
                        text = addressText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                        ),
                        color = Color(0xFFC0BEB8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedTransactionRow(
    photo: MapPhoto,
    resolvedAddress: ResolvedAddress?,
    currentFocus: GeoPoint?,
    amountVisibility: AmountVisibility,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141311),
        border = BorderStroke(1.5.dp, Color(0xFFFFB800)),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = photo.mediaUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    photo.categoryName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    if (amountVisibility == AmountVisibility.SHOWN) {
                        NumberFormat.getNumberInstance(Locale("vi", "VN")).format(photo.amount.minorUnits) + " ₫"
                    } else "••••",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFFB800),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDateShort(photo.occurred),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9E9D98),
                )
                val locText = resolvedAddress?.shortAddress
                    ?: photo.location.placeName?.takeIf { !isCoordinateString(it) }
                    ?: if (currentFocus != null) formatDistance(calculateDistanceMeters(currentFocus, photo.location.point)) else "${photo.location.point.latitude.formatCoordinate()}"
                Text(
                    locText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9E9D98),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExpandedTransactionRow(
    photo: MapPhoto,
    isSelected: Boolean,
    resolvedAddress: ResolvedAddress?,
    currentFocus: GeoPoint?,
    amountVisibility: AmountVisibility,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0x33FFB800) else Color(0xFF1A1916),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFFFFB800) else Color(0xFF282723),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = photo.mediaUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    photo.categoryName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                val displayAddr = resolvedAddress?.shortAddress
                    ?: photo.location.placeName?.takeIf { !isCoordinateString(it) }
                    ?: "${photo.location.point.latitude.formatCoordinate()}, ${photo.location.point.longitude.formatCoordinate()}"
                Text(
                    formatDateShort(photo.occurred) + " • " + displayAddr,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9E9D98),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (amountVisibility == AmountVisibility.SHOWN) {
                        NumberFormat.getNumberInstance(Locale("vi", "VN")).format(photo.amount.minorUnits) + " ₫"
                    } else "••••",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color(0xFFFFB800) else Color.White,
                )
                if (currentFocus != null) {
                    Text(
                        formatDistance(calculateDistanceMeters(currentFocus, photo.location.point)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF9E9D98),
                    )
                }
            }
            IconButton(
                onClick = onOpen,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.ArrowOutward,
                    contentDescription = "Mở giao dịch",
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun isCoordinateString(text: String): Boolean =
    text.matches(Regex("^[\\d\\.\\,\\-\\s]+$"))

private fun formatDateShort(value: InstantAtZone): String =
    Instant.ofEpochMilli(value.epochMillis).atZone(ZoneId.of(value.zoneId)).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))

private fun Double.formatCoordinate() = String.format(Locale.US, "%.4f", this)

private fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
    val r = 6371000.0
    val lat1 = Math.toRadians(p1.latitude)
    val lat2 = Math.toRadians(p2.latitude)
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLng = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) *
        sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "${meters.roundToInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000)
    }
}
