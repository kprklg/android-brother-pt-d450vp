package com.ptd450vp.labelprinter

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ptd450vp.labelprinter.ui.BrotherAppTheme
import com.ptd450vp.labelprinter.ui.EditorScreen
import com.ptd450vp.labelprinter.ui.LabelViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LabelViewModel by viewModels()

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImagePicked(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleUsbIntent(intent)

        setContent {
            BrotherAppTheme {
                EditorScreen(
                    viewModel = viewModel,
                    onPickImageForBlock = { blockId ->
                        if (blockId != null) {
                            viewModel.markPendingImageBlock(blockId)
                        }
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.refreshUsbInventory(autoSwitchIfDetected = true)
        }
    }
}
