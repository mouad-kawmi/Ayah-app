package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "alphaAnim"
    )

    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = CustomOutBackEasing
        ),
        label = "scaleAnim"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // Delay for splash screen (2.5s)
        onTimeout()
    }

    // Professional Islamic Splash Screen Layout
    // Matches the app's dark green rich theme
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        Color(0xFF0F2618)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                // If there's an app logo or icon, we can put it here.
                // Using an image vector from the res, or text-based logo.
                Text(
                    text = "﷽",
                    fontSize = 58.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "نور الإيمان",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    letterSpacing = 2.sp
                ),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Noor Al Iman",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = primaryColor.copy(alpha = 0.7f),
                    letterSpacing = 4.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

val CustomOutBackEasing = Easing { fraction ->
    val c1 = 1.70158f
    val c3 = c1 + 1f
    1f + c3 * Math.pow((fraction - 1f).toDouble(), 3.0).toFloat() + c1 * Math.pow((fraction - 1f).toDouble(), 2.0).toFloat()
}
