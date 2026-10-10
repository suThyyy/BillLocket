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
import androidx.compose.ui.res.stringResource
import com.billlocket.app.R
import com.billlocket.app.locale.AppLanguage
import com.billlocket.app.locale.AppLocaleController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface StorageMessage {
    data object CurrencyChangeBlocked : StorageMessage
    data object MeasureFailed : StorageMessage
    data class CleanupSucceeded(val removedCount: Int) : StorageMessage
    data object CleanupFailed : StorageMessage
    data object ExportSucceeded : StorageMessage
    data object ExportFailed : StorageMessage
    data object InvalidBackup : StorageMessage
    data object RestoreSucceeded : StorageMessage
    data object RestoreFailedDataPreserved : StorageMessage
}

data class StorageState(val usage: MediaStorageUsage? = null, val busy: Boolean = false, val message: StorageMessage? = null, val pendingImport: BackupSummary? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val media: MediaStorePort,
    private val backup: BackupPort,
    private val localeController: AppLocaleController,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    val language: StateFlow<AppLanguage> = localeController.current
    private val _storage = MutableStateFlow(StorageState())
    val storage = _storage.asStateFlow()

    init {
        refreshStorage()
        localeController.syncFromSystem()
    }
    fun setLanguage(value: AppLanguage) = localeController.apply(value)
    fun setTheme(value: ThemeMode) = viewModelScope.launch { settingsRepository.updateTheme(value) }
    fun setAmounts(value: AmountVisibility) = viewModelScope.launch { settingsRepository.updateAmountVisibility(value) }
    fun setCurrency(value: String) = viewModelScope.launch { val result = settingsRepository.updateCurrency(value); if (result is Outcome.Failure) _storage.update { it.copy(message = StorageMessage.CurrencyChangeBlocked) } }
    fun refreshStorage() = viewModelScope.launch { when (val result = media.measureStorage()) { is Outcome.Success -> _storage.update { it.copy(usage = result.value) }; is Outcome.Failure -> _storage.update { it.copy(message = StorageMessage.MeasureFailed) } } }
    fun cleanup() = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        when (val result = media.reconcile(System.currentTimeMillis())) {
            is Outcome.Success -> _storage.update { it.copy(busy = false, message = StorageMessage.CleanupSucceeded(result.value.removedOrphanFiles + result.value.removedStagingFiles + result.value.removedTrashOrCacheFiles)) }
            is Outcome.Failure -> _storage.update { it.copy(busy = false, message = StorageMessage.CleanupFailed) }
        }
        refreshStorage()
    }
    fun export(options: BackupOptions, output: OutputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        val result = backup.export(options, output)
        _storage.update { it.copy(busy = false, message = if (result is Outcome.Success) StorageMessage.ExportSucceeded else StorageMessage.ExportFailed) }
    }
    fun inspect(input: InputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, message = null) }
        when (val result = backup.inspect(input)) {
            is Outcome.Success -> _storage.update { it.copy(busy = false, pendingImport = result.value) }
            is Outcome.Failure -> _storage.update { it.copy(busy = false, message = StorageMessage.InvalidBackup) }
        }
    }
    fun importReplace(input: InputStream) = viewModelScope.launch {
        _storage.update { it.copy(busy = true, pendingImport = null, message = null) }
        val result = backup.importReplace(input)
        _storage.update { it.copy(busy = false, message = if (result is Outcome.Success) StorageMessage.RestoreSucceeded else StorageMessage.RestoreFailedDataPreserved) }
        refreshStorage()
    }
    fun dismissImport() = _storage.update { it.copy(pendingImport = null) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GeneralSettingsScreen(onBack: () -> Unit, onWidget: () -> Unit, onStorage: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val value by viewModel.settings.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column { Text(stringResource(R.string.settings_personalization), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.settings_personalization_sub), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            BillLocketSectionCard {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = currentLanguage == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = {
                                Text(
                                    when (lang) {
                                        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                                        AppLanguage.VIETNAMESE -> stringResource(R.string.settings_language_vietnamese)
                                        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                                    }
                                )
                            }
                        )
                    }
                }
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ThemeMode.entries.forEach { mode -> FilterChip(value.theme == mode, { viewModel.setTheme(mode) }, { Text(when (mode) { ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system); ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light); ThemeMode.DARK -> stringResource(R.string.settings_theme_dark) }) }) } }
                Text(stringResource(R.string.settings_amount_visibility), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AmountVisibility.entries.forEach { mode -> FilterChip(value.amountVisibility == mode, { viewModel.setAmounts(mode) }, { Text(when (mode) { AmountVisibility.SHOWN -> stringResource(R.string.settings_amount_show); AmountVisibility.HIDDEN -> stringResource(R.string.settings_amount_hide); AmountVisibility.OBSCURED -> stringResource(R.string.settings_amount_obscure) }) }) } }
                OutlinedButton(onClick = { viewModel.setCurrency("VND") }, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.settings_currency_format, value.currency.value)) }
            }
            BillLocketSectionCard {
                Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleMedium)
                PermissionRow(stringResource(R.string.settings_permission_camera), Manifest.permission.CAMERA)
                PermissionRow(stringResource(R.string.settings_permission_location), Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) PermissionRow(stringResource(R.string.settings_permission_notifications), Manifest.permission.POST_NOTIFICATIONS)
                Text(stringResource(R.string.settings_photo_picker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.settings_open_system_settings)) }
            }
            BillLocketGroupCard {
                BillLocketGroupItem(
                    title = stringResource(R.string.settings_widget),
                    subtitle = stringResource(R.string.settings_widget_sub),
                    icon = Icons.Filled.Widgets,
                    onClick = onWidget,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                BillLocketGroupItem(
                    title = stringResource(R.string.settings_storage_backup),
                    subtitle = stringResource(R.string.settings_storage_backup_sub),
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
    ListItem({ Text(label) }, supportingContent = { Text(if (granted) stringResource(R.string.settings_permission_granted) else stringResource(R.string.settings_permission_on_demand)) })
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
        title = { Text(stringResource(R.string.settings_storage_replace_dialog_title)) },
        text = { Text(stringResource(R.string.settings_storage_replace_dialog_message, summary.transactionCount, summary.imageCount)) },
        dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = { Button(onClick = { importUri?.let { context.contentResolver.openInputStream(it)?.let(viewModel::importReplace) } }) { Text(stringResource(R.string.settings_storage_restore_confirm)) } },
    ) }
    if (exportOptions != null) AlertDialog(
        onDismissRequest = { exportOptions = null }, title = { Text(stringResource(R.string.settings_storage_sensitive_dialog_title)) },
        text = { Text(stringResource(R.string.settings_storage_sensitive_dialog_message)) },
        dismissButton = { TextButton(onClick = { exportOptions = null }) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = { Button(onClick = { val name = "BillLocket-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.blk.zip"; create.launch(name) }) { Text(stringResource(R.string.settings_storage_choose_location)) } },
    )
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_storage_backup)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.usage?.let { usage -> Text(stringResource(R.string.settings_storage_usage_format, formatBytes(usage.authoritativeBytes), formatBytes(usage.temporaryBytes), formatBytes(usage.cacheBytes))) }
            Button(onClick = viewModel::cleanup, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_storage_cleanup_button)) }
            HorizontalDivider()
            Button(onClick = { exportOptions = BackupOptions(true, true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_storage_export_full)) }
            OutlinedButton(onClick = { exportOptions = BackupOptions(false, false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_storage_export_private)) }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_storage_restore_file)) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { msg ->
                val msgText = when (msg) {
                    StorageMessage.CurrencyChangeBlocked -> stringResource(R.string.settings_storage_msg_currency_blocked)
                    StorageMessage.MeasureFailed -> stringResource(R.string.settings_storage_msg_measure_failed)
                    is StorageMessage.CleanupSucceeded -> stringResource(R.string.settings_storage_msg_cleanup_succeeded, msg.removedCount)
                    StorageMessage.CleanupFailed -> stringResource(R.string.settings_storage_msg_cleanup_failed)
                    StorageMessage.ExportSucceeded -> stringResource(R.string.settings_storage_msg_export_succeeded)
                    StorageMessage.ExportFailed -> stringResource(R.string.settings_storage_msg_export_failed)
                    StorageMessage.InvalidBackup -> stringResource(R.string.settings_storage_msg_invalid_backup)
                    StorageMessage.RestoreSucceeded -> stringResource(R.string.settings_storage_msg_restore_succeeded)
                    StorageMessage.RestoreFailedDataPreserved -> stringResource(R.string.settings_storage_msg_restore_failed)
                }
                Text(msgText)
            }
        }
    }
}

private fun formatBytes(value: Long): String = when { value >= 1024 * 1024 -> "%.1f MB".format(value / 1024.0 / 1024.0); value >= 1024 -> "%.1f KB".format(value / 1024.0); else -> "$value B" }
