package com.billlocket.app.feature.recap

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.feature.map.BILLLOCKET_DARK_MAP_STYLE_JSON
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.ui.res.stringResource
import com.billlocket.app.R
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.activeAppLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFB800)
private val DarkBackground = Color(0xFF0D0D0D)
private val DarkSurface = Color(0xFF191815)
private val DarkCardBorder = Color(0xFF2A2824)
private val TextMuted = Color(0xFFA8A39A)
private val ExpenseRed = Color(0xFFFF716D)
private val IncomeGreen = Color(0xFF66D19E)

@Composable
private fun formatVnd(minorUnits: Long): String {
    return BillLocketFormatters.formatMoney(minorUnits, activeAppLocale())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MonthlyRecapScreen(
    onBack: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenCalendar: () -> Unit = onBack,
    viewModel: MonthlyRecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showShareModal by remember { mutableStateOf(false) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is MonthlyRecapEffect.Share -> {
                val uri = Uri.parse(effect.uri)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Monthly Recap", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Chia sẻ tổng kết tháng").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    val recap = state.recap
    val isLatestMonth = state.month >= YearMonth.now()
    val pagerState = rememberPagerState(initialPage = 0) { 5 }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            RecapTopBar(
                month = state.month,
                canGoNext = !isLatestMonth,
                onBack = onBack,
                onPreviousMonth = {
                    viewModel.onAction(MonthlyRecapAction.PreviousMonth)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                onNextMonth = {
                    viewModel.onAction(MonthlyRecapAction.NextMonth)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                onShareClick = { showShareModal = true },
            )
        },
        bottomBar = {
            if (recap != null && !state.isLoading) {
                RecapBottomBar(
                    currentPage = pagerState.currentPage,
                    pageCount = 5,
                    onSelectPage = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
                    onNext = {
                        if (pagerState.currentPage < 4) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onOpenCalendar()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && recap == null -> {
                    RecapLoadingState()
                }
                state.errorMessage != null && recap == null -> {
                    val errorText = when (state.errorMessage) {
                        "Không thể tải tổng kết tháng",
                        "Không thể tạo tổng kết tháng" -> stringResource(R.string.recap_error_load_failed)
                        "Không thể tạo ảnh tổng kết" -> stringResource(R.string.recap_error_export_failed)
                        null -> stringResource(R.string.recap_error_load_failed)
                        else -> state.errorMessage.orEmpty()
                    }
                    RecapErrorState(
                        message = errorText,
                        onRetry = { viewModel.onAction(MonthlyRecapAction.Retry) },
                    )
                }
                recap != null -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> RecapCoverPage(
                                recap = recap,
                                onStart = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            )
                            1 -> RecapOverviewPage(
                                recap = recap,
                            )
                            2 -> RecapCategoryPage(
                                recap = recap,
                            )
                            3 -> RecapLocationPage(
                                recap = recap,
                                onOpenTransaction = onOpenTransaction,
                            )
                            4 -> RecapGalleryPage(
                                recap = recap,
                                onOpenTransaction = onOpenTransaction,
                                onOpenCalendar = onOpenCalendar,
                            )
                        }
                    }
                }
            }

            if (showShareModal && recap != null) {
                RecapShareModal(
                    recap = recap,
                    privacy = state.privacy,
                    isExporting = state.isExporting,
                    errorMessage = state.errorMessage,
                    onDismiss = { showShareModal = false },
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@Composable
private fun RecapTopBar(
    month: YearMonth,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShareClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_previous_month),
                    tint = Color.White,
                )
            }
            Text(
                text = BillLocketFormatters.formatMonthYear(month, activeAppLocale()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            IconButton(
                onClick = onNextMonth,
                enabled = canGoNext,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    tint = if (canGoNext) Color.White else Color(0xFF4A4843),
                )
            }
        }

        Surface(
            onClick = onShareClick,
            shape = CircleShape,
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.recap_generate_and_share),
                    tint = Gold,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun RecapBottomBar(
    currentPage: Int,
    pageCount: Int,
    onSelectPage: (Int) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until pageCount) {
                val isCurrent = i == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) Gold else Color(0xFF4A4843))
                        .clickable { onSelectPage(i) },
                )
            }
        }

        val buttonText = when (currentPage) {
            0 -> stringResource(R.string.recap_start_viewing)
            1 -> stringResource(R.string.recap_next)
            2 -> stringResource(R.string.recap_continue)
            3 -> stringResource(R.string.recap_continue_viewing)
            else -> stringResource(R.string.recap_view_calendar)
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RecapCoverPage(
    recap: MonthlyRecap,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recap_cover_title, recap.month.monthValue, recap.month.year),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Gold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = stringResource(R.string.recap_cover_subtitle),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 44.sp,
            )
        }

        // Hero photos collage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (recap.photos.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.recap_memories_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.recap_memories_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                RecapHeroCollage(photos = recap.photos)
            }
        }

        // Statistics row
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Gold.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoLibrary, null, tint = Gold, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "${recap.photos.size}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(stringResource(R.string.recap_moments_count), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Gold.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ReceiptLong, null, tint = Gold, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "${recap.transactionCount}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(stringResource(R.string.recap_transactions_count), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }

            Text(
                text = if (recap.transactionCount > 0) {
                    stringResource(R.string.recap_moments_recorded, recap.photos.size)
                } else {
                    stringResource(R.string.recap_no_transactions)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun RecapHeroCollage(photos: List<MonthlyRecapPhoto>) {
    val count = photos.size
    when {
        count == 1 -> {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f),
            ) {
                AsyncImage(
                    model = photos[0].mediaUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        count == 2 -> {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    AsyncImage(
                        model = photos[0].mediaUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    AsyncImage(
                        model = photos[1].mediaUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        else -> {
            // 3 or more photos: Big card on left, 2 stacked on right matching Mockup 7
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                ) {
                    AsyncImage(
                        model = photos[0].mediaUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AsyncImage(
                            model = photos[1].mediaUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AsyncImage(
                            model = photos[2].mediaUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapOverviewPage(
    recap: MonthlyRecap,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // Photos banner at top
            if (recap.photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                ) {
                    items(recap.photos.take(6), key = { it.imageId.value }) { photo ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkCardBorder),
                            modifier = Modifier
                                .width(120.dp)
                                .fillMaxHeight(),
                        ) {
                            AsyncImage(
                                model = photo.mediaUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.recap_month_title, recap.month.monthValue),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                )
                Text(
                    text = formatVnd(recap.expense.minorUnits),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.recap_total_spent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        }

        item {
            // Summary row: Thu & Còn lại
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IncomeGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ArrowDownward, null, tint = IncomeGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(stringResource(R.string.home_income), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(
                                text = formatVnd(recap.income.minorUnits),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Gold.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalanceWallet, null, tint = Gold, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(stringResource(R.string.recap_remaining), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            val netUnits = recap.net.minorUnits
                            val netFormatted = (if (netUnits > 0) "+" else "") + formatVnd(netUnits)
                            Text(
                                text = netFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (netUnits >= 0) IncomeGreen else ExpenseRed,
                            )
                        }
                    }
                }
            }
        }

        item {
            // Most active day card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recap_daily_spending),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )

                        if (recap.mostActiveDayEpoch != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Gold.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val activeDate = LocalDate.ofEpochDay(recap.mostActiveDayEpoch)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.recap_most_active_day),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.recap_most_active_day_format,
                                                activeDate.dayOfMonth,
                                                activeDate.monthValue,
                                                recap.mostActiveDayCount,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Gold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Month days distribution visualization
                    val daysInMonth = recap.month.lengthOfMonth()
                    val activeDayNumber = recap.mostActiveDayEpoch?.let { LocalDate.ofEpochDay(it).dayOfMonth } ?: -1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (day in 1..daysInMonth) {
                            val isActive = day == activeDayNumber
                            val heightFraction = if (isActive) 1.0f else ((day * 7) % 11 + 2) / 13f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(if (isActive) Gold else Color(0xFF33312C)),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("1", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${daysInMonth / 2}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapCategoryPage(
    recap: MonthlyRecap,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.recap_top_categories_header),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        item {
            val top = recap.topCategory
            if (top != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Gold.copy(alpha = 0.2f),
                                modifier = Modifier.size(46.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Restaurant, null, tint = Gold, modifier = Modifier.size(24.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = top.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                val percent = if (recap.expense.minorUnits > 0) {
                                    (top.amount.minorUnits * 100 / recap.expense.minorUnits).toInt()
                                } else 0
                                Text(
                                    text = stringResource(R.string.recap_category_spent_format, formatVnd(top.amount.minorUnits), percent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Gold,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Category photos if available
                        val catPhotos = recap.photos.filter { it.categoryName == top.name }
                        if (catPhotos.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(catPhotos.take(4), key = { it.imageId.value }) { photo ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = DarkBackground,
                                        modifier = Modifier.size(92.dp),
                                    ) {
                                        AsyncImage(
                                            model = photo.mediaUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.recap_no_category_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        item {
            // BillLocket Smart Insight Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Gold.copy(alpha = 0.18f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lightbulb, null, tint = Gold, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.recap_perspective_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                        val top = recap.topCategory
                        val insightText = if (top != null && recap.expense.minorUnits > 0) {
                            val ratio = (top.amount.minorUnits * 10 / recap.expense.minorUnits).toInt().coerceAtLeast(1)
                            stringResource(R.string.recap_perspective_ratio, ratio, top.name)
                        } else {
                            stringResource(R.string.recap_perspective_keep_recording)
                        }
                        Text(
                            text = insightText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapLocationPage(
    recap: MonthlyRecap,
    onOpenTransaction: (TransactionId) -> Unit,
) {
    val located = recap.locatedPhotos
    var selectedLocationPhoto by remember(located) { mutableStateOf(located.firstOrNull()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.recap_places_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.recap_places_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        if (located.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.LocationOff, null, tint = Gold, modifier = Modifier.size(42.dp))
                        Text(
                            text = stringResource(R.string.recap_no_places_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.recap_no_places_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            // Interactive Map container
            val photosWithCoords = located.filter { it.latitude != null && it.longitude != null }
            val firstCoord = photosWithCoords.firstOrNull()
            val savedLocationTitle = stringResource(R.string.recap_saved_location)

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (firstCoord != null) {
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(
                            LatLng(firstCoord.latitude!!, firstCoord.longitude!!),
                            12.5f,
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                mapStyleOptions = MapStyleOptions(BILLLOCKET_DARK_MAP_STYLE_JSON),
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                mapToolbarEnabled = false,
                            ),
                        ) {
                            photosWithCoords.forEach { photo ->
                                val markerState = rememberMarkerState(
                                    key = photo.imageId.value,
                                    position = LatLng(photo.latitude!!, photo.longitude!!),
                                )
                                Marker(
                                    state = markerState,
                                    title = photo.placeName ?: savedLocationTitle,
                                    snippet = photo.categoryName,
                                    onClick = {
                                        selectedLocationPhoto = photo
                                        true
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // Fallback when coordinates are missing
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.recap_saved_locations_monthly), color = TextMuted)
                    }
                }
            }

            // Bottom place detail card
            val currentSelected = selectedLocationPhoto ?: located.first()
            val placeName = currentSelected.placeName ?: savedLocationTitle
            val photosAtPlace = located.filter { it.placeName == placeName }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = placeName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.recap_place_occurred),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }

                        Text(
                            text = stringResource(R.string.recap_place_moments_count, photosAtPlace.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(photosAtPlace, key = { it.imageId.value }) { photo ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DarkBackground,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable { onOpenTransaction(photo.transactionId) },
                            ) {
                                AsyncImage(
                                    model = photo.mediaUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapGalleryPage(
    recap: MonthlyRecap,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.recap_photos_overview_title, recap.month.monthValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.recap_photos_overview_subtitle, recap.photos.size, recap.transactionCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        if (recap.photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Gold, modifier = Modifier.size(40.dp))
                        Text(
                            text = stringResource(R.string.recap_no_photos_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.recap_no_photos_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Render 2 photos per row
                val rows = recap.photos.chunked(2)
                items(rows) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (photo in pair) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.dp)
                                    .clickable { onOpenTransaction(photo.transactionId) },
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = photo.mediaUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Tag badge overlay
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp),
                                    ) {
                                        val day = LocalDate.ofEpochDay(photo.epochDay).dayOfMonth
                                        Text(
                                            text = "$day/${recap.month.monthValue} | ${formatVnd(photo.amount.minorUnits)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.recap_view_calendar), color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecapShareModal(
    recap: MonthlyRecap,
    privacy: RecapPrivacy,
    isExporting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAction: (MonthlyRecapAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A4843)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.recap_preview_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.recap_preview_share_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }

            // Preview card in 1080x1350 aspect ratio style
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkBackground,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("BillLocket", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold)
                        Text("MM/${recap.month.year}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.recap_review_month_header, recap.month.monthValue, recap.month.year),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (privacy.showAmounts) {
                            Text(
                                text = stringResource(R.string.recap_spent_amount, formatVnd(recap.expense.minorUnits)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.recap_amount_hidden),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                    }

                    if (recap.photos.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                        ) {
                            recap.photos.take(4).forEach { p ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurface,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    AsyncImage(
                                        model = p.mediaUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    Text(stringResource(R.string.recap_tagline_private), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }

            // Privacy options
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrivacyOptionRow(
                    label = stringResource(R.string.recap_toggle_amount),
                    icon = Icons.Default.AttachMoney,
                    checked = privacy.showAmounts,
                    onCheckedChange = { onAction(MonthlyRecapAction.ShowAmounts(it)) },
                )
                PrivacyOptionRow(
                    label = stringResource(R.string.recap_toggle_notes),
                    icon = Icons.Default.Description,
                    checked = privacy.showNotes,
                    onCheckedChange = { onAction(MonthlyRecapAction.ShowNotes(it)) },
                )
                PrivacyOptionRow(
                    label = stringResource(R.string.recap_toggle_locations),
                    icon = Icons.Default.Place,
                    checked = privacy.showPlaces,
                    onCheckedChange = { onAction(MonthlyRecapAction.ShowPlaces(it)) },
                )
            }

            if (errorMessage != null) {
                val errorText = when (errorMessage) {
                    "Không thể tải tổng kết tháng",
                    "Không thể tạo tổng kết tháng" -> stringResource(R.string.recap_error_load_failed)
                    "Không thể tạo ảnh tổng kết" -> stringResource(R.string.recap_error_export_failed)
                    else -> errorMessage
                }
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { onAction(MonthlyRecapAction.Export) },
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recap_generating_image), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recap_generate_and_share), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacyOptionRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF22201C),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = Gold, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Gold,
                    checkmarkColor = Color.Black,
                    uncheckedColor = Color(0xFF66645E),
                ),
            )
        }
    }
}

@Composable
private fun RecapLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = Gold)
            Text(
                text = stringResource(R.string.recap_compiling_story),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun RecapErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = ExpenseRed, modifier = Modifier.size(46.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.recap_retry), fontWeight = FontWeight.Bold)
            }
        }
    }
}
