package com.billlocket.app.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.ui.common.BillLocketSectionCard
import com.billlocket.app.ui.common.BillLocketGroupCard
import com.billlocket.app.ui.common.BillLocketGroupItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StorageState(val usage: MediaStorageUsage? = null, val busy: Boolean = false, val message: String? = null, val pendingImport: BackupSummary? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val media: MediaStorePort,
    private val backup: BackupPort,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    private val _storage = MutableStateFlow(StorageState())
    val storage = _storage.asStateFlow()

    init { refreshStorage() }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { settingsRepository.updateTheme(value) }
    fun setAmounts(value: AmountVisibility) = viewModelScope.launch { settingsRepository.updateAmountVisibility(value) }
    fun setCurrency(value: String) = viewModelScope.launch { val result = settingsRepository.updateCurrency(value); if (result is Outcome.Failure) _storage.update { it.copy(message = "Không thể đổi tiền tệ khi đã có dữ liệu tài chính") } }
    fun refreshStorage() = viewModelScope.launch { when (val result = media.measureStorage()) { is Outcome.Success -> _storage.update { it.copy(usage = result.value) }; is Outcome.Failure -> _storage.update { it.copy(message = "Không đọc được dung lượng") } } }
    fun cleanup() = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        when (val result = media.reconcile(System.currentTimeMillis())) {
            is Outcome.Success -> _storage.update { it.copy(busy = false, message = "Đã dọn ${result.value.removedOrphanFiles + result.value.removedStagingFiles + result.value.removedTrashOrCacheFiles} tệp không còn dùng") }
            is Outcome.Failure -> _storage.update { it.copy(busy = false, message = "Không thể dọn lưu trữ") }
        }
        refreshStorage()
    }
    fun export(options: BackupOptions, output: OutputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        val result = backup.export(options, output)
        _storage.update { it.copy(busy = false, message = if (result is Outcome.Success) "Đã xuất bản sao lưu" else "Xuất bản sao lưu thất bại") }
    }
    fun inspect(input: InputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        when (val result = backup.inspect(input)) {
            is Outcome.Success -> _storage.update { it.copy(busy = false, pendingImport = result.value) }
            is Outcome.Failure -> _storage.update { it.copy(busy = false, message = "Tệp sao lưu không hợp lệ") }
        }
    }
    fun importReplace(input: InputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, pendingImport = null, message = null) }
        val result = backup.importReplace(input)
        _storage.update { it.copy(busy = false, message = if (result is Outcome.Success) "Khôi phục thành công" else "Khôi phục thất bại; dữ liệu cũ được giữ nguyên") }
        refreshStorage()
    }
    fun dismissImport() = _storage.update { it.copy(pendingImport = null) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GeneralSettingsScreen(onBack: () -> Unit, onWidget: () -> Unit, onStorage: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val value by viewModel.settings.collectAsState()
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Cài đặt") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column { Text("Cá nhân hóa", style = MaterialTheme.typography.headlineMedium); Text("Giao diện, riêng tư và dữ liệu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            BillLocketSectionCard {
                Text("Giao diện", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ThemeMode.entries.forEach { mode -> FilterChip(value.theme == mode, { viewModel.setTheme(mode) }, { Text(when (mode) { ThemeMode.SYSTEM -> "Hệ thống"; ThemeMode.LIGHT -> "Sáng"; ThemeMode.DARK -> "Tối" }) }) } }
                Text("Hiển thị số tiền", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AmountVisibility.entries.forEach { mode -> FilterChip(value.amountVisibility == mode, { viewModel.setAmounts(mode) }, { Text(when (mode) { AmountVisibility.SHOWN -> "Hiện"; AmountVisibility.HIDDEN -> "Ẩn"; AmountVisibility.OBSCURED -> "Che" }) }) } }
                OutlinedButton(onClick = { viewModel.setCurrency("VND") }, shape = RoundedCornerShape(16.dp)) { Text("Tiền tệ: ${value.currency.value}") }
            }
            BillLocketSectionCard {
                Text("Quyền ứng dụng", style = MaterialTheme.typography.titleMedium)
                PermissionRow("Máy ảnh", Manifest.permission.CAMERA)
                PermissionRow("Vị trí", Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) PermissionRow("Thông báo", Manifest.permission.POST_NOTIFICATIONS)
                Text("Ảnh dùng Photo Picker nên BillLocket không cần đọc toàn bộ thư viện.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, shape = RoundedCornerShape(16.dp)) { Text("Mở cài đặt quyền hệ thống") }
            }
            BillLocketGroupCard {
                BillLocketGroupItem(
                    title = "Widget",
                    subtitle = "Ảnh, quyền riêng tư và thao tác khi chạm",
                    icon = Icons.Filled.Widgets,
                    onClick = onWidget,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = "Lưu trữ và sao lưu",
                    subtitle = "Dung lượng, xuất và khôi phục dữ liệu",
                    icon = Icons.Filled.Storage,
                    onClick = onStorage,
                )
            }
        }
    }
}

@Composable private fun PermissionRow(label: String, permission: String) {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ListItem({ Text(label) }, supportingContent = { Text(if (granted) "Đã cấp" else "Chỉ được hỏi khi bạn dùng tính năng") })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StorageBackupScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.storage.collectAsState()
    val context = LocalContext.current
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var exportOptions by remember { mutableStateOf<BackupOptions?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val options = exportOptions; if (uri != null && options != null) context.contentResolver.openOutputStream(uri)?.let { viewModel.export(options, it) }
        exportOptions = null
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri; if (uri != null) context.contentResolver.openInputStream(uri)?.let(viewModel::inspect)
    }
    state.pendingImport?.let { summary -> AlertDialog(
        onDismissRequest = viewModel::dismissImport,
        title = { Text("Thay thế toàn bộ dữ liệu?") },
        text = { Text("Bản sao có ${summary.transactionCount} giao dịch và ${summary.imageCount} ảnh. Dữ liệu BillLocket hiện tại sẽ bị thay thế.") },
        dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text("Hủy") } },
        confirmButton = { Button(onClick = { importUri?.let { context.contentResolver.openInputStream(it)?.let(viewModel::importReplace) } }) { Text("Khôi phục") } },
    ) }
    if (exportOptions != null) AlertDialog(
        onDismissRequest = { exportOptions = null }, title = { Text("Dữ liệu nhạy cảm") },
        text = { Text("Tệp xuất có thể chứa ảnh hóa đơn, số tiền và vị trí. Chỉ lưu ở nơi bạn tin cậy.") },
        dismissButton = { TextButton(onClick = { exportOptions = null }) { Text("Hủy") } },
        confirmButton = { Button(onClick = { val name = "BillLocket-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.blk.zip"; create.launch(name) }) { Text("Chọn nơi lưu") } },
    )
    Scaffold(topBar = { TopAppBar(title = { Text("Lưu trữ và sao lưu") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.usage?.let { usage -> Text("Ảnh gốc: ${formatBytes(usage.authoritativeBytes)}\nTạm thời / khôi phục: ${formatBytes(usage.temporaryBytes)}\nBộ nhớ đệm: ${formatBytes(usage.cacheBytes)}") }
            Button(onClick = viewModel::cleanup, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Dọn tệp mồ côi an toàn") }
            HorizontalDivider()
            Button(onClick = { exportOptions = BackupOptions(true, true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xuất đầy đủ") }
            OutlinedButton(onClick = { exportOptions = BackupOptions(false, false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xuất riêng tư (không ảnh/vị trí)") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Khôi phục từ tệp") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it) }
        }
    }
}

private fun formatBytes(value: Long): String = when { value >= 1024 * 1024 -> "%.1f MB".format(value / 1024.0 / 1024.0); value >= 1024 -> "%.1f KB".format(value / 1024.0); else -> "$value B" }
