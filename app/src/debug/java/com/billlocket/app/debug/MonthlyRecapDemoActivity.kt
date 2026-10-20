package com.billlocket.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MonthlyRecapDemoActivity : ComponentActivity() {

    private val seeder by lazy { MonthlyRecapDemoSeeder(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFB300),
                    onPrimary = Color.Black,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    error = Color(0xFFEF5350),
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Demo Seeder Tháng 9-10/2026") },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color(0xFF1A1A1A),
                                    titleContentColor = Color(0xFFFFB300),
                                ),
                            )
                        },
                    ) { innerPadding ->
                        SeederContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            seeder = seeder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeederContent(
    modifier: Modifier = Modifier,
    seeder: MonthlyRecapDemoSeeder,
) {
    val coroutineScope = rememberCoroutineScope()
    var discoveredImageCount by remember { mutableIntStateOf(0) }
    var seedPath by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var successResult by remember { mutableStateOf<MonthlyRecapDemoSeeder.SeederResult?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshImageStatus() {
        seedPath = seeder.getSeedDirectory().absolutePath
        val images = seeder.discoverInputImages()
        discoveredImageCount = images.size
    }

    LaunchedEffect(Unit) {
        refreshImageStatus()
    }

    val currencyFormatter = remember {
        NumberFormat.getNumberInstance(Locale("vi", "VN"))
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Discovered Images Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thư mục ảnh đầu vào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                    }
                    IconButton(
                        onClick = { refreshImageStatus() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Làm mới",
                            tint = Color(0xFFFFB300),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = seedPath,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Số ảnh tìm thấy: ",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                    )
                    Text(
                        text = "$discoveredImageCount ảnh",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (discoveredImageCount > 0) Color(0xFF4CAF50) else Color(0xFFEF5350),
                    )
                }

                if (discoveredImageCount == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF332020), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "Chưa có ảnh nào trong thư mục. Vui lòng chạy script seed-monthly-recap.ps1 để đẩy ảnh.",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                        )
                    }
                } else if (discoveredImageCount < MonthlyRecapSeedPlanner.MIN_RECOMMENDED_IMAGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF332E1A), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ít hơn 6 ảnh: Bộ sưu tập recap và bản đồ sẽ bị giới hạn.",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        infoMessage = null
                        successResult = null
                        try {
                            val result = seeder.seed { msg, frac ->
                                progressMessage = msg
                                progressFraction = frac
                            }
                            successResult = result
                            refreshImageStatus()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Đã xảy ra lỗi không xác định."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading && discoveredImageCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF423B2A),
                    disabledContentColor = Color.Gray,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thay thế bằng 98 giao dịch demo tháng 9-10/2026",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        infoMessage = null
                        successResult = null
                        try {
                            val count = seeder.cleanupPreviousSeededData { msg ->
                                progressMessage = msg
                            }
                            infoMessage = "Đã xóa thành công $count giao dịch demo tháng 9-10/2026."
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Lỗi dọn dẹp dữ liệu."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xóa dữ liệu demo tháng 9-10/2026", color = Color(0xFFEF5350))
            }
        }

        // Progress Section
        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color(0xFFFFB300),
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = progressMessage,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                    )
                    if (progressFraction > 0f) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFB300),
                            trackColor = Color(0xFF333333),
                        )
                    }
                }
            }
        }

        // Error Banner
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF331C1C)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = error,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Info Banner
        infoMessage?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E1E)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = info,
                        color = Color(0xFFA5D6A7),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Success Result Card
        successResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D20)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khởi tạo dữ liệu thành công!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF81C784),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF2E4D33))
                    Spacer(modifier = Modifier.height(12.dp))

                    MetricRow("Số giao dịch đã tạo", "${result.transactionCount}")
                    MetricRow("Số ảnh giao dịch đã gắn", "${result.imageCount}")
                    MetricRow("Số vị trí giả lập đã xác nhận", "${result.locationCount} (20 địa chỉ Hà Nội)")
                    MetricRow("Tổng thu nhập", "${currencyFormatter.format(result.totalIncomeMinor)} ₫")
                    MetricRow("Tổng chi tiêu", "${currencyFormatter.format(result.totalExpenseMinor)} ₫")
                    MetricRow("Danh mục chi tiêu hàng đầu", result.topCategoryName)
                    MetricRow("Ngày hoạt động nhiều nhất", result.mostActiveDay)

                    if (result.isWarningFewImages) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lưu ý: Dữ liệu được tạo với ít hơn 6 ảnh đầu vào.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD54F),
                        )
                    }
                }
            }
        }

        // Notes and Safety Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Thông tin an toàn & Idempotency:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Ghi chú giao dịch để trống; ID dữ liệu demo được lưu riêng để có thể dọn dẹp an toàn.\n" +
                        "• Chạy lại sẽ tạo backup đã kiểm tra trước khi thay thế dữ liệu hiện tại.\n" +
                        "• Ảnh được chuẩn hóa JPEG và loại bỏ EXIF tự động, tọa độ là dữ liệu giả lập.",
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFFB0B0B0))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
