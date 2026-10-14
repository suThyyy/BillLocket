package com.billlocket.app.feature.transaction

import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.capture.CaptureAction
import com.billlocket.app.feature.capture.CaptureEffect
import com.billlocket.app.feature.capture.CaptureLocationChoice
import com.billlocket.app.feature.capture.CaptureLocationStatus
import com.billlocket.app.feature.location.DeviceLocationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelImmediateDispatcherTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `warm location completing synchronously with immediate dispatcher attaches successfully without missing result`() = runTest(testDispatcher) {
        val preference = FakeCaptureLocationPreference(CaptureLocationChoice.ENABLED)
        val store = FakeDraftStore()
        var currentCalls = 0
        val targetPoint = GeoPoint(21.0285, 105.8542)
        val synchronousLocation = object : DeviceLocationPort {
            override suspend fun current(): Outcome<GeoPoint> {
                currentCalls++
                return Outcome.Success(targetPoint)
            }
        }
        val viewModel = createTestViewModel(
            store = store,
            locationPreference = preference,
            deviceLocation = synchronousLocation,
        )

        // 1. Permission-confirmed warm action sent
        viewModel.onAction(CaptureAction.WarmAutomaticLocation)

        // 2. Take photo
        val permissionEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.TakePhoto)
        assertEquals(CaptureEffect.LaunchLocationPermission, permissionEffect.await())

        val captureEffect = async { viewModel.effects.first() }
        viewModel.onAction(CaptureAction.LocationPermissionChanged(true))
        val request = captureEffect.await() as CaptureEffect.CapturePhoto

        // 3. Photo is captured & normalized
        viewModel.onAction(CaptureAction.PhotoCaptured(request.staged))

        // 4. Assert: confirmedLocation has targetPoint and status is Attached
        assertEquals(1, viewModel.state.value.images.size)
        val img = viewModel.state.value.images.single()
        assertEquals(targetPoint, img.confirmedLocation?.point)
        assertEquals(CaptureLocationStatus.Attached, viewModel.state.value.locationStatusByImageId[img.id])

        // 5. Assert: current() was called exactly once
        assertEquals(1, currentCalls)
    }
}
