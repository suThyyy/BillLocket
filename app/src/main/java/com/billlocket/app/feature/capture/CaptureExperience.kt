package com.billlocket.app.feature.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState

@Composable
fun CaptureScreen(
    onNavigateEditor: (DraftId) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }
    var isShutterFlashing by remember { mutableStateOf(false) }

    val isInitiallyGranted = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(CaptureAction.PermissionChanged(isInitiallyGranted))
        viewModel.onAction(
            CaptureAction.CapabilitiesChanged(
                cameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                frontCameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
                flashAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            ),
        )
    }

    LaunchedEffect(state.autoLocationEnabled) {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (state.autoLocationEnabled && hasLocationPermission) {
            viewModel.onAction(CaptureAction.WarmAutomaticLocation)
        }
    }

    LaunchedEffect(isShutterFlashing) {
        if (isShutterFlashing) {
            kotlinx.coroutines.delay(120)
            isShutterFlashing = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? Activity
        val permanentlyDenied = !granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onAction(CaptureAction.PermissionChanged(granted, permanentlyDenied))
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onAction(CaptureAction.LocationPermissionChanged(result.values.any { it }))
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        viewModel.onAction(CaptureAction.ImagesPicked(uris.map { it.toString() }))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(CaptureAction.PermissionChanged(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
                viewModel.onAction(
                    CaptureAction.CapabilitiesChanged(
                        cameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                        frontCameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
                        flashAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(controller, lifecycleOwner, state.permission, isInitiallyGranted) {
        val hasPermission = state.permission == CameraPermissionState.GRANTED || (state.permission == CameraPermissionState.UNKNOWN && isInitiallyGranted)
        if (hasPermission) runCatching { controller.bindToLifecycle(lifecycleOwner) }
        onDispose { runCatching { controller.unbind() } }
    }
    LaunchedEffect(state.lens) {
        runCatching { controller.cameraSelector = if (state.lens == CameraLens.BACK) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA }
    }
    LaunchedEffect(state.flashEnabled) { runCatching { controller.enableTorch(state.flashEnabled) } }
    LaunchedEffect(state.zoomLinear) { runCatching { controller.setLinearZoom(state.zoomLinear) } }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CaptureEffect.LaunchCameraPermission -> permissionLauncher.launch(Manifest.permission.CAMERA)
            CaptureEffect.OpenAppSettings -> context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) })
            CaptureEffect.LaunchPhotoPicker -> pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            CaptureEffect.LaunchLocationPermission -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
                else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            is CaptureEffect.CapturePhoto -> {
                val output = ImageCapture.OutputFileOptions.Builder(effect.output).build()
                runCatching {
                    controller.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = viewModel.onAction(CaptureAction.PhotoCaptured(effect.staged))
                            override fun onError(exception: ImageCaptureException) = viewModel.onAction(CaptureAction.CaptureFailed(effect.staged))
                        },
                    )
                }.onFailure { viewModel.onAction(CaptureAction.CaptureFailed(effect.staged)) }
            }
            is CaptureEffect.NavigateEditor -> onNavigateEditor(effect.draftId)
            CaptureEffect.NavigateBack -> onNavigateBack()
        }
    }
    BackHandler { viewModel.onAction(CaptureAction.Back) }

    Scaffold(containerColor = Color(0xFF080807)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CaptureTopBar(
                imageCount = state.images.size,
                autoLocationEnabled = state.autoLocationEnabled,
                onBack = { viewModel.onAction(CaptureAction.Back) },
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CameraStage(
                    state = state,
                    controller = controller,
                    isInitiallyGranted = isInitiallyGranted,
                    isShutterFlashing = isShutterFlashing,
                    onAction = viewModel::onAction,
                )
                if (state.images.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        LocketDraftStrip(
                            images = state.images,
                            onRemove = { viewModel.onAction(CaptureAction.RemoveImage(it)) },
                            onMove = { from, to -> viewModel.onAction(CaptureAction.MoveImage(from, to)) },
                        )
                    }
                }
                state.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            CaptureBottomControls(
                state = state,
                onPick = { viewModel.onAction(CaptureAction.PickImages) },
                onCapture = {
                    isShutterFlashing = true
                    viewModel.onAction(CaptureAction.TakePhoto)
                },
                onContinue = { viewModel.onAction(CaptureAction.Continue) },
            )
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(CaptureAction.KeepDraft) },
            title = { Text("Bỏ bản nháp?") },
            text = { Text("Các ảnh chưa lưu sẽ bị xóa.") },
            confirmButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.DiscardDraft) }) { Text("Bỏ") } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.KeepDraft) }) { Text("Tiếp tục chỉnh sửa") } },
        )
    }

    if (state.showLocationConsent) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(CaptureAction.DisableAutomaticLocation) },
            icon = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Tự động lưu vị trí ảnh?") },
            text = { Text("BillLocket chỉ gắn vị trí hiện tại vào ảnh bạn chụp sau khi bạn đồng ý. Ảnh nhập từ thư viện không dùng vị trí hiện tại và bạn luôn có thể sửa hoặc xóa vị trí sau.") },
            confirmButton = { Button(onClick = { viewModel.onAction(CaptureAction.EnableAutomaticLocation) }) { Text("Bật vị trí") } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.DisableAutomaticLocation) }) { Text("Không, vẫn chụp") } },
        )
    }
}

@Composable
private fun CaptureTopBar(imageCount: Int, autoLocationEnabled: Boolean, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
        ) {
            Text(
                "Hủy",
                Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (autoLocationEnabled) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp))
                        Text("Vị trí bật", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (imageCount > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "$imageCount/10",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraStage(
    state: CaptureState,
    controller: LifecycleCameraController,
    isInitiallyGranted: Boolean,
    isShutterFlashing: Boolean,
    onAction: (CaptureAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .aspectRatio(1f)
            .clip(shape)
            .background(Color(0xFF141312)),
    ) {
        val hasPermission = state.permission == CameraPermissionState.GRANTED || (state.permission == CameraPermissionState.UNKNOWN && isInitiallyGranted)
        if (hasPermission && state.cameraAvailable) {
            AndroidView(
                factory = { PreviewView(it).apply { this.controller = controller } },
                modifier = Modifier.fillMaxSize(),
            )
            if (isShutterFlashing) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)))
            }
            if (state.flashAvailable) {
                Box(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    CameraAction(
                        icon = if (state.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        description = "Đèn flash",
                        active = state.flashEnabled,
                    ) { onAction(CaptureAction.ToggleFlash) }
                }
            }
            if (state.frontCameraAvailable) {
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    CameraAction(
                        icon = Icons.Filled.Cameraswitch,
                        description = "Đổi camera",
                    ) { onAction(CaptureAction.ToggleLens) }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZoomPill("1×", state.zoomLinear < 0.25f) { onAction(CaptureAction.ZoomChanged(0f)) }
                ZoomPill("2×", state.zoomLinear >= 0.25f) { onAction(CaptureAction.ZoomChanged(0.5f)) }
            }
        } else if (state.permission == CameraPermissionState.UNKNOWN) {
            // Transient state while checking permission; remain on dark background without flashing UI
            Box(Modifier.fillMaxSize().background(Color(0xFF141312)))
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Bật camera để chụp hóa đơn", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("Bạn vẫn có thể chọn ảnh từ thư viện hoặc tiếp tục.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAction(CaptureAction.RequestCameraPermission) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (state.permission == CameraPermissionState.DENIED_PERMANENTLY) "Mở cài đặt" else "Cấp quyền camera", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ZoomPill(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (active) Color(0xFF1E1400) else Color.White,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CameraAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.58f),
        contentColor = if (active) Color(0xFF1E1400) else Color.White,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp)) }
    }
}

@Composable
private fun CaptureBottomControls(
    state: CaptureState,
    onPick: () -> Unit,
    onCapture: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val canCapture = state.permission == CameraPermissionState.GRANTED && state.cameraAvailable
        Box(
            Modifier
                .size(84.dp)
                .border(
                    width = 4.dp,
                    color = if (canCapture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = CircleShape,
                )
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (canCapture) Color.White else Color.White.copy(alpha = 0.3f))
                .clickable(enabled = canCapture && !state.isProcessing, onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isProcessing) {
                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            onClick = onPick,
            enabled = !state.isProcessing,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.height(44.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp), tint = Color.White)
                Text("Chọn ảnh", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        if (state.images.isEmpty()) {
            TextButton(
                onClick = onContinue,
                enabled = !state.isProcessing,
            ) {
                Text(
                    "Bỏ qua ảnh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        } else {
            Button(
                onClick = onContinue,
                enabled = !state.isProcessing,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Tiếp tục (${state.images.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1400),
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = Color(0xFF1E1400))
                }
            }
        }
    }
}

@Composable
private fun LocketDraftStrip(images: List<DraftImage>, onRemove: (String) -> Unit, onMove: (Int, Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(19.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(19.dp))) {
                AsyncImage(image.previewUri, "Ảnh giao dịch ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(onClick = { onRemove(image.id) }, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.7f), contentColor = Color.White) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Xóa ảnh", Modifier.size(14.dp)) }
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.58f)), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Sang trái", Modifier.size(16.dp), tint = Color.White) }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < images.lastIndex, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sang phải", Modifier.size(16.dp), tint = Color.White) }
                }
            }
        }
    }
}
