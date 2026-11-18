package com.chuahws.mayalanguageapp.presentation.translation

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CameraX integration on the emulator's virtual camera, not a field-photo accuracy test. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class CameraCaptureWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun virtualCameraCapturesDecodablePhotoAndCanReopenAndCancel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val open = mutableStateOf(true)
        var captured: Uri? = null
        var error: String? = null
        var cancellations = 0
        compose.setContent { MayaTranslateTheme {
            if (open.value) CameraCaptureScreen(
                onCancel = { cancellations++; open.value = false },
                onImageCaptured = { captured = it; open.value = false }, onError = { error = it })
            else Text("Camera closed")
        } }
        compose.waitUntil(60_000) {
            error != null || compose.onAllNodes(hasText("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(error)
        compose.onNodeWithText("Take photo").performClick()
        compose.waitUntil(30_000) { captured != null || error != null }
        assertNull(error)
        val uri = requireNotNull(captured)
        val image = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertNotNull(image)
        assertTrue(image.width > 0 && image.height > 0)
        image.recycle()
        compose.runOnIdle { open.value = true }
        compose.onNodeWithContentDescription("Close camera").performClick()
        compose.onNodeWithText("Camera closed").assertIsDisplayed()
        assertEquals(1, cancellations)
        context.contentResolver.delete(uri, null, null)
    }
}
