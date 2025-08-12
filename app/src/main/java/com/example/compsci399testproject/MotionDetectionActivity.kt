package com.example.compsci399testproject

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.compsci399testproject.sensors.RotationSensorService
import com.example.compsci399testproject.sensors.StepDetectionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun SensorTool() {
    val context = LocalContext.current

    // Step detection method toggle
    var useRhythm by remember { mutableStateOf(true) }
    var stepPulse by remember { mutableStateOf(false) }

    // Rotation service
    val rotationService = remember { RotationSensorService(context) }

    // Step detection
    val coroutineScope = rememberCoroutineScope()
    val stepService = remember {
        StepDetectionService(context, {
            coroutineScope.launch {
                stepPulse = true
                delay(1000)
                stepPulse = false
            }
        }, useRhythm)
    }

    // Start/stop listeners
    DisposableEffect(Unit) {
        rotationService.startListening()
        stepService.startListening()
        onDispose {
            rotationService.stopListening()
            stepService.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Compass image (rotates with azimuthCompass)
        Text("Heading: ${rotationService.azimuthCompass.toInt()}°", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.compass),
            contentDescription = "Compass",
            modifier = Modifier
                .size(150.dp)
                .rotate(rotationService.azimuthCompass)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Toggle detection strategy
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Rhythm Detection")
            Switch(
                checked = useRhythm,
                onCheckedChange = { useRhythm = it }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Step pulse visual (glowing dot)
        Box(
            modifier = Modifier
                .size(64.dp)
                .alpha(if (stepPulse) 1f else 0f)
                .background(color = Color.Green, shape = CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Step Detected", modifier = Modifier.alpha(if (stepPulse) 1f else 0f))
    }
}
