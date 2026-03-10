package com.example.astra

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.core.AstronomyUtils
import com.example.core.HorizonCoords
import com.example.data.Star
import kotlin.math.*

@Composable
fun SkyCanvas(
    rotationMatrix: FloatArray,
    stars: List<Star>,
    lat: Double,
    lon: Double
) {
    val context = LocalContext.current
    val lst = AstronomyUtils.calculateLST(lon)
    val sunPos = AstronomyUtils.calculateSunPosition()

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        
        val fovY = 60f 
        val aspectRatio = width / height
        val fovX = fovY * aspectRatio
        
        val scaleX = width / fovX
        val scaleY = height / fovY

        // Helper for drawing landscape-oriented text below a point
        fun drawLandscapeText(text: String, x: Float, y: Float, paint: Paint) {
            drawContext.canvas.nativeCanvas.save()
            // Rotate 90 degrees if the app is in portrait but we want landscape readability
            // However, usually "landscape mode" means the text is horizontal when the phone is held sideways.
            // If the activity is portrait, rotating -90 or 90 makes it readable in landscape.
            // Assuming the user wants it readable when the phone is held horizontally:
            drawContext.canvas.nativeCanvas.rotate(-90f, x, y)
            drawContext.canvas.nativeCanvas.drawText(text, x, y + 40f, paint) // 40f below
            drawContext.canvas.nativeCanvas.restore()
        }

        // Draw Sun
        val sunHorizon = AstronomyUtils.toHorizon(sunPos.ra, sunPos.dec, lat, lon, lst)
        val sunScreenPos = projectToScreen(sunHorizon.az, sunHorizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
        if (sunScreenPos != null) {
            drawCircle(
                color = Color.Yellow,
                radius = 20f,
                center = sunScreenPos
            )
            drawLandscapeText(
                "SUN",
                sunScreenPos.x,
                sunScreenPos.y + 25f,
                Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 40f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
        }

        // Draw Cardinal Directions
        val directions = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
        directions.forEach { (label, az) ->
            val screenPos = projectToScreen(az, 0.0, rotationMatrix, centerX, centerY, scaleX, scaleY)
            if (screenPos != null) {
                drawLandscapeText(
                    label,
                    screenPos.x,
                    screenPos.y,
                    Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 60f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                )
            }
        }

        // Draw Stars and Labels
        stars.forEach { star ->
            val horizon = AstronomyUtils.toHorizon(star.ra, star.dec, lat, lon, lst)
            val screenPos = projectToScreen(horizon.az, horizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)

            if (screenPos != null) {
                val starSize = max(3f, 10f - star.magnitude * 1.5f)
                val starAlpha = (1.0f - (star.magnitude / 6.0f)).coerceIn(0.2f, 1.0f)
                
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha),
                    radius = starSize,
                    center = screenPos
                )

                // Increased magnitude threshold to 4.0 to include Megrez (3.3) and others
                if (star.magnitude < 4.0f && star.commonName != null) {
                    drawLandscapeText(
                        star.commonName!!,
                        screenPos.x,
                        screenPos.y + starSize + 10f,
                        Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 30f
                            textAlign = Paint.Align.CENTER
                            alpha = (starAlpha * 255).toInt()
                        }
                    )
                }
            }
        }

        // Draw Constellation Lines
        drawConstellations(stars, lat, lon, lst, rotationMatrix, centerX, centerY, scaleX, scaleY)
    }
}

fun projectToScreen(
    az: Double,
    alt: Double,
    rotationMatrix: FloatArray,
    centerX: Float,
    centerY: Float,
    scaleX: Float,
    scaleY: Float
): Offset? {
    val altRad = Math.toRadians(alt)
    val azRad = Math.toRadians(az)
    
    val worldX = cos(altRad) * sin(azRad)
    val worldY = sin(altRad)
    val worldZ = cos(altRad) * cos(azRad)

    val screenX_vec = rotationMatrix[0] * worldX + rotationMatrix[1] * worldY + rotationMatrix[2] * worldZ
    val screenY_vec = rotationMatrix[4] * worldX + rotationMatrix[5] * worldY + rotationMatrix[6] * worldZ
    val screenZ_vec = rotationMatrix[8] * worldX + rotationMatrix[9] * worldY + rotationMatrix[10] * worldZ

    if (screenZ_vec > 0) {
        val screenX = centerX + (screenX_vec / screenZ_vec).toFloat() * scaleX * 50f
        val screenY = centerY - (screenY_vec / screenZ_vec).toFloat() * scaleY * 50f
        return Offset(screenX, screenY)
    }
    return null
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellations(
    stars: List<Star>,
    lat: Double,
    lon: Double,
    lst: Double,
    rotationMatrix: FloatArray,
    centerX: Float,
    centerY: Float,
    scaleX: Float,
    scaleY: Float
) {
    val connections = mutableListOf<Pair<String, String>>()
    
    // Ursa Major (Big Dipper)
    connections.addAll(listOf(
        "Dubhe" to "Merak", "Merak" to "Phecda", "Phecda" to "Megrez",
        "Megrez" to "Alioth", "Alioth" to "Mizar", "Mizar" to "Alkaid", "Megrez" to "Dubhe"
    ))

    // Cassiopeia (W shape)
    connections.addAll(listOf(
        "Caph" to "Schedar", "Schedar" to "Tsih", "Tsih" to "Ruchbah", "Ruchbah" to "Segin"
    ))

    // Orion
    connections.addAll(listOf(
        "Betelgeuse" to "Bellatrix", "Bellatrix" to "Rigel", "Rigel" to "Saiph", "Saiph" to "Betelgeuse",
        "Alnitak" to "Alnilam", "Alnilam" to "Mintaka" // Belt
    ))

    // Crux (Southern Cross)
    connections.addAll(listOf(
        "Gacrux" to "Acrux", "Mimosa" to "Imai"
    ))

    // Leo
    connections.addAll(listOf(
        "Regulus" to "Algieba", "Algieba" to "Zosma", "Zosma" to "Denebola", "Denebola" to "Regulus"
    ))

    connections.forEach { (s1, s2) ->
        val star1 = stars.find { it.commonName == s1 }
        val star2 = stars.find { it.commonName == s2 }
        if (star1 != null && star2 != null) {
            val h1 = AstronomyUtils.toHorizon(star1.ra, star1.dec, lat, lon, lst)
            val h2 = AstronomyUtils.toHorizon(star2.ra, star2.dec, lat, lon, lst)
            val p1 = projectToScreen(h1.az, h1.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
            val p2 = projectToScreen(h2.az, h2.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
            if (p1 != null && p2 != null) {
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.4f),
                    start = p1,
                    end = p2,
                    strokeWidth = 2f
                )
            }
        }
    }
}
