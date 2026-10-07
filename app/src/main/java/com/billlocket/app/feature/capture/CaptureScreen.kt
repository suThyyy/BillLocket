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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LegacyCaptureScreen(
    onNavigateEditor: (com.billlocket.app.domain.model.DraftId) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val controller = remember { LifecycleCameraController(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? Activity
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onAction(CaptureAction.PermissionChanged(granted, permanentlyDenied))
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        viewModel.onAction(CaptureAction.ImagesPicked(uris.map { it.toString() }))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(
                    CaptureAction.PermissionChanged(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                    ),
                )
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

    DisposableEffect(controller, lifecycleOwner, state.permission) {
        if (state.permission == CameraPermissionState.GRANTED) {
            runCatching { controller.bindToLifecycle(lifecycleOwner) }
        }
        onDispose { runCatching { controller.unbind() } }
    }
    LaunchedEffect(state.lens) {
        runCatching {
            controller.cameraSelector = if (state.lens == CameraLens.BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    LaunchedEffect(state.flashEnabled) { runCatching { controller.enableTorch(state.flashEnabled) } }
    LaunchedEffect(state.zoomLinear) { runCatching { controller.setLinearZoom(state.zoomLinear) } }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CaptureEffect.LaunchCameraPermission -> permissionLauncher.launch(Manifest.permission.CAMERA)
            CaptureEffect.OpenAppSettings -> context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
            CaptureEffect.LaunchPhotoPicker -> pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            CaptureEffect.LaunchLocationPermission -> viewModel.onAction(CaptureAction.LocationPermissionChanged(false))
            is CaptureEffect.CapturePhoto -> {
                val output = ImageCapture.OutputFileOptions.Builder(effect.output).build()
                runCatching {
                    controller.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                viewModel.onAction(CaptureAction.PhotoCaptured(effect.staged))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.onAction(CaptureAction.CaptureFailed(effect.staged))
                            }
                        },
                    )
                }.onFailure { viewModel.onAction(CaptureAction.CaptureFailed(effect.staged)) }
            }
            is CaptureEffect.NavigateEditor -> onNavigateEditor(effect.draftId)
            CaptureEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler { viewModel.onAction(CaptureAction.Back) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ghi lại giao dịch") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(CaptureAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.permission == CameraPermissionState.GRANTED && state.cameraAvailable) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { PreviewView(it).apply { this.controller = controller } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.flashAvailable) {
                            IconButton(onClick = { viewModel.onAction(CaptureAction.ToggleFlash) }) {
                                Icon(
                                    if (state.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                    contentDescription = "Bật hoặc tắt đèn flash",
                                )
                            }
                        }
                        if (state.frontCameraAvailable) {
                            IconButton(onClick = { viewModel.onAction(CaptureAction.ToggleLens) }) {
                                Icon(Icons.Filled.Cameraswitch, contentDescription = "Đổi camera")
                            }
                        }
                    }
                }
                Slider(
                    value = state.zoomLinear,
                    onValueChange = { viewModel.onAction(CaptureAction.ZoomChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.NoPhotography, contentDescription = null, modifier = Modifier.size(64.dp))
                    Text("Bạn có thể cấp quyền camera, chọn ảnh hoặc tiếp tục không có ảnh.")
                    Button(onClick = { viewModel.onAction(CaptureAction.RequestCameraPermission) }) {
                        Text(if (state.permission == CameraPermissionState.DENIED_PERMANENTLY) "Mở cài đặt" else "Cấp quyền camera")
                    }
                }
            }

            if (state.images.isNotEmpty()) {
                DraftImageStrip(
                    images = state.images,
                    onRemove = { viewModel.onAction(CaptureAction.RemoveImage(it)) },
                    onMove = { from, to -> viewModel.onAction(CaptureAction.MoveImage(from, to)) },
                )
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { viewModel.onAction(CaptureAction.PickImages) }, enabled = !state.isProcessing) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(" Chọn ảnh")
                }
                if (state.permission == CameraPermissionState.GRANTED && state.cameraAvailable) {
                    FilledIconButton(
                        onClick = { viewModel.onAction(CaptureAction.TakePhoto) },
                        enabled = !state.isProcessing,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Chụp ảnh")
                    }
                }
                Button(onClick = { viewModel.onAction(CaptureAction.Continue) }, enabled = !state.isProcessing) {
                    Text(if (state.images.isEmpty()) "Không dùng ảnh" else "Tiếp tục")
                }
            }
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
}

@Composable
private fun DraftImageStrip(
    images: List<DraftImage>,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            Card(modifier = Modifier.width(116.dp)) {
                AsyncImage(
                    model = image.previewUri,
                    contentDescription = "Ảnh giao dịch ${index + 1}",
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Đưa ảnh sang trái")
                    }
                    IconButton(onClick = { onRemove(image.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Xóa ảnh")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < images.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Đưa ảnh sang phải")
                    }
                }
                if (image.locationCandidate != null) {
                    Text("Có gợi ý vị trí", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
