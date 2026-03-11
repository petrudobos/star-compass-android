package com.example.astra

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (locationPermissionGranted) {
            locationManager.requestSingleUpdate()
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

                // UI Controls - Fixed Landscape (USB on right side)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalAlignment = Alignment.End // Fixed shifting by aligning icons/buttons to the right
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

                if (showPermissionRationale && (!cameraPermissionGranted || !locationPermissionGranted)) {
                    PermissionDialog(
                        onConfirm = {
                            showPermissionRationale = false
                            checkAndRequestPermissions()
                        },
                        onDismiss = { showPermissionRationale = false }
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
            horizontalArrangement = Arrangement.End // Keep elements right-aligned
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
