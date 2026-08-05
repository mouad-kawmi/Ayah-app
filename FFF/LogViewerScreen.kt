package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFile by remember { mutableStateOf("app.log") }

    LaunchedEffect(selectedFile) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val dir = context.getExternalFilesDir("logs")
            val file = File(dir, selectedFile)
            if (file.exists()) {
                // Stream the file, keeping only the last 500 lines in memory
                val lastLines = ArrayDeque<String>(500)
                file.useLines { lines ->
                    lines.forEach { line ->
                        if (lastLines.size == 500) {
                            lastLines.removeFirst()
                        }
                        lastLines.addLast(line)
                    }
                }
                logs = lastLines.reversed()
            } else {
                logs = listOf("File not found or empty.")
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Viewer ($selectedFile)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { selectedFile = if (selectedFile == "app.log") "error.log" else "app.log" }) {
                        Text(if (selectedFile == "app.log") "Show Errors" else "Show All")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF1E1E1E)),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(logs) { line ->
                    val color = when {
                        line.contains("ERROR") || line.contains("FATAL") -> Color(0xFFFF5252)
                        line.contains("WARNING") -> Color(0xFFFFD740)
                        line.contains("DEBUG") -> Color(0xFF69F0AE)
                        else -> Color.White
                    }
                    Text(
                        text = line,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
                }
            }
        }
    }
}
