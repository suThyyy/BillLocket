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
import androidx.compose.ui.res.stringResource
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
import com.billlocket.app.R
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.ThemeMode
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.feature.transaction.draft.DraftImageKind
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import com.billlocket.app.ui.theme.AppTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

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
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var previousImageIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(state.images) {
        selectedImageId = resolveSelectedImageId(
            previousImageIds = previousImageIds,
            currentImages = state.images,
            currentSelectedId = selectedImageId,
        )
        previousImageIds = state.images.map { it.id }.toSet()
    }

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
                            locationStatusByImageId = state.locationStatusByImageId,
                            selectedImageId = selectedImageId,
                            onSelect = { selectedImageId = it },
                            onRemove = { viewModel.onAction(CaptureAction.RemoveImage(it)) },
                            onMove = { from, to -> viewModel.onAction(CaptureAction.MoveImage(from, to)) },
                        )
                    }
                    val activeImage = state.images.find { it.id == selectedImageId } ?: state.images.lastOrNull()
                    val activeStatus = activeImage?.let {
                        state.locationStatusByImageId[it.id]
                            ?: if (it.confirmedLocation != null) CaptureLocationStatus.Attached else CaptureLocationStatus.NotRequested
                    } ?: CaptureLocationStatus.NotRequested

                    if (activeImage != null && activeStatus != CaptureLocationStatus.NotRequested) {
                        Spacer(Modifier.height(6.dp))
                        CaptureLocationStatusRow(status = activeStatus)
                    }
                }
                if (state.locationPermissionDeniedNotice) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Icon(
                            Icons.Filled.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = stringResource(R.string.capture_location_permission_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                state.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(localizeCaptureError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            title = { Text(stringResource(R.string.capture_discard_title)) },
            text = { Text(stringResource(R.string.capture_discard_message)) },
            confirmButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.DiscardDraft) }) { Text(stringResource(R.string.capture_discard_confirm)) } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.KeepDraft) }) { Text(stringResource(R.string.capture_continue_editing)) } },
        )
    }

    if (state.showLocationConsent) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(CaptureAction.DisableAutomaticLocation) },
            icon = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.capture_location_consent_title)) },
            text = { Text(stringResource(R.string.capture_location_consent_message)) },
            confirmButton = { Button(onClick = { viewModel.onAction(CaptureAction.EnableAutomaticLocation) }) { Text(stringResource(R.string.capture_location_enable)) } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(CaptureAction.DisableAutomaticLocation) }) { Text(stringResource(R.string.capture_location_skip)) } },
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
                stringResource(R.string.capture_cancel),
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
                        Text(stringResource(R.string.capture_location_on), style = MaterialTheme.typography.labelSmall)
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
                        description = stringResource(R.string.capture_flash_desc),
                        active = state.flashEnabled,
                    ) { onAction(CaptureAction.ToggleFlash) }
                }
            }
            if (state.frontCameraAvailable) {
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    CameraAction(
                        icon = Icons.Filled.Cameraswitch,
                        description = stringResource(R.string.capture_switch_camera_desc),
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
                Text(stringResource(R.string.capture_permission_prompt), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.capture_permission_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAction(CaptureAction.RequestCameraPermission) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (state.permission == CameraPermissionState.DENIED_PERMANENTLY) stringResource(R.string.capture_open_settings) else stringResource(R.string.capture_grant_permission), color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.capture_pick_photos), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        if (state.images.isEmpty()) {
            TextButton(
                onClick = onContinue,
                enabled = !state.isProcessing,
            ) {
                Text(
                    stringResource(R.string.capture_skip_photos),
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
                        stringResource(R.string.capture_continue_count, state.images.size),
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
fun LocketDraftStrip(
    images: List<DraftImage>,
    locationStatusByImageId: Map<String, CaptureLocationStatus>,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedImageId: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            val isSelected = selectedImageId == image.id
            val status = locationStatusByImageId[image.id]
                ?: if (image.confirmedLocation != null) CaptureLocationStatus.Attached else CaptureLocationStatus.NotRequested

            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .border(
                        width = if (isSelected) 3.dp else 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(19.dp),
                    )
                    .clickable(enabled = onSelect != null) { onSelect?.invoke(image.id) },
            ) {
                AsyncImage(
                    image.previewUri,
                    stringResource(R.string.capture_photo_desc, index + 1),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                LocationThumbnailBadge(
                    status = status,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp),
                )
                Surface(
                    onClick = { onRemove(image.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, stringResource(R.string.capture_delete_photo), Modifier.size(14.dp))
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.58f)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.capture_move_left),
                            Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < images.lastIndex,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.capture_move_right),
                            Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

fun resolveSelectedImageId(
    previousImageIds: Set<String>,
    currentImages: List<DraftImage>,
    currentSelectedId: String?,
): String? {
    if (currentImages.isEmpty()) return null
    val currentIds = currentImages.map { it.id }
    val newIds = currentIds.filter { it !in previousImageIds }
    return when {
        newIds.isNotEmpty() -> newIds.last()
        currentSelectedId != null && currentIds.contains(currentSelectedId) -> currentSelectedId
        else -> currentImages.last().id
    }
}

@Composable
fun LocationThumbnailBadge(
    status: CaptureLocationStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        CaptureLocationStatus.NotRequested -> Unit
        CaptureLocationStatus.Resolving -> {
            val description = stringResource(R.string.capture_location_resolving)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = modifier.semantics { contentDescription = description },
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
        CaptureLocationStatus.Attached -> {
            val description = stringResource(R.string.capture_location_attached)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF1E1400),
                modifier = modifier.semantics { contentDescription = description },
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF1E1400),
                    )
                }
            }
        }
        CaptureLocationStatus.Unavailable -> {
            val description = stringResource(R.string.capture_location_unavailable)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White.copy(alpha = 0.85f),
                modifier = modifier.semantics { contentDescription = description },
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureLocationStatusRow(
    status: CaptureLocationStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        CaptureLocationStatus.NotRequested -> Unit
        CaptureLocationStatus.Resolving -> {
            Row(
                modifier = modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    text = stringResource(R.string.capture_location_resolving),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        CaptureLocationStatus.Attached -> {
            Row(
                modifier = modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.capture_location_attached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CaptureLocationStatus.Unavailable -> {
            Row(
                modifier = modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = stringResource(R.string.capture_location_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Preview(name = "Capture Location Resolving Dark", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationResolvingDarkPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Resolving)
        }
    }
}

@Preview(name = "Capture Location Resolving Light", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationResolvingLightPreview() {
    AppTheme(mode = ThemeMode.LIGHT) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Resolving)
        }
    }
}

@Preview(name = "Capture Location Attached Dark", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationAttachedDarkPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Attached)
        }
    }
}

@Preview(name = "Capture Location Attached Light", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationAttachedLightPreview() {
    AppTheme(mode = ThemeMode.LIGHT) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Attached)
        }
    }
}

@Preview(name = "Capture Location Unavailable Dark", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationUnavailableDarkPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Unavailable)
        }
    }
}

@Preview(name = "Capture Location Unavailable Light", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationUnavailableLightPreview() {
    AppTheme(mode = ThemeMode.LIGHT) {
        Column(Modifier.padding(16.dp)) {
            CaptureLocationStatusRow(status = CaptureLocationStatus.Unavailable)
        }
    }
}

@Preview(name = "Capture Location Multi Status Dark", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun CaptureLocationMultiStatusDarkPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        val sampleImages = listOf(
            DraftImage(
                id = "img-1",
                kind = DraftImageKind.STAGED,
                previewUri = "content://media/1",
                mediaUri = "content://media/1",
                widthPx = 100,
                heightPx = 100,
                sizeBytes = 1024L,
            ),
            DraftImage(
                id = "img-2",
                kind = DraftImageKind.STAGED,
                previewUri = "content://media/2",
                mediaUri = "content://media/2",
                widthPx = 100,
                heightPx = 100,
                sizeBytes = 1024L,
            ),
        )
        val statusMap = mapOf(
            "img-1" to CaptureLocationStatus.Attached,
            "img-2" to CaptureLocationStatus.Resolving,
        )
        Column(Modifier.padding(16.dp)) {
            LocketDraftStrip(
                images = sampleImages,
                locationStatusByImageId = statusMap,
                selectedImageId = "img-2",
                onRemove = {},
                onMove = { _, _ -> },
            )
            Spacer(Modifier.height(8.dp))
            CaptureLocationStatusRow(status = CaptureLocationStatus.Resolving)
        }
    }
}

@Preview(name = "Capture Location Multi Status Light", showBackground = true)
@Composable
private fun CaptureLocationMultiStatusLightPreview() {
    AppTheme(mode = ThemeMode.LIGHT) {
        val sampleImages = listOf(
            DraftImage(
                id = "img-1",
                kind = DraftImageKind.STAGED,
                previewUri = "content://media/1",
                mediaUri = "content://media/1",
                widthPx = 100,
                heightPx = 100,
                sizeBytes = 1024L,
            ),
            DraftImage(
                id = "img-2",
                kind = DraftImageKind.STAGED,
                previewUri = "content://media/2",
                mediaUri = "content://media/2",
                widthPx = 100,
                heightPx = 100,
                sizeBytes = 1024L,
            ),
        )
        val statusMap = mapOf(
            "img-1" to CaptureLocationStatus.Attached,
            "img-2" to CaptureLocationStatus.Unavailable,
        )
        Column(Modifier.padding(16.dp)) {
            LocketDraftStrip(
                images = sampleImages,
                locationStatusByImageId = statusMap,
                selectedImageId = "img-2",
                onRemove = {},
                onMove = { _, _ -> },
            )
            Spacer(Modifier.height(8.dp))
            CaptureLocationStatusRow(status = CaptureLocationStatus.Unavailable)
        }
    }
}
