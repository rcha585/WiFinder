package com.example.compsci399testproject

import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compsci399testproject.utils.CoordTransform

class LocationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoordTransform.load(applicationContext)
        setContent { FindingLocationScreen() }
    }
}

@Composable
fun FindingLocationScreen() {
    val context = LocalContext.current

    var oxText by remember { mutableStateOf(CoordTransform.originX.toString()) }
    var oyText by remember { mutableStateOf(CoordTransform.originY.toString()) }
    var invY by remember { mutableStateOf(CoordTransform.invertY) }
    var scaleText by remember { mutableStateOf(CoordTransform.pxPerUnit.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.lighter_grey))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Origin / Transform",
            color = colorResource(id = R.color.dark_blue),
            fontWeight = FontWeight(700),
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(fontSize = 22.sp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = oxText, onValueChange = { oxText = it },
            label = { Text("Origin X") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = oyText, onValueChange = { oyText = it },
            label = { Text("Origin Y") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = scaleText, onValueChange = { scaleText = it },
            label = { Text("Scale (px per unit, default 1)") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = invY, onCheckedChange = { invY = it })
            Text("Invert Y (screen Y down)", color = colorResource(R.color.dark_blue))
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                // 一键设置为 (885, 972)
                oxText = "885"; oyText = "972"
            }) { Text("Use (885, 972)") }

            Button(
                onClick = {
                    val ox = oxText.toFloatOrNull() ?: CoordTransform.originX
                    val oy = oyText.toFloatOrNull() ?: CoordTransform.originY
                    val sc = scaleText.toFloatOrNull() ?: CoordTransform.pxPerUnit
                    CoordTransform.save(context = context, ox = ox, oy = oy, scale = sc, invY = invY)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.light_blue),
                    contentColor = colorResource(id = R.color.darker_white)
                )
            ) { Text("Save") }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colorResource(id = R.color.light_blue), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                "Current:\n" +
                        "Origin = (${CoordTransform.originX}, ${CoordTransform.originY})\n" +
                        "Scale  = ${CoordTransform.pxPerUnit}\n" +
                        "InvertY= ${CoordTransform.invertY}",
                color = colorResource(id = R.color.dark_blue)
            )
        }
    }
}
