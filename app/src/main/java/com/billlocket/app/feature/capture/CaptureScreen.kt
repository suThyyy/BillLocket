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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.billlocket.app.R
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
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(CaptureAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                                    contentDescription = stringResource(R.string.capture_flash_toggle_desc),
                                )
                            }
                        }
                        if (state.frontCameraAvailable) {
                            IconButton(onClick = { viewModel.onAction(CaptureAction.ToggleLens) }) {
                                Icon(Icons.Filled.Cameraswitch, contentDescription = stringResource(R.string.capture_switch_camera_desc))
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
                    Text(stringResource(R.string.capture_permission_fallback_msg))
                    Button(onClick = { viewModel.onAction(CaptureAction.RequestCameraPermission) }) {
                        Text(if (state.permission == CameraPermissionState.DENIED_PERMANENTLY) stringResource(R.string.capture_open_settings) else stringResource(R.string.capture_grant_permission))
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
            state.errorMessage?.let { Text(localizeCaptureError(it), color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { viewModel.onAction(CaptureAction.PickImages) },
                    enabled = !state.isProcessing && state.images.size < MAX_CAPTURE_IMAGES,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(" " + stringResource(R.string.capture_pick_photos))
                }
                if (state.permission == CameraPermissionState.GRANTED && state.cameraAvailable) {
                    FilledIconButton(
                        onClick = { viewModel.onAction(CaptureAction.TakePhoto) },
                        enabled = !state.isProcessing && state.images.size < MAX_CAPTURE_IMAGES,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.capture_take_photo))
                    }
                }
                Button(onClick = { viewModel.onAction(CaptureAction.Continue) }, enabled = !state.isProcessing) {
                    Text(if (state.images.isEmpty()) stringResource(R.string.capture_no_photos) else stringResource(R.string.capture_continue))
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(CaptureAction.KeepDraft) },
            title = { Text(stringResource(R.string.capture_discard_title)) },
            text = { Text(stringResource(R.string.capture_discard_message)) },
            confirmButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.DiscardDraft) }) { Text(stringResource(R.string.capture_discard_confirm)) } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.KeepDraft) }) { Text(stringResource(R.string.capture_continue_editing)) } },
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
                    contentDescription = stringResource(R.string.capture_photo_desc, index + 1),
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.capture_move_left))
                    }
                    IconButton(onClick = { onRemove(image.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.capture_delete_photo))
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < images.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.capture_move_right))
                    }
                }
                if (image.locationCandidate != null) {
                    Text(stringResource(R.string.capture_has_location_hint), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun localizeCaptureError(msg: String): String = when (msg) {
    "Không có quyền vị trí; ảnh vẫn được lưu bình thường" -> stringResource(R.string.capture_error_permission_denied)
    "Không thể chụp ảnh" -> stringResource(R.string.capture_error_take_photo)
    "Không thể chuẩn bị ảnh" -> stringResource(R.string.capture_error_prepare_photo)
    "Ảnh không hợp lệ hoặc quá lớn" -> stringResource(R.string.capture_error_invalid_or_large)
    "Không lấy được vị trí chính xác; ảnh vẫn được lưu bình thường" -> stringResource(R.string.capture_error_location_inaccurate)
    else -> msg
}
