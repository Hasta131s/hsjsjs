package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CrosshairDashboard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrosshairDashboard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Shared preferences for customization
    val sharedPrefs = remember { context.getSharedPreferences("tvnah_cross_prefs", Context.MODE_PRIVATE) }

    // Read initial settings
    var shape by remember { mutableStateOf(sharedPrefs.getString("shape", "cross") ?: "cross") }
    var size by remember { mutableStateOf(sharedPrefs.getInt("size", 40)) }
    var selectedColorCode by remember { mutableStateOf(sharedPrefs.getInt("color", 0xFF00FF00.toInt())) }
    var thickness by remember { mutableStateOf(sharedPrefs.getFloat("thickness", 3f)) }
    var opacity by remember { mutableStateOf(sharedPrefs.getFloat("opacity", 1.0f)) }
    var gap by remember { mutableStateOf(sharedPrefs.getInt("gap", 4)) }
    var outline by remember { mutableStateOf(sharedPrefs.getBoolean("outline", true)) }

    // Service status & permission checks
    var isOverlayPermissionGranted by remember { mutableStateOf(false) }
    var isNotificationPermissionGranted by remember { mutableStateOf(false) }
    var isServiceActive by remember { mutableStateOf(false) }

    // Continuous custom hue selector state
    var customHue by remember { mutableStateOf(120f) } // Default green hue
    var usePresetColors by remember { mutableStateOf(true) }

    // Preset color map (Label -> Hex AARRGGBB)
    val colorPresets = remember {
        listOf(
            "Neon Yeşil" to 0xFF00FF00.toInt(),
            "Neon Siyan" to 0xFF00FFFF.toInt(),
            "Kırmızı" to 0xFFFF0033.toInt(),
            "Sarı" to 0xFFFFFF00.toInt(),
            "Elektrik Mor" to 0xFFCC00FF.toInt(),
            "Saf Beyaz" to 0xFFFFFFFF.toInt(),
            "Mat Siyah" to 0xFF222222.toInt()
        )
    }

    // Direct helper to save and write parameters to preference
    fun persistSettings() {
        sharedPrefs.edit().apply {
            putString("shape", shape)
            putInt("size", size)
            putInt("color", selectedColorCode)
            putFloat("thickness", thickness)
            putFloat("opacity", opacity)
            putInt("gap", gap)
            putBoolean("outline", outline)
            apply()
        }
    }

    // Effect to check and sync permissions / service status when view resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }
                isNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                isServiceActive = CrosshairService.isServiceRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Trigger changes immediately to preferences so that they register live inside overlay service
    LaunchedEffect(shape, size, selectedColorCode, thickness, opacity, gap, outline) {
        persistSettings()
    }

    // Notification permission launcher for Android 13+
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationPermissionGranted = granted
        // Start service now that policy check is done
        val serviceIntent = Intent(context, CrosshairService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        isServiceActive = true
    }

    fun toggleService() {
        if (!isOverlayPermissionGranted) {
            // Must ask for permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
            return
        }

        if (isServiceActive) {
            // Stop service
            val serviceIntent = Intent(context, CrosshairService::class.java)
            context.stopService(serviceIntent)
            isServiceActive = false
        } else {
            // Start service - request notification first if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val serviceIntent = Intent(context, CrosshairService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                isServiceActive = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TVNAH CROSS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Profesyonel Nişangah Özelleştirici",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(12.dp)
                            .background(
                                color = if (isServiceActive) MaterialTheme.colorScheme.primary else Color.Red,
                                shape = CircleShape
                            )
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            
            // Permission warning block if overlay window permission is missing
            if (!isOverlayPermissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF661E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ekran Üzerinde Gösterme İzni Gerekli",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nişangahı diğer oyunların ve uygulamaların üstünde gösterebilmek için 'Ekranın üstünde çizim yapma' izni vermelisiniz.",
                            color = Color(0xFFECEFF1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF661E1E)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_permission_btn")
                        ) {
                            Text("İzin Ver", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. LIVE CROSSHAIR PREVIEW BOARD (Interactive Grid / Checkerboard overlay design)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Draw nice tactical checkerboard grids for crosshair sight checking
                            val step = 16.dp.toPx()
                            val width = this.size.width
                            val height = this.size.height
                            
                            // Draws custom fine subtle reticle lines
                            for (x in 0..width.toInt() step step.toInt()) {
                                drawLine(
                                    color = Color(0xFF222C37),
                                    start = Offset(x.toFloat(), 0f),
                                    end = Offset(x.toFloat(), height),
                                    strokeWidth = 1f
                                )
                            }
                            for (y in 0..height.toInt() step step.toInt()) {
                                drawLine(
                                    color = Color(0xFF222C37),
                                    start = Offset(0f, y.toFloat()),
                                    end = Offset(width, y.toFloat()),
                                    strokeWidth = 1f
                                )
                            }
                            // Center absolute benchmark reticles
                            drawLine(
                                color = Color(0xFF00FF7F).copy(alpha = 0.15f),
                                start = Offset(width / 2, 0f),
                                end = Offset(width / 2, height),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color(0xFF00FF7F).copy(alpha = 0.15f),
                                start = Offset(0f, height / 2),
                                end = Offset(width, height / 2),
                                strokeWidth = 1.5f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Embed exact custom CrosshairView inside Compose via AndroidView for 100% parity
                    AndroidView(
                        factory = { ctx ->
                            CrosshairView(ctx).apply {
                                this.shape = shape
                                this.crosshairSize = size
                                this.crosshairColor = selectedColorCode
                                this.thickness = thickness
                                this.opacity = opacity
                                this.gap = gap
                                this.outline = outline
                            }
                        },
                        update = { view ->
                            view.shape = shape
                            view.crosshairSize = size
                            view.crosshairColor = selectedColorCode
                            view.thickness = thickness
                            view.opacity = opacity
                            view.gap = gap
                            view.outline = outline
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay watermark preview indicator
                    Text(
                        text = "CANLI ÖNİZLEME",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }

            // 2. STYLING PARAMETERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Section Title
                Text(
                    text = "NİŞANGAH ŞEKLİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Layout Shape options Grid custom styling
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shapes = listOf(
                        "cross" to "Artı (Plus)",
                        "dot" to "Nokta (Dot)",
                        "t_cross" to "T-Şekli",
                        "circle" to "Daire",
                        "square" to "Kare",
                        "plus_dot" to "Karma (Plus+Dot)"
                    )
                    shapes.forEach { (key, title) ->
                        val isSelected = shape == key
                        Card(
                            modifier = Modifier
                                .widthIn(min = 100.dp)
                                .clickable { shape = key }
                                .testTag("shape_option_$key"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Slider Cards Block
                Text(
                    text = "BOYUT, KALINLIK VE ÖLÇEKLER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        // Size Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Nişangah Boyutu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = "$size dp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        Slider(
                            value = size.toFloat(),
                            onValueChange = { size = it.toInt() },
                            valueRange = 10f..120f,
                            modifier = Modifier.testTag("size_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Thickness Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Çizgi Kalınlığı", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = String.format("%.1f dp", thickness), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        Slider(
                            value = thickness,
                            onValueChange = { thickness = it },
                            valueRange = 1f..12f,
                            modifier = Modifier.testTag("thickness_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gap Size Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Merkez Boşluk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = "$gap dp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        Slider(
                            value = gap.toFloat(),
                            onValueChange = { gap = it.toInt() },
                            valueRange = 0f..30f,
                            modifier = Modifier.testTag("gap_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Opacity Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Saydamlık (Opaklık)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = "${(opacity * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.testTag("opacity_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. COLOR SELECTION CARD
                Text(
                    text = "RENK ÖZELLEŞTİRME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        // Selector Tabs between Preset and Custom Spectrum
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (usePresetColors) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { usePresetColors = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Hazır Renkler",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (usePresetColors) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (!usePresetColors) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { usePresetColors = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Hassas Spektrum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!usePresetColors) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (usePresetColors) {
                            // Render presets horizontal rows list
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                colorPresets.forEach { (name, code) ->
                                    val isPicked = selectedColorCode == code
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(code))
                                            .border(
                                                width = if (isPicked) 3.dp else 1.dp,
                                                color = if (isPicked) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColorCode = code }
                                            .testTag("color_preset_$name"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPicked) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seçildi",
                                                tint = if (code == 0xFFFFFFFF.toInt()) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Infinite Spectrum Slider Hue
                            val previewHsvColor = remember(customHue) {
                                val floatArray = floatArrayOf(customHue, 1f, 1f)
                                Color.hsv(customHue, 1f, 1f)
                            }
                            
                            LaunchedEffect(customHue) {
                                selectedColorCode = previewHsvColor.toArgb()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Color swatch preview circle
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(previewHsvColor)
                                        .border(2.dp, Color.White, CircleShape)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Renk Tonu Değeri",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    // Colored gradient brush slider representing all colors
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.Red, Color.Yellow, Color.Green,
                                                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                    )
                                                )
                                            )
                                    )
                                    Slider(
                                        value = customHue,
                                        onValueChange = { customHue = it },
                                        valueRange = 0f..360f,
                                        modifier = Modifier.testTag("hue_slider")
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. CONTRAST OUTLINE OPTIONS
                Text(
                    text = "REHBER VE KONTRAST ÖZELLİKLERİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dış Kontrast Çizgisi (Dış Çerçeve)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Nişangahı her arka planda belirgin yapmak için siyah dış kenarlık ekler.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = outline,
                            onCheckedChange = { outline = it },
                            modifier = Modifier.testTag("outline_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // TOP LEVEL TRIGGER TOGGLE ENGINE
                Button(
                    onClick = { toggleService() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("toggle_crosshair_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceActive) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                        contentColor = if (isServiceActive) Color.White else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceActive) "NİŞANGAHI KAPAT" else "NİŞANGAHI BAŞLAT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
