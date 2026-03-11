package com.example.astra

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.astra.ui.AppError
import com.example.astra.ui.ErrorTracker
import com.example.data.StarRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    private lateinit var orientationManager: OrientationManager
    private lateinit var locationManager: LocationManager
    private lateinit var starRepository: StarRepository
    private val errorTracker = ErrorTracker()

    private var cameraPermissionGranted by mutableStateOf(false)
    private var locationPermissionGranted by mutableStateOf(false)
    private var showPermissionRationale by mutableStateOf(false)
    private var showCalibrationDialog by mutableStateOf(false)
    private var hasShownCalibrationOnce by mutableStateOf(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (locationPermissionGranted) {
            locationManager.requestSingleUpdate()
        }

        // Show calibration dialog after permissions are granted
        if (cameraPermissionGranted && locationPermissionGranted && !hasShownCalibrationOnce) {
            showCalibrationDialog = true
            hasShownCalibrationOnce = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orientationManager = OrientationManager(this, errorTracker)
        locationManager = LocationManager(this, errorTracker)
        starRepository = StarRepository(this)

        lifecycleScope.launch {
            try {
                starRepository.seedDatabase()
            } catch (_: Exception) {
            }
        }

        cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA)
        locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        setContent {
            val rotationMatrix by orientationManager.rotationMatrix.collectAsState()
            val stars by starRepository.getAllStars().collectAsState(initial = emptyList())
            val location by locationManager.location.collectAsState()
            val errors = errorTracker.errors

            var isDarkMode by remember { mutableStateOf(false) }
            var showSnapshotInfo by remember { mutableStateOf(false) }
            var showFilterInfo by remember { mutableStateOf(false) }
            
            var showCalibrationDialogState by remember { mutableStateOf(showCalibrationDialog) }
            var showARTestDialog by remember { mutableStateOf(false) }
            var showCompassIssuesDialog by remember { mutableStateOf(false) }

            // Sync state with dialog visibility
            LaunchedEffect(showCalibrationDialog) {
                showCalibrationDialogState = showCalibrationDialog
            }

            val view = LocalView.current

            Box(modifier = Modifier.fillMaxSize()) {
                if (cameraPermissionGranted) {
                    CameraPreview(
                        onError = { errorTracker.reportError(AppError.CameraError(it)) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }

                SkyCanvas(
                    rotationMatrix = rotationMatrix,
                    stars = stars,
                    lat = location?.latitude ?: 0.0,
                    lon = location?.longitude ?: 0.0,
                    isDarkMode = isDarkMode
                )

                // Helper Buttons - TOP CENTER of screen (Landscape orientation)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Calibration Helper Button
                    SmallActionButton(
                        icon = Icons.Default.Explore,
                        contentDescription = "Calibrate Compass",
                        onClick = { showCalibrationDialogState = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    )

                    // AR Test Helper Button
                    SmallActionButton(
                        icon = Icons.Default.Navigation,
                        contentDescription = "Test AR Alignment",
                        onClick = { showARTestDialog = true },
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )

                    // Compass Issues Helper Button
                    SmallActionButton(
                        icon = Icons.Default.Warning,
                        contentDescription = "Compass Issues",
                        onClick = { showCompassIssuesDialog = true },
                        containerColor = MaterialTheme.colorScheme.error
                    )
                }

                // UI Controls - Fixed Landscape (USB on right side)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Filter Group
                    ActionGroup(
                        showInfo = showFilterInfo,
                        onToggleInfo = { showFilterInfo = !showFilterInfo },
                        infoText = "If camera permission wasn't given, the AR background defaults to black.",
                        icon = Icons.Default.Brightness4,
                        onAction = { isDarkMode = !isDarkMode },
                        fabColor = MaterialTheme.colorScheme.secondary
                    )

                    // Snapshot Group
                    ActionGroup(
                        showInfo = showSnapshotInfo,
                        onToggleInfo = { showSnapshotInfo = !showSnapshotInfo },
                        infoText = "Captures the current view (camera + stars) to Pictures/StarCompass.",
                        icon = Icons.Default.CameraAlt,
                        onAction = { captureAndSaveScreenshot(view) },
                        fabColor = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Dialogs
                if (showPermissionRationale && (!cameraPermissionGranted || !locationPermissionGranted)) {
                    PermissionDialog(
                        onConfirm = {
                            showPermissionRationale = false
                            checkAndRequestPermissions()
                        },
                        onDismiss = { showPermissionRationale = false }
                    )
                }

                if (showCalibrationDialogState) {
                    CalibrationDialog(
                        onDismiss = { 
                            showCalibrationDialogState = false
                            showCalibrationDialog = false
                        },
                        onCalibrate = {
                            // Trigger haptic feedback
                            triggerHapticFeedback()
                            showCalibrationDialogState = false
                            showCalibrationDialog = false
                        }
                    )
                }

                if (showARTestDialog) {
                    ARTestDialog(
                        onDismiss = { showARTestDialog = false }
                    )
                }

                if (showCompassIssuesDialog) {
                    CompassIssuesDialog(
                        onDismiss = { showCompassIssuesDialog = false }
                    )
                }

                if (errors.isNotEmpty()) {
                    ErrorOverlay(
                        errors = errors,
                        onDismiss = { errorTracker.dismissError(it) },
                        locationPermissionMissing = !locationPermissionGranted
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationManager.start()
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationManager.requestSingleUpdate()
        }
        cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA)
        locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        if (!cameraPermissionGranted || !locationPermissionGranted) {
            showPermissionRationale = true
        }
    }

    override fun onStop() {
        super.onStop()
        orientationManager.stop()
    }

    @Composable
    fun SmallActionButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        containerColor: Color
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor.copy(alpha = 0.85f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }
    }

    @Composable
    fun CalibrationDialog(onDismiss: () -> Unit, onCalibrate: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            icon = {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = "Calibrate",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { 
                Text(
                    "Calibrate Your Compass",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "This is CRITICAL for compass-based orientation!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    InstructionStep("1", "Open the app on your phone")
                    InstructionStep("2", "Hold the phone in landscape (USB on right)")
                    InstructionStep("3", "Move the phone in a figure-8 pattern for 10-15 seconds")
                    
                    Column(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BulletPoint("This calibrates the magnetometer")
                        BulletPoint("Do it away from metal objects, magnets, speakers")
                    }
                    
                    InstructionStep("4", "You should feel haptic feedback when calibration completes")
                }
            },
            confirmButton = {
                Button(onClick = onCalibrate) {
                    Text("Start Calibrating")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        )
    }

    @Composable
    fun ARTestDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            icon = {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "AR Test",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { 
                Text(
                    "Test AR Alignment",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InstructionStep("1", "Point phone at North (use a compass app to verify)")
                    BulletPoint("The big red \"N\" label should appear in the center/top")
                    
                    InstructionStep("2", "Rotate 90° to face East")
                    BulletPoint("Red \"E\" should now be in center")
                    
                    InstructionStep("3", "Look for bright stars (e.g., Sirius, Vega, Betelgeuse if visible)")
                    BulletPoint("Stars should stay locked to their sky positions as you rotate")
                    
                    InstructionStep("4", "Tilt phone up/down")
                    Column(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BulletPoint("Stars overhead should stay overhead")
                        BulletPoint("Horizon stars should stay at horizon")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Got It")
                }
            }
        )
    }

    @Composable
    fun CompassIssuesDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Compass Issues",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { 
                Text(
                    "🧭 Understanding Compass Issues on Android",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Your compass might be affected by:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    
                    IssueItem(
                        "Magnetic interference",
                        "Cases with magnets, metal surfaces, speakers"
                    )
                    IssueItem(
                        "Indoor environments",
                        "Steel beams in buildings"
                    )
                    IssueItem(
                        "Device calibration",
                        "Needs periodic figure-8 calibration"
                    )
                    IssueItem(
                        "Hardware limitations",
                        "Some phones have weaker magnetometers"
                    )
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Understood")
                }
            }
        )
    }

    @Composable
    fun InstructionStep(number: String, text: String) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                number,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }

    @Composable
    fun BulletPoint(text: String) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "•",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun IssueItem(title: String, description: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    private fun triggerHapticFeedback() {
        lifecycleScope.launch {
            // Simulate calibration time
            delay(3000)
            
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
            
            Toast.makeText(
                this@MainActivity,
                "Compass calibrated! ✓",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @Composable
    fun ActionGroup(
        showInfo: Boolean,
        onToggleInfo: () -> Unit,
        infoText: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onAction: () -> Unit,
        fabColor: Color
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (showInfo) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .widthIn(max = 220.dp)
                        .clickable { onToggleInfo() }
                ) {
                    Text(
                        text = infoText,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleInfo) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
            }

            FloatingActionButton(
                onClick = onAction,
                containerColor = fabColor,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(icon, contentDescription = "Action", modifier = Modifier.size(32.dp))
            }
        }
    }

    @Composable
    fun PermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionItem(
                        "Camera",
                        "Used to show the sky behind the star map for an Augmented Reality experience."
                    )
                    PermissionItem(
                        "Location",
                        "Used to calculate the exact position of stars relative to your current standing point."
                    )
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) { Text("Grant Access") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        )
    }

    @Composable
    fun PermissionItem(title: String, description: String) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, fontSize = 14.sp, textAlign = TextAlign.Justify)
        }
    }

    private fun captureAndSaveScreenshot(view: View) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val filename = "StarCompass_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/StarCompass")
            }
            val imageUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            fos = imageUri?.let { contentResolver.openOutputStream(it) }
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(this, "Snapshot saved to Pictures/StarCompass", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Failed to save snapshot", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun ErrorOverlay(
    errors: List<AppError>,
    onDismiss: (AppError) -> Unit,
    locationPermissionMissing: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        errors.forEach { error ->
            if (error is AppError.LocationError && !locationPermissionMissing) return@forEach

            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = error.message, modifier = Modifier.weight(1f))
                    if (error.recoverable) {
                        TextButton(onClick = { onDismiss(error) }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}
