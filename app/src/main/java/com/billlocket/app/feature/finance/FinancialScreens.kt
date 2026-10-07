package com.billlocket.app.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.billlocket.app.domain.model.*
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.BillLocketSectionCard
import com.billlocket.app.ui.common.BillLocketGroupCard
import com.billlocket.app.ui.common.BillLocketGroupItem
import com.billlocket.app.ui.common.BillLocketErrorBanner
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun money(value: Long) = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(value) + " ₫"

@Composable
fun MoreScreen(onAccounts: () -> Unit, onCategories: () -> Unit, onTransfers: () -> Unit, onBudgets: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, top = 24.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Của bạn", style = MaterialTheme.typography.headlineLarge)
                Text("Quản lý tiền theo cách riêng", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(26.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("BillLocket", style = MaterialTheme.typography.titleLarge)
                        Text("Tài chính của bạn, lưu bằng ảnh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text(
                "Quản lý",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        item {
            BillLocketGroupCard {
                BillLocketGroupItem(
                    title = "Tài khoản",
                    subtitle = "Số dư và thứ tự tài khoản",
                    icon = Icons.Filled.AccountBalanceWallet,
                    onClick = onAccounts,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = "Danh mục",
                    subtitle = "Danh mục thu và chi",
                    icon = Icons.Filled.Category,
                    onClick = onCategories,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = "Chuyển tiền",
                    subtitle = "Lịch sử chuyển giữa tài khoản",
                    icon = Icons.Filled.SwapHoriz,
                    onClick = onTransfers,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = "Ngân sách",
                    subtitle = "Hạn mức chi theo tháng",
                    icon = Icons.Filled.Savings,
                    onClick = onBudgets,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = "Cài đặt",
                    subtitle = "Giao diện, quyền riêng tư và dữ liệu",
                    icon = Icons.Filled.Settings,
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccountsScreen(
    onBack: (() -> Unit)? = null,
    onCreate: () -> Unit,
    onEdit: (AccountId) -> Unit,
    onOpen: (AccountId) -> Unit,
    onTransfers: () -> Unit = {},
    onCategories: () -> Unit = {},
    onBudgets: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    var showTopMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onBack != null) {
                            BackButton(onBack)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Tài khoản",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Quản lý số dư và các tài khoản của bạn",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box {
                        Surface(
                            onClick = { showTopMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = "Tùy chọn",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chuyển tiền") },
                                leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                                onClick = { showTopMenu = false; onTransfers() },
                            )
                            DropdownMenuItem(
                                text = { Text("Danh mục") },
                                leadingIcon = { Icon(Icons.Filled.Category, null) },
                                onClick = { showTopMenu = false; onCategories() },
                            )
                            DropdownMenuItem(
                                text = { Text("Ngân sách") },
                                leadingIcon = { Icon(Icons.Filled.Savings, null) },
                                onClick = { showTopMenu = false; onBudgets() },
                            )
                            DropdownMenuItem(
                                text = { Text("Cài đặt") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showTopMenu = false; onSettings() },
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.errorMessage?.let { msg ->
                item { BillLocketErrorBanner(msg) }
            }

            val active = state.items.filterNot { it.account.isArchived }.sortedBy { it.account.sortOrder }
            val totalBalance = active.sumOf { it.balance?.minorUnits ?: 0L }

            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                ) {
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = "Tổng số dư",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(
                                    onClick = { viewModel.onAction(AccountsAction.ToggleAmountVisibility) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = if (state.amountVisibility == AmountVisibility.SHOWN) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = "Ẩn/hiện số dư",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                listOf(12.dp, 20.dp, 16.dp, 28.dp, 24.dp).forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(h)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                RoundedCornerShape(2.dp),
                                            ),
                                    )
                                }
                            }
                        }

                        val balanceFormatted = if (state.amountVisibility == AmountVisibility.HIDDEN) "••••••••" else money(totalBalance)
                        Text(
                            text = balanceFormatted,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (totalBalance < 0 && state.amountVisibility != AmountVisibility.HIDDEN) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = "${active.size} tài khoản đang hoạt động",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                AccountPeriodTab.THIS_MONTH to "Tháng này",
                                AccountPeriodTab.THIS_YEAR to "Năm nay",
                                AccountPeriodTab.ALL to "Tất cả",
                            ).forEach { (tab, label) ->
                                val selected = state.periodTab == tab
                                Surface(
                                    onClick = { viewModel.onAction(AccountsAction.SetPeriodTab(tab)) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (state.periodTab == AccountPeriodTab.THIS_MONTH) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    IconButton(
                                        onClick = { viewModel.onAction(AccountsAction.PreviousMonth) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Tháng trước",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = "tháng ${state.anchorMonth.monthValue} ${state.anchorMonth.year}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    IconButton(
                                        onClick = { viewModel.onAction(AccountsAction.NextMonth) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Tháng sau",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.SouthWest,
                                        contentDescription = null,
                                        tint = Color(0xFF66BB6A),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Thu nhập",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val inc = state.periodIncome?.minorUnits ?: 0L
                                Text(
                                    text = if (state.amountVisibility == AmountVisibility.HIDDEN) "••••••••" else money(inc),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF66BB6A),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.NorthEast,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Chi tiêu",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val exp = state.periodExpense?.minorUnits ?: 0L
                                Text(
                                    text = if (state.amountVisibility == AmountVisibility.HIDDEN) "••••••••" else money(exp),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = onTransfers,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Chuyển tiền",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Surface(
                        onClick = onCreate,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Tài khoản",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tài khoản của bạn (${active.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (active.size > 1) {
                        Surface(
                            onClick = { viewModel.onAction(AccountsAction.ToggleReorderMode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.isReorderMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (state.isReorderMode) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.isReorderMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    tint = if (state.isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (state.isReorderMode) "Xong" else "Sắp xếp",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isLoading && active.isEmpty()) {
                item {
                    BillLocketEmptyState(
                        Icons.Filled.AccountBalanceWallet,
                        "Chưa có tài khoản",
                        "Tạo ví tiền mặt hoặc tài khoản ngân hàng để bắt đầu.",
                        actionLabel = "Thêm tài khoản",
                        onAction = onCreate,
                    )
                }
            }

            items(active, key = { it.account.id.value }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item.account.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (item.account.type) {
                                        AccountType.BANK -> Icons.Filled.AccountBalance
                                        AccountType.EWALLET -> Icons.Filled.Payments
                                        AccountType.CASH -> Icons.Filled.AccountBalanceWallet
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = accountType(item.account.type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val bal = item.balance?.minorUnits ?: 0L
                            val balColor = if (bal < 0 && state.amountVisibility != AmountVisibility.HIDDEN) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                            Text(
                                text = if (state.amountVisibility == AmountVisibility.HIDDEN) "••••••••" else money(bal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = balColor,
                            )

                            if (state.isReorderMode) {
                                IconButton(
                                    onClick = { viewModel.onAction(AccountsAction.Move(item.account.id, -1)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Đưa lên", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.onAction(AccountsAction.Move(item.account.id, 1)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Đưa xuống", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Box {
                                    var itemMenuExpanded by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable { itemMenuExpanded = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "Tùy chọn tài khoản",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = itemMenuExpanded,
                                        onDismissRequest = { itemMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Chỉnh sửa") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                            onClick = { itemMenuExpanded = false; onEdit(item.account.id) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Lưu trữ") },
                                            leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                            onClick = {
                                                itemMenuExpanded = false
                                                viewModel.onAction(AccountsAction.AskArchive(item.account))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreate),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Thêm tài khoản mới",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Quản lý tất cả tài khoản của bạn tại đây",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            val archived = state.items.filter { it.account.isArchived }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        text = "Đã lưu trữ (${archived.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(archived, key = { "archived-${it.account.id.value}" }) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.account.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = item.account.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (state.amountVisibility == AmountVisibility.HIDDEN) "••••••••" else money(item.balance?.minorUnits ?: 0L),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Quản lý & Cài đặt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
            }
            item {
                BillLocketGroupCard {
                    BillLocketGroupItem(
                        title = "Danh mục",
                        subtitle = "Danh mục thu và chi",
                        icon = Icons.Filled.Category,
                        onClick = onCategories,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    BillLocketGroupItem(
                        title = "Ngân sách",
                        subtitle = "Hạn mức chi theo tháng",
                        icon = Icons.Filled.Savings,
                        onClick = onBudgets,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    BillLocketGroupItem(
                        title = "Cài đặt",
                        subtitle = "Giao diện, widget và dữ liệu",
                        icon = Icons.Filled.Settings,
                        onClick = onSettings,
                    )
                }
            }
        }
    }
    state.archiveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(AccountsAction.AskArchive(null)) },
            title = { Text("Lưu trữ tài khoản?") },
            text = { Text("${target.name} sẽ không dùng cho giao dịch mới. Lịch sử và số dư vẫn được giữ nguyên.") },
            confirmButton = { TextButton(onClick = { viewModel.onAction(AccountsAction.ConfirmArchive) }) { Text("Lưu trữ") } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(AccountsAction.AskArchive(null)) }) { Text("Hủy") } },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccountEditorScreen(onBack: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()
    CollectEffects(viewModel.effects) { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (state.id == null) "Thêm tài khoản" else "Sửa tài khoản") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.name, { viewModel.onAction(AccountEditorAction.NameChanged(it)) }, label = { Text("Tên tài khoản") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type -> FilterChip(state.type == type, { viewModel.onAction(AccountEditorAction.TypeChanged(type)) }, { Text(accountType(type)) }) }
            }
            OutlinedTextField(
                state.openingBalanceText,
                { viewModel.onAction(AccountEditorAction.OpeningBalanceChanged(it)) },
                label = { Text("Số dư ban đầu") },
                enabled = state.id == null,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.onAction(AccountEditorAction.Save) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Lưu") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccountDetailScreen(onBack: () -> Unit, onEdit: (AccountId) -> Unit, viewModel: AccountDetailViewModel = hiltViewModel()) {
    val details by viewModel.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Chi tiết tài khoản") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        val value = details
        if (value == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(accountType(value.account.type).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(value.account.name, style = MaterialTheme.typography.headlineMedium)
                    Text("Số dư hiện tại", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(value.balance.minorUnits), style = MaterialTheme.typography.headlineLarge)
                }
            }
            BillLocketSectionCard { Text("Số dư ban đầu", style = MaterialTheme.typography.labelMedium); Text(money(value.account.openingBalance.minorUnits), style = MaterialTheme.typography.titleMedium) }
            if (value.account.isArchived) AssistChip(onClick = {}, label = { Text("Đã lưu trữ") })
            Button(onClick = { onEdit(value.account.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Chỉnh sửa tài khoản") }
        }
    }
}

val AVAILABLE_CATEGORY_COLORS = listOf(
    0xFFFFB300L, // Amber
    0xFFFF7043L, // Coral
    0xFFFFD54FL, // Gold
    0xFF42A5F5L, // Blue
    0xFF66BB6AL, // Green
    0xFF26C6DAL, // Cyan
    0xFF26A69AL, // Teal
    0xFFAB47BCL, // Purple
    0xFF7E57C2L, // Deep Purple
    0xFFEC407AL, // Pink
    0xFFEF5350L, // Red
    0xFF8D6E63L, // Brown
    0xFF78909CL, // Blue Gray
)

val AVAILABLE_CATEGORY_ICONS = listOf(
    "restaurant" to Icons.Filled.Restaurant,
    "local_cafe" to Icons.Filled.LocalCafe,
    "shopping_bag" to Icons.Filled.ShoppingBag,
    "checkroom" to Icons.Filled.Checkroom,
    "directions_bus" to Icons.Filled.DirectionsBus,
    "local_gas_station" to Icons.Filled.LocalGasStation,
    "local_parking" to Icons.Filled.LocalParking,
    "home" to Icons.Filled.Home,
    "bolt" to Icons.Filled.Bolt,
    "water_drop" to Icons.Filled.WaterDrop,
    "wifi" to Icons.Filled.Wifi,
    "phone_android" to Icons.Filled.PhoneAndroid,
    "school" to Icons.Filled.School,
    "menu_book" to Icons.Filled.MenuBook,
    "spa" to Icons.Filled.Spa,
    "fitness_center" to Icons.Filled.FitnessCenter,
    "medical_services" to Icons.Filled.MedicalServices,
    "sports_esports" to Icons.Filled.SportsEsports,
    "flight" to Icons.Filled.Flight,
    "redeem" to Icons.Filled.CardGiftcard,
    "family_restroom" to Icons.Filled.FamilyRestroom,
    "child_care" to Icons.Filled.ChildCare,
    "pets" to Icons.Filled.Pets,
    "work" to Icons.Filled.Work,
    "edit_note" to Icons.Filled.EditNote,
    "receipt_long" to Icons.Filled.ReceiptLong,
    "verified_user" to Icons.Filled.VerifiedUser,
    "credit_score" to Icons.Filled.CreditScore,
    "credit_card" to Icons.Filled.CreditCard,
    "payments" to Icons.Filled.Payments,
    "schedule" to Icons.Filled.Schedule,
    "store" to Icons.Filled.Store,
    "laptop" to Icons.Filled.Laptop,
    "show_chart" to Icons.Filled.ShowChart,
    "savings" to Icons.Filled.Savings,
    "trending_up" to Icons.Filled.TrendingUp,
    "replay" to Icons.Filled.Replay,
    "sell" to Icons.Filled.Sell,
    "volunteer_activism" to Icons.Filled.VolunteerActivism,
    "price_check" to Icons.Filled.PriceCheck,
    "account_balance" to Icons.Filled.AccountBalance,
    "more_horiz" to Icons.Filled.MoreHoriz,
)

fun categoryIcon(name: String, iconKey: String? = null): ImageVector {
    val key = ((iconKey ?: "") + " " + name).lowercase()
    return when {
        "cafe" in key || "cà phê" in key || "coffee" in key -> Icons.Filled.LocalCafe
        "ăn" in key || "uống" in key || "food" in key || "dining" in key || "restaurant" in key -> Icons.Filled.Restaurant
        "quần" in key || "áo" in key || "clothing" in key || "checkroom" in key -> Icons.Filled.Checkroom
        "mua" in key || "shop" in key || "shopping" in key -> Icons.Filled.ShoppingBag
        "xăng" in key || "gas" in key || "nhiên liệu" in key -> Icons.Filled.LocalGasStation
        "gửi xe" in key || "đỗ xe" in key || "parking" in key -> Icons.Filled.LocalParking
        "xe" in key || "di chuyển" in key || "transport" in key || "bus" in key -> Icons.Filled.DirectionsBus
        "nhà" in key || "cửa" in key || "home" in key || "house" in key -> Icons.Filled.Home
        "điện thoại" in key || "phone" in key -> Icons.Filled.PhoneAndroid
        "điện" in key || "bolt" in key || "electricity" in key -> Icons.Filled.Bolt
        "nước" in key || "water" in key -> Icons.Filled.WaterDrop
        "internet" in key || "mạng" in key || "wifi" in key -> Icons.Filled.Wifi
        "học" in key || "trường" in key || "school" in key || "education" in key -> Icons.Filled.School
        "sách" in key || "book" in key -> Icons.Filled.MenuBook
        "đẹp" in key || "spa" in key || "mỹ phẩm" in key || "beauty" in key -> Icons.Filled.Spa
        "thuốc" in key || "pharmacy" in key || "y tế" in key || "bệnh" in key -> Icons.Filled.MedicalServices
        "khỏe" in key || "gym" in key || "fitness" in key || "health" in key -> Icons.Filled.FitnessCenter
        "giải trí" in key || "game" in key || "chơi" in key || "entertainment" in key -> Icons.Filled.SportsEsports
        "du lịch" in key || "bay" in key || "flight" in key || "travel" in key -> Icons.Filled.Flight
        "quà" in key || "gift" in key || "redeem" in key -> Icons.Filled.CardGiftcard
        "con" in key || "trẻ" in key || "child" in key || "baby" in key -> Icons.Filled.ChildCare
        "gia đình" in key || "family" in key -> Icons.Filled.FamilyRestroom
        "thú cưng" in key || "pet" in key || "chó" in key || "mèo" in key -> Icons.Filled.Pets
        "văn phòng" in key || "bút" in key || "stationery" in key || "edit_note" in key -> Icons.Filled.EditNote
        "công việc" in key || "work" in key -> Icons.Filled.Work
        "phí" in key || "dịch vụ" in key || "service" in key || "receipt" in key -> Icons.Filled.ReceiptLong
        "bảo hiểm" in key || "insurance" in key || "shield" in key -> Icons.Filled.VerifiedUser
        "trả góp" in key || "installment" in key || "score" in key -> Icons.Filled.CreditScore
        "nợ" in key || "debt" in key || "vay" in key -> Icons.Filled.CreditCard
        "thuế" in key || "tax" in key || "ngân hàng" in key || "bank" in key -> Icons.Filled.AccountBalance
        "lương" in key || "salary" in key || "payments" in key -> Icons.Filled.Payments
        "thưởng" in key || "bonus" in key -> Icons.Filled.CardGiftcard
        "làm thêm" in key || "part_time" in key || "schedule" in key || "thời gian" in key -> Icons.Filled.Schedule
        "kinh doanh" in key || "business" in key || "store" in key || "bán hàng" in key -> Icons.Filled.Store
        "freelance" in key || "laptop" in key || "máy tính" in key -> Icons.Filled.Laptop
        "đầu tư" in key || "invest" in key || "chứng khoán" in key -> Icons.Filled.ShowChart
        "tiết kiệm" in key || "savings" in key || "lãi" in key -> Icons.Filled.Savings
        "cổ tức" in key || "dividends" in key || "trending" in key -> Icons.Filled.TrendingUp
        "hoàn tiền" in key || "cashback" in key || "replay" in key -> Icons.Filled.Replay
        "bán đồ" in key || "sell" in key || "thanh lý" in key -> Icons.Filled.Sell
        "trợ cấp" in key || "allowance" in key || "từ thiện" in key -> Icons.Filled.VolunteerActivism
        "thu nợ" in key || "đòi nợ" in key || "price_check" in key -> Icons.Filled.PriceCheck
        "khác" in key || "other" in key || "more" in key -> Icons.Filled.MoreHoriz
        else -> Icons.Filled.Category
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh mục", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Surface(
                        onClick = { viewModel.onAction(CategoriesAction.Edit(null)) },
                        shape = CircleShape,
                        color = Color(0x1AFFB800),
                        border = BorderStroke(1.dp, Color(0x4DFFB800)),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Thêm danh mục",
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Segmented Pills [ Chi | Thu ]
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isExpense = state.selectedType == TransactionType.EXPENSE
                Surface(
                    onClick = { viewModel.onAction(CategoriesAction.SelectType(TransactionType.EXPENSE)) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isExpense) Color(0x26FFB800) else Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, if (isExpense) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                    modifier = Modifier.height(38.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Chi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                            color = if (isExpense) Color(0xFFFFB800) else Color(0xFF8E8B85),
                        )
                    }
                }

                val isIncome = state.selectedType == TransactionType.INCOME
                Surface(
                    onClick = { viewModel.onAction(CategoriesAction.SelectType(TransactionType.INCOME)) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isIncome) Color(0x26FFB800) else Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, if (isIncome) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                    modifier = Modifier.height(38.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Thu",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                            color = if (isIncome) Color(0xFFFFB800) else Color(0xFF8E8B85),
                        )
                    }
                }
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            val activeCategories = remember(state.items) {
                state.items.filterNot { it.isArchived }.sortedBy { it.sortOrder }
            }
            val archivedCategories = remember(state.items) {
                state.items.filter { it.isArchived }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (activeCategories.isEmpty()) {
                    item {
                        BillLocketEmptyState(
                            Icons.Filled.Category,
                            "Chưa có danh mục",
                            "Tạo danh mục để phân loại giao dịch.",
                            actionLabel = "Thêm danh mục",
                            onAction = { viewModel.onAction(CategoriesAction.Edit(null)) },
                        )
                    }
                }

                itemsIndexed(activeCategories, key = { _, item -> item.id.value }) { index, category ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF161513),
                        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Category Icon Tile
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(category.colorArgb), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = categoryIcon(category.name, category.iconKey),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            // Category Name & Type
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp, end = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (category.isDefault) "Mặc định" else "Tùy chỉnh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E8B85),
                                )
                            }

                            // Action buttons group [ ^ | v | ✎ | 🗄 ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    onClick = { viewModel.onAction(CategoriesAction.Move(category.id, -1)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF24221F),
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "Đưa lên",
                                            tint = if (index > 0) Color(0xFFCCCCCC) else Color(0xFF555555),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { viewModel.onAction(CategoriesAction.Move(category.id, 1)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF24221F),
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Đưa xuống",
                                            tint = if (index < activeCategories.lastIndex) Color(0xFFCCCCCC) else Color(0xFF555555),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { viewModel.onAction(CategoriesAction.Edit(category)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF24221F),
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Sửa",
                                            tint = Color(0xFFCCCCCC),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { viewModel.onAction(CategoriesAction.AskArchive(category)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF24221F),
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Archive,
                                            contentDescription = "Lưu trữ",
                                            tint = Color(0xFFCCCCCC),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (archivedCategories.isNotEmpty()) {
                    item {
                        Text(
                            text = "Đã lưu trữ (${archivedCategories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF8E8B85),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(archivedCategories, key = { "archived-${it.id.value}" }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF131210),
                            border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(item.colorArgb).copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(item.name, item.iconKey),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF75726B),
                                    modifier = Modifier.weight(1f),
                                )
                                Text("Đã ẩn", style = MaterialTheme.typography.labelSmall, color = Color(0xFF555555))
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showEditor) {
        CategoryEditorSheet(state, viewModel::onAction)
    }

    state.archiveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(CategoriesAction.AskArchive(null)) },
            containerColor = Color(0xFF1E1D19),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Lưu trữ danh mục?") },
            text = { Text("${target.name} sẽ bị ẩn khỏi giao dịch mới nhưng lịch sử và số liệu cũ vẫn được giữ nguyên.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onAction(CategoriesAction.ConfirmArchive) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800), contentColor = Color.Black),
                ) { Text("Lưu trữ") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(CategoriesAction.AskArchive(null)) }) {
                    Text("Hủy", color = Color(0xFF8E8B85))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(state: CategoriesState, onAction: (CategoriesAction) -> Unit) {
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = { onAction(CategoriesAction.Edit(null)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF181714),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF44423E), CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.editing == null) "Thêm danh mục" else "Sửa danh mục",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Surface(
                    onClick = { onAction(CategoriesAction.Edit(null)) },
                    shape = CircleShape,
                    color = Color(0xFF262421),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Live Preview Tile
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(state.colorArgb), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(state.name, state.iconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = state.name.ifBlank { "Tên danh mục" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.name.isBlank()) Color(0xFF666666) else Color.White,
                )
            }

            // Category Type (Chi / Thu) if creating new
            if (state.editing == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isExpense = state.selectedType == TransactionType.EXPENSE
                    Surface(
                        onClick = { onAction(CategoriesAction.SelectType(TransactionType.EXPENSE)) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isExpense) Color(0x26FFB800) else Color(0xFF1E1D19),
                        border = BorderStroke(1.dp, if (isExpense) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Chi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                                color = if (isExpense) Color(0xFFFFB800) else Color(0xFF8E8B85),
                            )
                        }
                    }

                    val isIncome = state.selectedType == TransactionType.INCOME
                    Surface(
                        onClick = { onAction(CategoriesAction.SelectType(TransactionType.INCOME)) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isIncome) Color(0x26FFB800) else Color(0xFF1E1D19),
                        border = BorderStroke(1.dp, if (isIncome) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Thu",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                                color = if (isIncome) Color(0xFFFFB800) else Color(0xFF8E8B85),
                            )
                        }
                    }
                }
            }

            // Name Field
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(CategoriesAction.NameChanged(it)) },
                label = { Text("Tên danh mục") },
                singleLine = true,
                trailingIcon = {
                    if (state.name.isNotEmpty()) {
                        IconButton(onClick = { onAction(CategoriesAction.NameChanged("")) }) {
                            Icon(Icons.Filled.Close, "Xóa", modifier = Modifier.size(18.dp), tint = Color(0xFF8E8B85))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1D19),
                    unfocusedContainerColor = Color(0xFF1E1D19),
                    focusedBorderColor = Color(0xFFFFB800),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = Color(0xFFFFB800),
                    focusedLabelColor = Color(0xFFFFB800),
                    unfocusedLabelColor = Color(0xFF8E8B85),
                ),
            )

            // Color Palette Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Màu sắc",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFCCCCCC),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(AVAILABLE_CATEGORY_COLORS) { color ->
                        val isSelected = color == state.colorArgb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { onAction(CategoriesAction.ColorChanged(color)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 32.dp else 26.dp)
                                    .background(Color(color), CircleShape)
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                        else Modifier
                                    ),
                            )
                        }
                    }
                }
            }

            // Icon Picker Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Biểu tượng",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFCCCCCC),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(AVAILABLE_CATEGORY_ICONS) { (key, iconVector) ->
                        val isSelected = key == state.iconKey
                        Surface(
                            onClick = { onAction(CategoriesAction.IconKeyChanged(key)) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0x26FFB800) else Color(0xFF22201D),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = key,
                                    tint = if (isSelected) Color(0xFFFFB800) else Color(0xFFCCCCCC),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons [ Hủy | Lưu ]
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = { onAction(CategoriesAction.Edit(null)) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Hủy", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Button(
                    onClick = { onAction(CategoriesAction.Save) },
                    enabled = state.name.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB800),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0x33FFB800),
                        disabledContentColor = Color(0x66000000),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Lưu", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransfersScreen(onBack: () -> Unit, onCreate: () -> Unit, onEdit: (TransferId) -> Unit, viewModel: TransfersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Chuyển tiền") }, navigationIcon = { BackButton(onBack) }, actions = { IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, "Thêm chuyển tiền") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.errorMessage?.let { item { BillLocketErrorBanner(it) } }
            if (!state.isLoading && state.items.isEmpty()) item { BillLocketEmptyState(Icons.Filled.SwapHoriz, "Chưa có chuyển tiền", "Chuyển tiền giữa hai tài khoản mà không làm thay đổi thu chi.", actionLabel = "Tạo chuyển tiền", onAction = onCreate) }
            items(state.items, key = { it.transfer.id.value }) { item ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onEdit(item.transfer.id) },
                    RoundedCornerShape(18.dp),
                    MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.SwapHoriz, null) } }
                        Column(Modifier.weight(1f)) { Text("${item.fromName} → ${item.toName}", style = MaterialTheme.typography.titleSmall); Text(item.transfer.note ?: Instant.ofEpochMilli(item.transfer.occurred.epochMillis).atZone(ZoneId.of(item.transfer.occurred.zoneId)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(money(item.transfer.amount.minorUnits), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransferEditorScreen(onBack: () -> Unit, viewModel: TransferEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()
    CollectEffects(viewModel.effects) { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (state.id == null) "Thêm chuyển tiền" else "Sửa chuyển tiền") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Từ tài khoản")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.accounts.forEach { account -> FilterChip(state.fromId == account.id, { viewModel.onAction(TransferEditorAction.FromChanged(account.id)) }, { Text(account.name) }) } }
            Text("Đến tài khoản")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.accounts.forEach { account -> FilterChip(state.toId == account.id, { viewModel.onAction(TransferEditorAction.ToChanged(account.id)) }, { Text(account.name) }) } }
            OutlinedTextField(state.amountText, { viewModel.onAction(TransferEditorAction.AmountChanged(it)) }, label = { Text("Số tiền") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.note, { viewModel.onAction(TransferEditorAction.NoteChanged(it)) }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.onAction(TransferEditorAction.Save) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Lưu chuyển tiền") }
        }
    }
}

@Composable private fun BackButton(onBack: () -> Unit) = IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
private fun accountType(type: AccountType) = when (type) { AccountType.CASH -> "Tiền mặt"; AccountType.BANK -> "Ngân hàng"; AccountType.EWALLET -> "Ví điện tử" }
