package com.billlocket.app.feature.transaction.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.R
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.feature.finance.categoryIcon
import com.billlocket.app.feature.map.BILLLOCKET_DARK_MAP_STYLE_JSON
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.activeAppLocale
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.BillLocketErrorBanner
import com.billlocket.app.ui.common.BillLocketLoadingState
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionDetailScreen(
    onNavigateEdit: (TransactionId, DraftId) -> Unit,
    onNavigateMap: (TransactionId, TransactionImageId?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val snackbar = remember { SnackbarHostState() }

    val context = LocalContext.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is TransactionDetailEffect.NavigateEdit -> onNavigateEdit(effect.transactionId, effect.draftId)
            is TransactionDetailEffect.ShowUndo -> {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.transaction_deleted),
                    actionLabel = context.getString(R.string.action_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onAction(TransactionDetailAction.UndoDelete(effect.token))
                } else viewModel.onAction(TransactionDetailAction.FinishDelete(effect.token))
            }
            TransactionDetailEffect.NavigateBack -> onNavigateBack()
        }
    }
    BackHandler { viewModel.onAction(TransactionDetailAction.Back) }

    Scaffold(
        containerColor = Color(0xFF0F0E0C),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(TransactionDetailAction.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color(0xFFFFB800),
                        )
                    }
                },
                actions = {
                    if (state.transaction != null && !state.isDeleting) {
                        IconButton(onClick = { viewModel.onAction(TransactionDetailAction.Edit) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                                tint = Color(0xFFFFB800),
                            )
                        }
                        IconButton(onClick = { viewModel.onAction(TransactionDetailAction.RequestDelete) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.detail_delete_title),
                                tint = Color(0xFFFFB800),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0E0C),
                ),
            )
        },
    ) { padding ->
        val locale = activeAppLocale()
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { BillLocketLoadingState("BillLocket") }
            state.notFound -> Box(Modifier.fillMaxSize().padding(padding).padding(18.dp), contentAlignment = Alignment.Center) { BillLocketEmptyState(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.destination_not_found), stringResource(R.string.destination_not_found_message)) }
            else -> state.transaction?.let { transaction ->
                val primaryLocation = state.selectedLocation

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val amountFormatted = BillLocketFormatters.formatMoney(transaction.amount.minorUnits, locale)
                    val amountText = (if (transaction.type == TransactionType.EXPENSE) "-$amountFormatted" else "+$amountFormatted")

                    // Hero Photo & Amount section matching Image 2
                    if (state.images.isEmpty()) {
                        BillLocketEmptyState(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.home_no_photos_attached), "")
                        Text(
                            amountText,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (transaction.type == TransactionType.EXPENSE) Color(0xFFFF6B6B) else Color(0xFF66D19E),
                        )
                    } else if (state.images.size == 1) {
                        HeroImageCard(
                            item = state.images.first(),
                            amountText = amountText,
                            transactionType = transaction.type,
                            categoryName = state.categoryName,
                            categoryIconKey = state.categoryIconKey,
                            accountName = state.accountName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                        )
                    } else {
                        val cardWidth = (LocalConfiguration.current.screenWidthDp - 32).dp
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(state.images, key = { _, item -> item.image.id.value }) { _, item ->
                                val isSelected = item.image.id == state.selectedImageId
                                Box(
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .height(380.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .then(
                                            if (isSelected) Modifier.border(2.dp, Color(0xFFFFB800), RoundedCornerShape(24.dp))
                                            else Modifier
                                        )
                                        .clickable { viewModel.onAction(TransactionDetailAction.SelectImage(item.image.id)) },
                                ) {
                                    HeroImageCard(
                                        item = item,
                                        amountText = amountText,
                                        transactionType = transaction.type,
                                        categoryName = state.categoryName,
                                        categoryIconKey = state.categoryIconKey,
                                        accountName = state.accountName,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    // "Thông tin" Section card matching Image 2
                    val time = Instant.ofEpochMilli(transaction.occurred.epochMillis)
                        .atZone(ZoneId.of(transaction.occurred.zoneId))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF141311),
                        border = BorderStroke(1.dp, Color(0xFF24231E)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.detail_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )

                            // Row 1: Thời gian
                            InfoItemRow(
                                icon = Icons.Filled.Schedule,
                                label = stringResource(R.string.detail_field_time),
                                value = BillLocketFormatters.formatDateTime(transaction.occurred.epochMillis, ZoneId.of(transaction.occurred.zoneId), locale),
                            )

                            // Row 2: Địa chỉ
                            val addressValue = when {
                                state.isResolvingAddress -> stringResource(R.string.map_resolving_address)
                                state.address != null -> state.address?.fullAddress.orEmpty()
                                primaryLocation != null -> {
                                    primaryLocation.placeName?.takeIf { !it.matches(Regex("^[\\d\\.\\,\\-\\s]+$")) }
                                        ?: String.format(Locale.US, "%.4f, %.4f", primaryLocation.point.latitude, primaryLocation.point.longitude)
                                }
                                else -> stringResource(R.string.map_no_location)
                            }

                            InfoItemRow(
                                icon = Icons.Filled.Place,
                                label = stringResource(R.string.detail_field_location),
                                value = addressValue,
                                hasTrailingChevron = primaryLocation != null,
                                onClick = if (primaryLocation != null) { { onNavigateMap(transaction.id, state.selectedImageId) } } else null,
                            )

                            // Row 3: Vị trí chi tiết (optional, only if resolved)
                            state.address?.detailLocation?.let { detail ->
                                InfoItemRow(
                                    icon = Icons.Filled.Map,
                                    label = stringResource(R.string.destination_image_location),
                                    value = detail,
                                )
                            }

                            // Row 4: Ghi chú (if present)
                            transaction.note?.takeIf { it.isNotBlank() }?.let { note ->
                                InfoItemRow(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    label = stringResource(R.string.detail_field_note),
                                    value = note,
                                )
                            }
                        }
                    }

                    // "Vị trí trên bản đồ" Section card matching Image 2
                    if (primaryLocation != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF141311),
                            border = BorderStroke(1.dp, Color(0xFF24231E)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Map,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB800),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.map_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Mini Map Preview (non-interactive, tap opens Map screen)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(86.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onNavigateMap(transaction.id, state.selectedImageId) },
                                    ) {
                                        val cameraPositionState = rememberCameraPositionState {
                                            position = CameraPosition.fromLatLngZoom(
                                                LatLng(primaryLocation.point.latitude, primaryLocation.point.longitude),
                                                14.5f,
                                            )
                                        }
                                        val markerState = rememberMarkerState(
                                            position = LatLng(primaryLocation.point.latitude, primaryLocation.point.longitude),
                                        )
                                        val properties = remember {
                                            MapProperties(
                                                mapStyleOptions = MapStyleOptions(BILLLOCKET_DARK_MAP_STYLE_JSON),
                                            )
                                        }
                                        val uiSettings = remember {
                                            MapUiSettings(
                                                scrollGesturesEnabled = false,
                                                zoomGesturesEnabled = false,
                                                tiltGesturesEnabled = false,
                                                rotationGesturesEnabled = false,
                                                myLocationButtonEnabled = false,
                                                mapToolbarEnabled = false,
                                                compassEnabled = false,
                                                zoomControlsEnabled = false,
                                            )
                                        }

                                        GoogleMap(
                                            modifier = Modifier.fillMaxSize(),
                                            cameraPositionState = cameraPositionState,
                                            properties = properties,
                                            uiSettings = uiSettings,
                                        ) {
                                            Marker(
                                                state = markerState,
                                                title = state.categoryName,
                                            )
                                        }

                                        // Overlay to consume any map touches and ensure whole box clicks to map screen
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Transparent),
                                        )
                                    }

                                    // Button "Xem trên bản đồ"
                                    Surface(
                                        onClick = { onNavigateMap(transaction.id, state.selectedImageId) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF1B1914),
                                        border = BorderStroke(1.dp, Color(0xFFFFB800)),
                                        modifier = Modifier.height(44.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Map,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB800),
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = stringResource(R.string.map_title),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFFFB800),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    state.errorMessage?.let { msg ->
                        val text = if (msg == "Không thể xóa giao dịch") stringResource(R.string.tx_detail_error_delete_failed) else msg
                        BillLocketErrorBanner(text)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(TransactionDetailAction.CancelDelete) },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = { TextButton(onClick = { viewModel.onAction(TransactionDetailAction.ConfirmDelete) }) { Text(stringResource(R.string.detail_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(TransactionDetailAction.CancelDelete) }) { Text(stringResource(R.string.detail_delete_cancel)) } },
        )
    }
}

@Composable
private fun InfoItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    hasTrailingChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Gold icon tile
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x2E4A3B18)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(18.dp),
            )
        }

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            color = Color(0xFF9E9D98),
            modifier = Modifier.width(90.dp),
        )

        // Value text
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            ),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )

        if (hasTrailingChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF8E8D88),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HeroImageCard(
    item: DetailImage,
    amountText: String,
    transactionType: TransactionType,
    categoryName: String,
    categoryIconKey: String?,
    accountName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1E1D1A)),
    ) {
        if (item.exists) {
            AsyncImage(
                model = item.image.mediaUri,
                contentDescription = stringResource(R.string.tx_detail_photo_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.HideImage, null, Modifier.size(36.dp))
                    Text(stringResource(R.string.tx_detail_photo_unavailable))
                }
            }
        }

        // Gradient overlay for crisp readability matching Image 2
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        )

        // Overlay Content: Amount & Category/Account pill
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                ),
                color = if (transactionType == TransactionType.EXPENSE) Color(0xFFFF6B6B) else Color(0xFF66D19E),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.60f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        categoryIcon(categoryName, categoryIconKey),
                        contentDescription = null,
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (accountName.isNotBlank()) "$categoryName • $accountName" else categoryName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}
