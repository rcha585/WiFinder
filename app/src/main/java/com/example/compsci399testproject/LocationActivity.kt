package com.example.compsci399testproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compsci399testproject.ui.theme.COMPSCI399TestProjectTheme

@Composable
fun UserLocationIcon()
{
    Image(
        painter = painterResource(id = R.drawable.position_icon),
        contentDescription = "Position Icon",
        modifier = Modifier.size(80.dp)
            .shadow(
                8.dp,
                CircleShape,
            )
    )
}

@Composable
fun MapLocation() {
    Box(modifier = Modifier.fillMaxSize().background(colorResource(id = R.color.lighter_grey))) {
        UserLocationIcon()
    }
}

@Composable
fun FindingLocation() {
    Column(modifier = Modifier
        .fillMaxSize()
        .background(colorResource(id = R.color.lighter_grey))
        .padding(0.dp, 120.dp, 0.dp, 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Finding your location",
            color = colorResource(id = R.color.dark_blue),
            fontWeight = FontWeight(600),
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(
                fontSize = 24.sp
            ),
            modifier = Modifier.padding(0.dp, 50.dp, 0.dp, 0.dp)
        )

        Box(
            modifier = Modifier
                .padding(0.dp, 200.dp, 0.dp, 0.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(color = colorResource(id = R.color.dark_blue))
        )
    }
}

@Preview
@Composable
fun PreviewLocation() {
    FindingLocation()
}