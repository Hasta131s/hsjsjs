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
import com.example.ui.theme.*

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Corporate style crosshair badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(HighDensityPrimaryBlue, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Draw minimalist white crosshair symbol
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        val cx = this.size.width / 2f
                                        val cy = this.size.height / 2f
                                        val len = this.size.width * 0.7f
                                        
                                        // Draw horizontal white line
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(cx - len / 2f, cy),
                                            end = Offset(cx + len / 2f, cy),
                                            strokeWidth = 2.5.dp.toPx()
                                        )
                                        // Draw vertical white line
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(cx, cy - len / 2f),
                                            end = Offset(cx, cy + len / 2f),
                                            strokeWidth = 2.5.dp.toPx()
                                        )
                                    }
                            )
                        }
                        
                        Column {
                            Text(
                                text = "TVNAH CROSS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = HighDensityTextDark,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Sade ve Kurumsal Nişangah Pro",
                                fontSize = 11.sp,
                                color = HighDensityTextMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HighDensityBackground,
                    titleContentColor = HighDensityTextDark
                ),
                actions = {
                    IconButton(
                        onClick = { /* Settings action */ },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .background(HighDensityGreySurface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = HighDensityTextDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(HighDensityBackground)
                .verticalScroll(rememberScrollState())
        ) {
            
            // Permission warning block with new corporate warning background (DCE2F9)
            if (!isOverlayPermissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = HighDensityWarnContainer),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Uyarı",
                                tint = HighDensityPrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "İzin Gerekiyor",
                                fontWeight = FontWeight.Bold,
                                color = HighDensityWarnText,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Crosshair'i oyunların üzerinde göstermek için \"Diğer uygulamaların üzerinde görüntüleme\" izni verin.",
                                color = HighDensityWarnText.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.testTag("grant_permission_btn")
                            ) {
                                Text(
                                    text = "İZİN VER",
                                    color = HighDensityPrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 1. LIVE CROSSHAIR PREVIEW BOARD (Interactive Grid - 32dp Curved Slate-900 Box)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = HighDensityLivePreviewBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Tactical grids inside the slate container
                            val step = 20.dp.toPx()
                            val width = this.size.width
                            val height = this.size.height
                            
                            // Fine grid lines
                            for (x in 0..width.toInt() step step.toInt()) {
                                drawLine(
                                    color = Color(30, 41, 59, 110), // slate-800 subtle
                                    start = Offset(x.toFloat(), 0f),
                                    end = Offset(x.toFloat(), height),
                                    strokeWidth = 1f
                                )
                            }
                            for (y in 0..height.toInt() step step.toInt()) {
                                drawLine(
                                    color = Color(30, 41, 59, 110),
                                    start = Offset(0f, y.toFloat()),
                                    end = Offset(width, y.toFloat()),
                                    strokeWidth = 1f
                                )
                            }
                            // Center coordinates
                            drawLine(
                                color = Color(0xFF00FF7F).copy(alpha = 0.12f),
                                start = Offset(width / 2, 0f),
                                end = Offset(width / 2, height),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color(0xFF00FF7F).copy(alpha = 0.12f),
                                start = Offset(0f, height / 2),
                                end = Offset(width, height / 2),
                                strokeWidth = 1.5f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
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

                    // Monospace uppercase preview indicator
                    Text(
                        text = "ÖNİZLEME",
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )

                    // Right bottom status indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = if (isServiceActive) NeonGreen else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isServiceActive) "Aktif" else "Pasif",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. CONFIGURATION CONTROL MODULES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Shape Header Label
                Text(
                    text = "STİL SEÇİMİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = HighDensityTextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                // Layout Shape Selection curved list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(HighDensityGreySurface)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shapes = listOf(
                        "cross" to "+",
                        "dot" to "•",
                        "t_cross" to "T",
                        "circle" to "O",
                        "square" to "▢",
                        "plus_dot" to "⊕"
                    )
                    
                    shapes.forEach { (key, symbol) ->
                        val isSelected = shape == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) HighDensityPrimaryBlue.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { shape = key }
                                .testTag("shape_option_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbol,
                                fontSize = if (symbol == "•") 24.sp else if (symbol == "▢") 20.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) HighDensityPrimaryBlue else HighDensityTextDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom High Density Sliders (Side-by-side / Compact arrangement)
                Text(
                    text = "NİŞANGAH İNCE AYARLARI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = HighDensityTextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = HighDensityGreySurface),
                    border = BorderStroke(1.dp, HighDensityBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        // Size Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Nişangah Boyutu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = HighDensityTextDark)
                            Text(text = "$size dp", fontWeight = FontWeight.Bold, color = HighDensityPrimaryBlue, fontSize = 13.sp)
                        }
                        Slider(
                            value = size.toFloat(),
                            onValueChange = { size = it.toInt() },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = HighDensityPrimaryBlue,
                                thumbColor = HighDensityPrimaryBlue
                            ),
                            modifier = Modifier.testTag("size_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Thickness Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Çizgi Kalınlığı", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = HighDensityTextDark)
                            Text(text = String.format("%.1f dp", thickness), fontWeight = FontWeight.Bold, color = HighDensityPrimaryBlue, fontSize = 13.sp)
                        }
                        Slider(
                            value = thickness,
                            onValueChange = { thickness = it },
                            valueRange = 1f..10f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = HighDensityPrimaryBlue,
                                thumbColor = HighDensityPrimaryBlue
                            ),
                            modifier = Modifier.testTag("thickness_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Gap Size Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Merkez Boşluk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = HighDensityTextDark)
                            Text(text = "$gap dp", fontWeight = FontWeight.Bold, color = HighDensityPrimaryBlue, fontSize = 13.sp)
                        }
                        Slider(
                            value = gap.toFloat(),
                            onValueChange = { gap = it.toInt() },
                            valueRange = 0f..25f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = HighDensityPrimaryBlue,
                                thumbColor = HighDensityPrimaryBlue
                            ),
                            modifier = Modifier.testTag("gap_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Opacity Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Saydamlık (Opaklık)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = HighDensityTextDark)
                            Text(text = "${(opacity * 100).toInt()}%", fontWeight = FontWeight.Bold, color = HighDensityPrimaryBlue, fontSize = 13.sp)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = HighDensityPrimaryBlue,
                                thumbColor = HighDensityPrimaryBlue
                            ),
                            modifier = Modifier.testTag("opacity_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. COLOR SELECTION CARD
                Text(
                    text = "RENK ÖZELLEŞTİRME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = HighDensityTextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = HighDensityGreySurface),
                    border = BorderStroke(1.dp, HighDensityBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (usePresetColors) HighDensityPrimaryBlue else Color.Transparent)
                                    .clickable { usePresetColors = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Hazır Renkler",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (usePresetColors) Color.White else HighDensityTextMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!usePresetColors) HighDensityPrimaryBlue else Color.Transparent)
                                    .clickable { usePresetColors = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Hassas Spektrum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!usePresetColors) Color.White else HighDensityTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (usePresetColors) {
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
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(code))
                                            .border(
                                                width = if (isPicked) 3.dp else 1.dp,
                                                color = if (isPicked) HighDensityPrimaryBlue else Color.Gray.copy(alpha = 0.3f),
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
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val previewHsvColor = remember(customHue) {
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
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(previewHsvColor)
                                        .border(2.dp, Color.White, CircleShape)
                                )

                                Column(modifier = Modifier.weight(1f)) {
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
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent,
                                            thumbColor = HighDensityPrimaryBlue
                                        ),
                                        modifier = Modifier.testTag("hue_slider")
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. CONTRAST GUIDE OUTLINES
                Text(
                    text = "REHBER VE KONTRAST ÖZELLİKLERİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = HighDensityTextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = HighDensityGreySurface),
                    border = BorderStroke(1.dp, HighDensityBorder)
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
                                text = "Dış Kontrast Çizgisi (Dış Kenar)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = HighDensityTextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Nişangahı her arka planda belirgin yapmak için siyah dış kenarlık ekler.",
                                fontSize = 11.sp,
                                color = HighDensityTextMuted,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = outline,
                            onCheckedChange = { outline = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HighDensityPrimaryBlue
                            ),
                            modifier = Modifier.testTag("outline_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // MAIN TOGGLE ACTIVE ENGINE (High Density visual layout matching "CROSSHAIR'I BAŞLAT" button specification)
                Button(
                    onClick = { toggleService() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("toggle_crosshair_btn"),
                    shape = RoundedCornerShape(100.dp), // Fully round capsule button as requested
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceActive) Color(0xFFD32F2F) else HighDensityPrimaryBlue,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
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
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 5. INTERACTIVE NOTIFICATION DRAWER PREVIEW BOX (Tactile Gamer Experience Touch)
                Text(
                    text = "BİLDİRİM PANELİ CANLI ÖNİZLEME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = HighDensityTextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, HighDensityBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Small icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(HighDensityGreySurface, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = HighDensityTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Text fields
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BİLDİRİM PANELİ SİMÜLASYONU",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighDensityTextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isServiceActive) "TVNAH CROSS Aktif • Kapatmak için dokun" else "Aktif servis yok",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isServiceActive) HighDensityTextDark else HighDensityTextMuted
                            )
                        }

                        // Close button inside mock
                        if (isServiceActive) {
                            Button(
                                onClick = { toggleService() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = Color(0xFFC62828)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = "KAPAT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(HighDensityGreySurface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PASİF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighDensityTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
