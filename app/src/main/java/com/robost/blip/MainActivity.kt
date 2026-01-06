package com.robost.blip

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robost.blip.home.HomeScreen
import com.robost.blip.home.HomeViewModel
import com.robost.blip.ui.theme.BlipTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()
        setContent {
            BlipTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navigateCommand by viewModel.navigateCommand.collectAsStateWithLifecycle()
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        driveSliderPosition = navigateCommand.signedTargetRpm.toFloat(),
                        onDriveSliderPositionChange = {
                            viewModel.onDriveSliderPositionChange(it)
                        },
                        onDriveSliderRelease = {
                            viewModel.onDriveSliderRelease()
                        }
                    )
//                    Home(
//                        status = viewModel.status.value ?: "Idle",
//                        targetRpm = targetRpm,
//                        onRpmChange = {
//                        },
//                        modifier = Modifier.padding(innerPadding),
//                        onBrakeClick = {
//                            viewModel.brake()
//                        },
//                        onDrivePress = {
//                            viewModel.drive(targetRpm.toFloat())
//                        },
//                        onDriveRelease = {
//                            viewModel.stop()
//                        }
//                    )
                }
            }
        }
    }
}


@Composable
fun Home(
    status: String,
    targetRpm: Int,
    onRpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBrakeClick: () -> Unit,
    onDrivePress: () -> Unit,
    onDriveRelease: () -> Unit
) {
    Column(modifier.padding(16.dp)) {
        Button(
            onClick = onBrakeClick, modifier = modifier
        ) {
            Text("Brake!")
        }
        Status(status, modifier)
        Text("Target RPM: $targetRpm")
        Slider(
            modifier = Modifier.padding(horizontal = 32.dp),
            value = targetRpm.toFloat(),
            onValueChange = { onRpmChange(it.toInt()) },
            valueRange = 0f..300f,
            steps = 299
        )
        PressReleaseButton(onDrivePress, onDriveRelease)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PressReleaseButton(
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val backgroundColor = if (isPressed) Color(0xFF1976D2) else Color(0xFF90CAF9)
    val textColor = if (isPressed) Color.White else Color.Black

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPress()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onRelease()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text("Drive", color = textColor)
    }
}

@Composable
fun Status(status: String, modifier: Modifier = Modifier) {
    Text(
        text = "Status $status!", modifier = modifier
    )
}