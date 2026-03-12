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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    // Recompute celestial positions every recomposition (driven by sensor updates ~60fps,
    // but LST/sun/moon positions change slowly — no extra cost).
    val lst = AstronomyUtils.calculateLST(lon)
    val sunPos  = AstronomyUtils.calculateSunPosition()
    val moonPos = AstronomyUtils.calculateMoonPosition()
    val moonIllumination = AstronomyUtils.moonPhase()

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
        val width  = size.width
        val height = size.height
        val centerX = width  / 2f
        val centerY = height / 2f

        val fovY = 60f
        val aspectRatio = width / height
        val fovX = fovY * aspectRatio
        val scaleX = width  / fovX
        val scaleY = height / fovY

        if (isDarkMode) drawRect(color = Color.Black.copy(alpha = 0.7f))

        fun drawSkyText(text: String, x: Float, y: Float, paint: Paint, offsetBelow: Float = 0f) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, x, y + offsetBelow, paint)
            }
        }

        // ── Sun ──────────────────────────────────────────────────────────────
        val sunHorizon = AstronomyUtils.toHorizon(sunPos.ra, sunPos.dec, lat, lon, lst)
        val sunScreen  = projectToScreen(sunHorizon.az, sunHorizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
        if (sunScreen != null) {
            drawSunIcon(sunScreen, radius = 28f)
            drawSkyText(
                "SUN", sunScreen.x, sunScreen.y,
                Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 46f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                },
                offsetBelow = 60f
            )
        }

        // ── Moon ─────────────────────────────────────────────────────────────
        val moonHorizon = AstronomyUtils.toHorizon(moonPos.ra, moonPos.dec, lat, lon, lst)
        val moonScreen  = projectToScreen(moonHorizon.az, moonHorizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
        if (moonScreen != null) {
            drawMoonIcon(moonScreen, radius = 24f, illumination = moonIllumination)
            drawSkyText(
                "MOON", moonScreen.x, moonScreen.y,
                Paint().apply {
                    color = android.graphics.Color.argb(255, 200, 200, 220)
                    textSize = 38f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                },
                offsetBelow = 56f
            )
        }

        // ── Cardinal Directions ───────────────────────────────────────────────
        val directions = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
        directions.forEach { (label, az) ->
            val screenPos = projectToScreen(az, 0.0, rotationMatrix, centerX, centerY, scaleX, scaleY)
            if (screenPos != null) {
                drawSkyText(
                    label, screenPos.x, screenPos.y,
                    Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 60f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                )
            }
        }

        // ── Stars ─────────────────────────────────────────────────────────────
        stars.forEach { star ->
            val horizon   = AstronomyUtils.toHorizon(star.ra, star.dec, lat, lon, lst)
            val screenPos = projectToScreen(horizon.az, horizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
            if (screenPos != null) {
                val starSize  = max(3f, 10f - star.magnitude * 1.5f)
                val starAlpha = (1.0f - (star.magnitude / 6.0f)).coerceIn(0.2f, 1.0f)
                drawCircle(color = Color.White.copy(alpha = starAlpha), radius = starSize, center = screenPos)
                if (star.magnitude < 4.0f && star.commonName != null) {
                    drawSkyText(
                        star.commonName!!, screenPos.x, screenPos.y,
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

        // ── Constellations ────────────────────────────────────────────────────
        constellationLabels.clear()
        drawConstellations(stars, lat, lon, lst, rotationMatrix, centerX, centerY, scaleX, scaleY) { name, x, y ->
            constellationLabels.add(ConstellationLabel(name, x, y))
            drawSkyText(
                name.uppercase(), x, y,
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

// ── Sun icon: filled yellow disc + radiating spikes ──────────────────────────
private fun DrawScope.drawSunIcon(center: Offset, radius: Float) {
    // Glow
    drawCircle(color = Color(0xFFFFDD44).copy(alpha = 0.25f), radius = radius * 1.8f, center = center)
    // Disc
    drawCircle(color = Color(0xFFFFDD00), radius = radius, center = center)
    // Rays
    val rayLen  = radius * 0.7f
    val rayStart = radius * 1.15f
    val strokePaint = Stroke(width = 3f)
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val sx = center.x + (rayStart * cos(angle)).toFloat()
        val sy = center.y + (rayStart * sin(angle)).toFloat()
        val ex = center.x + ((rayStart + rayLen) * cos(angle)).toFloat()
        val ey = center.y + ((rayStart + rayLen) * sin(angle)).toFloat()
        drawLine(color = Color(0xFFFFDD00), start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 3f)
    }
}

// ── Moon icon: crescent shape using two overlapping circles ──────────────────
// illumination: 0.0 = new moon (dark), 1.0 = full moon (bright)
private fun DrawScope.drawMoonIcon(center: Offset, radius: Float, illumination: Float) {
    val moonColor = Color(0xFFD8D8F0)

    // Soft glow
    drawCircle(color = moonColor.copy(alpha = 0.15f * illumination), radius = radius * 1.6f, center = center)

    if (illumination > 0.85f) {
        // Full moon: solid bright disc
        drawCircle(color = moonColor, radius = radius, center = center)
        // Subtle crater-like ring
        drawCircle(color = Color.Black.copy(alpha = 0.08f), radius = radius * 0.45f,
            center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f))
    } else {
        // Crescent: outer disc clipped by inner dark disc offset to one side
        // Draw outer (bright) circle
        drawCircle(color = moonColor.copy(alpha = 0.9f), radius = radius, center = center)
        // Overlay dark circle shifted to simulate shadow
        // Shadow offset: shifts more as illumination decreases
        val shadowShift = radius * (1f - illumination) * 1.4f
        drawCircle(
            color = Color(0xFF0A0A1A),  // near-black to match sky background
            radius = radius * 1.05f,
            center = Offset(center.x + shadowShift, center.y)
        )
        // Thin outline to keep crescent visible against bright backgrounds
        drawCircle(
            color = moonColor.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

data class ConstellationLabel(val name: String, val x: Float, val y: Float)

/**
 * Projects a celestial position (azimuth, altitude) to screen coordinates.
 *
 * World coordinate system (horizon):
 *   worldX = East  = -(sin(az) * cos(alt))   [negated to fix E/W mirror]
 *   worldY = North = -(cos(az) * cos(alt))   [negated to fix N/S swap]
 *   worldZ = Up    =   sin(alt)
 *
 * Screen projection:
 *   screenY uses SUBTRACTION (standard computer graphics Y-down convention).
 *   The worldY negation is what fixed the N/S compass swap.
 */
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
    val azRad  = Math.toRadians(az)

    // E/W already correct (worldX negated from previous fix)
    // N/S fix: negate worldY (this alone fixes the N/S swap)
    val worldX = -(sin(azRad) * cos(altRad))  // East  (negated = fixes E/W mirror)
    val worldY = -(cos(azRad) * cos(altRad))  // North (negated = fixes N/S swap)
    val worldZ =   sin(altRad)               // Up

    val screenX_vec = (rotationMatrix[0] * worldX + rotationMatrix[1] * worldY + rotationMatrix[2] * worldZ).toFloat()
    val screenY_vec = (rotationMatrix[4] * worldX + rotationMatrix[5] * worldY + rotationMatrix[6] * worldZ).toFloat()
    val screenZ_vec = (rotationMatrix[8] * worldX + rotationMatrix[9] * worldY + rotationMatrix[10] * worldZ).toFloat()

    if (screenZ_vec > 0) {
        val screenX = centerX + (screenX_vec / screenZ_vec) * scaleX * 50f
        val screenY = centerY - (screenY_vec / screenZ_vec) * scaleY * 50f  // ← CORRECTED: back to subtraction
        return Offset(screenX, screenY)
    }
    return null
}

private fun DrawScope.drawConstellations(
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
                    drawLine(color = Color.Cyan.copy(alpha = 0.4f), start = p1, end = p2, strokeWidth = 2f)
                    points.add(p1); points.add(p2)
                }
            }
        }
        if (points.isNotEmpty()) {
            onLabelReady(name, points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        }
    }
}
