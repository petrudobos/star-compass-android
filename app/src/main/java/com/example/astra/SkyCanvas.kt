package com.example.astra

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.core.AstronomyUtils
import com.example.data.Star
import java.util.Locale
import kotlin.math.*

@Composable
fun SkyCanvas(
    rotationMatrix: FloatArray,
    stars: List<Star>,
    lat: Double,
    lon: Double,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val lst = AstronomyUtils.calculateLST(lon)
    val sunPos = AstronomyUtils.calculateSunPosition()

    val constellationLabels = remember { mutableStateListOf<ConstellationLabel>() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    constellationLabels.forEach { label ->
                        if (offset.x in (label.x - 100f)..(label.x + 100f) &&
                            offset.y in (label.y - 50f)..(label.y + 50f)
                        ) {
                            val lang = Locale.getDefault().language
                            val url = "https://$lang.wikipedia.org/wiki/${label.name.replace(" ", "_")}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }
                }
            }
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

        if (isDarkMode) {
            drawRect(color = Color.Black.copy(alpha = 0.7f))
        }

        // Updated for native landscape: Draw text upright (0 rotation) or 0 rotation instead of 90.
        // The request says rotate 90 degrees counter clockwise. 
        // Previously it was canvas.nativeCanvas.rotate(90f, x, y) which is 90 clockwise.
        // So 0f (upright in landscape) is technically 90 CCW from the previous state.
        fun drawSkyText(text: String, x: Float, y: Float, paint: Paint, offsetBelow: Float = 0f) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, x, y + offsetBelow, paint)
            }
        }

        // Draw Sun
        val sunHorizon = AstronomyUtils.toHorizon(sunPos.ra, sunPos.dec, lat, lon, lst)
        val sunScreenPos = projectToScreen(sunHorizon.az, sunHorizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
        if (sunScreenPos != null) {
            drawCircle(
                color = Color.Yellow,
                radius = 30f,
                center = sunScreenPos
            )
            drawSkyText(
                "SUN",
                sunScreenPos.x,
                sunScreenPos.y,
                Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 50f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                },
                offsetBelow = 60f
            )
        }

        // Draw Cardinal Directions
        val directions = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
        directions.forEach { (label, az) ->
            val screenPos = projectToScreen(az, 0.0, rotationMatrix, centerX, centerY, scaleX, scaleY)
            if (screenPos != null) {
                drawSkyText(
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

        // Draw Stars
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

                if (star.magnitude < 4.0f && star.commonName != null) {
                    drawSkyText(
                        star.commonName!!,
                        screenPos.x,
                        screenPos.y,
                        Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 30f
                            textAlign = Paint.Align.CENTER
                            alpha = (starAlpha * 255).toInt()
                        },
                        offsetBelow = starSize + 20f
                    )
                }
            }
        }

        constellationLabels.clear()
        drawConstellations(stars, lat, lon, lst, rotationMatrix, centerX, centerY, scaleX, scaleY) { name, x, y ->
            constellationLabels.add(ConstellationLabel(name, x, y))
            drawSkyText(
                name.uppercase(),
                x,
                y,
                Paint().apply {
                    color = android.graphics.Color.CYAN
                    textSize = 45f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    alpha = 180
                }
            )
        }
    }
}

data class ConstellationLabel(val name: String, val x: Float, val y: Float)

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
    
    val worldX = cos(altRad) * sin(azRad) // East
    val worldY = sin(altRad)              // Up
    val worldZ = cos(altRad) * cos(azRad) // North

    val screenX_vec = rotationMatrix[0] * worldX + rotationMatrix[4] * worldY + rotationMatrix[8] * worldZ
    val screenY_vec = rotationMatrix[1] * worldX + rotationMatrix[5] * worldY + rotationMatrix[9] * worldZ
    val screenZ_vec = rotationMatrix[2] * worldX + rotationMatrix[6] * worldY + rotationMatrix[10] * worldZ

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
    scaleY: Float,
    onLabelReady: (String, Float, Float) -> Unit
) {
    val constellationGroups = listOf(
        "Ursa Major" to listOf(
            "Dubhe" to "Merak", "Merak" to "Phecda", "Phecda" to "Megrez",
            "Megrez" to "Alioth", "Alioth" to "Mizar", "Mizar" to "Alkaid", "Megrez" to "Dubhe"
        ),
        "Cassiopeia" to listOf(
            "Caph" to "Schedar", "Schedar" to "Tsih", "Tsih" to "Ruchbah", "Ruchbah" to "Segin"
        ),
        "Orion" to listOf(
            "Betelgeuse" to "Bellatrix", "Bellatrix" to "Rigel", "Rigel" to "Saiph", "Saiph" to "Betelgeuse",
            "Alnitak" to "Alnilam", "Alnilam" to "Mintaka"
        ),
        "Crux" to listOf(
            "Gacrux" to "Acrux", "Mimosa" to "Imai"
        ),
        "Leo" to listOf(
            "Regulus" to "Algieba", "Algieba" to "Zosma", "Zosma" to "Denebola", "Denebola" to "Regulus"
        )
    )

    constellationGroups.forEach { (name, connections) ->
        val points = mutableListOf<Offset>()
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
                    points.add(p1)
                    points.add(p2)
                }
            }
        }
        
        if (points.isNotEmpty()) {
            val avgX = points.map { it.x }.average().toFloat()
            val avgY = points.map { it.y }.average().toFloat()
            onLabelReady(name, avgX, avgY)
        }
    }
}
