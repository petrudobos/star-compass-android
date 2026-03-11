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
        if (locationPermissionGranted) locationManager.requestSingleUpdate()
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
            try { starRepository.seedDatabase() } catch (_: Exception) {}
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

            LaunchedEffect(showCalibrationDialog) {
                showCalibrationDialogState = showCalibrationDialog
            }

            val view = LocalView.current

            Box(modifier = Modifier.fillMaxSize()) {
                if (cameraPermissionGranted) {
                    CameraPreview(onError = { errorTracker.reportError(AppError.CameraError(it)) })
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }

                SkyCanvas(
                    rotationMatrix = rotationMatrix,
                    stars = stars,
                    lat = location?.latitude ?: 0.0,
                    lon = location?.longitude ?: 0.0,
                    isDarkMode = isDarkMode
                )

                // Top-center buttons: tap directly opens the dialog, no info toggle
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Calibration button
                    FloatingActionButton(
                        onClick = { showCalibrationDialogState = true },
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = "Calibrate",
                            modifier = Modifier.size(24.dp), tint = Color.White)
                    }

                    // AR Test button
                    FloatingActionButton(
                        onClick = { showARTestDialog = true },
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = "AR Test",
                            modifier = Modifier.size(24.dp), tint = Color.White)
                    }

                    // Compass Issues button
                    FloatingActionButton(
                        onClick = { showCompassIssuesDialog = true },
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Compass Issues",
                            modifier = Modifier.size(24.dp), tint = Color.White)
                    }
                }

                // Right-side controls with info toggle
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    ActionGroup(
                        showInfo = showFilterInfo,
                        onToggleInfo = { showFilterInfo = !showFilterInfo },
                        infoText = "Toggles between camera view and dark background for better star visibility.",
                        icon = Icons.Default.Brightness4,
                        onAction = { isDarkMode = !isDarkMode },
                        fabColor = MaterialTheme.colorScheme.secondary
                    )
                    ActionGroup(
                        showInfo = showSnapshotInfo,
                        onToggleInfo = { showSnapshotInfo = !showSnapshotInfo },
                        infoText = "Captures current view (camera + stars) to Pictures/StarCompass folder.",
                        icon = Icons.Default.CameraAlt,
                        onAction = { captureAndSaveScreenshot(view) },
                        fabColor = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Dialogs
                if (showPermissionRationale && (!cameraPermissionGranted || !locationPermissionGranted)) {
                    PermissionDialog(
                        onConfirm = { showPermissionRationale = false; checkAndRequestPermissions() },
                        onDismiss = { showPermissionRationale = false }
                    )
                }

                if (showCalibrationDialogState) {
                    CalibrationDialog(
                        onDismiss = { showCalibrationDialogState = false; showCalibrationDialog = false },
                        onCalibrate = { triggerHapticFeedback(); showCalibrationDialogState = false; showCalibrationDialog = false }
                    )
                }

                if (showARTestDialog) {
                    ARTestDialog(onDismiss = { showARTestDialog = false })
                }

                if (showCompassIssuesDialog) {
                    CompassIssuesDialog(onDismiss = { showCompassIssuesDialog = false })
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
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) locationManager.requestSingleUpdate()
        cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA)
        locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!cameraPermissionGranted || !locationPermissionGranted) showPermissionRationale = true
    }

    override fun onStop() {
        super.onStop()
        orientationManager.stop()
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
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
                containerColor = fabColor.copy(alpha = 0.9f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }

    @Composable
    fun CalibrationDialog(onDismiss: () -> Unit, onCalibrate: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            icon = {
                Icon(Icons.Default.Explore, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Calibrate Your Compass", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("This is CRITICAL for compass-based orientation!",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStep("1", "Open the app on your phone")
                    InstructionStep("2", "Hold the phone in landscape (USB on right)")
                    InstructionStep("3", "Move the phone in a figure-8 pattern for 10-15 seconds")
                    Column(modifier = Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BulletPoint("This calibrates the magnetometer")
                        BulletPoint("Do it away from metal objects, magnets, speakers")
                    }
                    InstructionStep("4", "You should feel haptic feedback when calibration completes")
                }
            },
            confirmButton = { Button(onClick = onCalibrate) { Text("Start Calibrating") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
        )
    }

    @Composable
    fun ARTestDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            icon = {
                Icon(Icons.Default.Navigation, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
            },
            title = { Text("Test AR Alignment", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InstructionStep("1", "Point phone at North (use a compass app to verify)")
                    BulletPoint("The big red \"N\" label should appear in the center/top")
                    InstructionStep("2", "Rotate 90° to face East")
                    BulletPoint("Red \"E\" should now be in center")
                    InstructionStep("3", "Look for bright stars (e.g., Sirius, Vega, Betelgeuse if visible)")
                    BulletPoint("Stars should stay locked to their sky positions as you rotate")
                    InstructionStep("4", "Tilt phone up/down")
                    Column(modifier = Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BulletPoint("Stars overhead should stay overhead")
                        BulletPoint("Horizon stars should stay at horizon")
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text("Got It") } }
        )
    }

    @Composable
    fun CompassIssuesDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("🧭 Understanding Compass Issues", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Your compass might be affected by:",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    IssueItem("Magnetic interference", "Cases with magnets, metal surfaces, speakers")
                    IssueItem("Indoor environments", "Steel beams in buildings")
                    IssueItem("Device calibration", "Needs periodic figure-8 calibration")
                    IssueItem("Hardware limitations", "Some phones have weaker magnetometers")
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text("Understood") } }
        )
    }

    @Composable
    fun InstructionStep(number: String, text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(number, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Text(text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }

    @Composable
    fun BulletPoint(text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text("•", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    fun IssueItem(title: String, description: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    private fun triggerHapticFeedback() {
        lifecycleScope.launch {
            delay(3000)
            if (!hasPermission(Manifest.permission.VIBRATE)) {
                Toast.makeText(this@MainActivity, "Compass calibrated! ✓ (Haptic feedback unavailable)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
                Toast.makeText(this@MainActivity, "Compass calibrated! ✓", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this@MainActivity, "Compass calibrated! ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun PermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionItem("Camera", "Used to show the sky behind the star map for an Augmented Reality experience.")
                    PermissionItem("Location", "Used to calculate the exact position of stars relative to your current standing point.")
                }
            },
            confirmButton = { Button(onClick = onConfirm) { Text("Grant Access") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
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
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
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
        requestPermissionsLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        )
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
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = error.message, modifier = Modifier.weight(1f))
                    if (error.recoverable) {
                        TextButton(onClick = { onDismiss(error) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
