package com.billlocket.app.feature.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.WidgetOpenAction
import com.billlocket.app.domain.model.WidgetSettings
import com.billlocket.app.domain.port.WidgetUpdatePort
import com.billlocket.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WidgetSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val updater: WidgetUpdatePort,
) : ViewModel() {
    val settings = repository.settings.map { it.widget }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetSettings())

    fun update(value: WidgetSettings) = viewModelScope.launch {
        repository.updateWidgetSettings(value)
        updater.requestRefresh()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: WidgetSettingsViewModel = hiltViewModel()) {
    val value by viewModel.settings.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Widget") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quyền riêng tư", style = MaterialTheme.typography.titleMedium)
            SettingSwitch("Hiển thị số tiền", "Tắt để ẩn số tiền trên màn hình chính", value.showAmount) { viewModel.update(value.copy(showAmount = it)) }
            SettingSwitch("Hiển thị danh mục", "Có thể ẩn hoàn toàn thông tin phân loại", value.showCategory) { viewModel.update(value.copy(showCategory = it)) }
            HorizontalDivider()
            Text("Khi chạm vào ảnh", style = MaterialTheme.typography.titleMedium)
            WidgetOpenAction.entries.forEach { action ->
                Row(Modifier.fillMaxWidth().clickable { viewModel.update(value.copy(openAction = action)) }.padding(vertical = 10.dp)) {
                    RadioButton(action == value.openAction, { viewModel.update(value.copy(openAction = action)) })
                    Text(if (action == WidgetOpenAction.TRANSACTION_DETAIL) "Mở chi tiết giao dịch" else "Chụp nhanh hóa đơn", Modifier.padding(top = 12.dp))
                }
            }
            Text("Nút ＋ trên widget luôn mở chụp nhanh. Nếu ảnh đã bị xóa hoặc không đọc được, widget tự chuyển sang ảnh hợp lệ khác.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SettingSwitch(title: String, supporting: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(title); Text(supporting, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, onChanged)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsHubScreen(onBack: () -> Unit, onWidget: () -> Unit, onStorage: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Cài đặt") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem({ Text("Widget") }, supportingContent = { Text("Ảnh, quyền riêng tư và thao tác khi chạm") }, modifier = Modifier.clickable(onClick = onWidget))
            ListItem({ Text("Lưu trữ và sao lưu") }, supportingContent = { Text("Dung lượng, xuất và khôi phục dữ liệu") }, modifier = Modifier.clickable(onClick = onStorage))
        }
    }
}
