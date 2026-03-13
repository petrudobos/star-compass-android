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

// ── Projection Constants ─────────────────────────────────────────────────────
private const val FOV_Y = 60f
private const val PROJECTION_SCALE = 50f

// ── Sun Icon Constants ───────────────────────────────────────────────────────
private const val SUN_ICON_RADIUS = 28f
private const val SUN_GLOW_RADIUS_FACTOR = 1.8f
private const val SUN_RAY_LENGTH_FACTOR = 0.7f
private const val SUN_RAY_START_FACTOR = 1.15f
private const val SUN_RAY_COUNT = 8
private const val SUN_RAY_STROKE_WIDTH = 3f
private const val SUN_LABEL_TEXT_SIZE = 46f
private const val SUN_LABEL_OFFSET = 60f
private val SUN_COLOR = Color(0xFFFFDD00)
private val SUN_GLOW_COLOR = Color(0xFFFFDD44)

// ── Moon Icon Constants ──────────────────────────────────────────────────────
private const val MOON_ICON_RADIUS = 24f
private const val MOON_GLOW_RADIUS_FACTOR = 1.6f
private const val MOON_LABEL_TEXT_SIZE = 38f
private const val MOON_LABEL_OFFSET = 56f
private const val MOON_FULL_THRESHOLD = 0.85f
private const val MOON_CRATER_RADIUS_FACTOR = 0.45f
private const val MOON_CRATER_OFFSET_FACTOR = 0.2f
private const val MOON_OUTLINE_ALPHA = 0.5f
private const val MOON_OUTLINE_STROKE_WIDTH = 1.5f
private val MOON_COLOR = Color(0xFFD8D8F0)
private val MOON_DARK_COLOR = Color(0xFF0A0A1A)

// ── Cardinal Direction Constants ─────────────────────────────────────────────
private const val CARDINAL_TEXT_SIZE = 60f
private val CARDINAL_COLOR = android.graphics.Color.RED

// ── Star Constants ───────────────────────────────────────────────────────────
private const val STAR_MIN_SIZE = 3f
private const val STAR_MAX_SIZE = 10f
private const val STAR_SIZE_SCALE = 1.5f
private const val STAR_MIN_ALPHA = 0.2f
private const val STAR_MAX_ALPHA = 1.0f
private const val STAR_LABEL_MAGNITUDE_THRESHOLD = 4.0f
private const val STAR_LABEL_TEXT_SIZE = 30f
private const val STAR_LABEL_OFFSET_FACTOR = 20f

// ── Constellation Constants ──────────────────────────────────────────────────
private const val CONSTELLATION_LINE_ALPHA = 0.4f
private const val CONSTELLATION_LINE_WIDTH = 2f
private const val CONSTELLATION_LABEL_TEXT_SIZE = 45f
private const val CONSTELLATION_LABEL_ALPHA = 180
private val CONSTELLATION_COLOR = Color.Cyan

// ── UI Constants ─────────────────────────────────────────────────────────────
private const val TAP_HITBOX_PADDING = 100f
private const val TAP_HITBOX_HEIGHT_PADDING = 50f

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
                        if (offset.x in (label.x - TAP_HITBOX_PADDING)..(label.x + TAP_HITBOX_PADDING) &&
                            offset.y in (label.y - TAP_HITBOX_HEIGHT_PADDING)..(label.y + TAP_HITBOX_HEIGHT_PADDING)
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

        val fovY = FOV_Y
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
            drawSunIcon(sunScreen, radius = SUN_ICON_RADIUS)
            drawSkyText(
                "SUN", sunScreen.x, sunScreen.y,
                Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = SUN_LABEL_TEXT_SIZE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                },
                offsetBelow = SUN_LABEL_OFFSET
            )
        }

        // ── Moon ─────────────────────────────────────────────────────────────
        val moonHorizon = AstronomyUtils.toHorizon(moonPos.ra, moonPos.dec, lat, lon, lst)
        val moonScreen  = projectToScreen(moonHorizon.az, moonHorizon.alt, rotationMatrix, centerX, centerY, scaleX, scaleY)
        if (moonScreen != null) {
            drawMoonIcon(moonScreen, radius = MOON_ICON_RADIUS, illumination = moonIllumination)
            drawSkyText(
                "MOON", moonScreen.x, moonScreen.y,
                Paint().apply {
                    color = android.graphics.Color.argb(255, 200, 200, 220)
                    textSize = MOON_LABEL_TEXT_SIZE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                },
                offsetBelow = MOON_LABEL_OFFSET
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
                        color = CARDINAL_COLOR
                        textSize = CARDINAL_TEXT_SIZE
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
                val starSize  = max(STAR_MIN_SIZE, STAR_MAX_SIZE - star.magnitude * STAR_SIZE_SCALE)
                val starAlpha = (1.0f - (star.magnitude / 6.0f)).coerceIn(STAR_MIN_ALPHA, STAR_MAX_ALPHA)
                drawCircle(color = Color.White.copy(alpha = starAlpha), radius = starSize, center = screenPos)
                if (star.magnitude < STAR_LABEL_MAGNITUDE_THRESHOLD && star.commonName != null) {
                    drawSkyText(
                        star.commonName!!, screenPos.x, screenPos.y,
                        Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = STAR_LABEL_TEXT_SIZE
                            textAlign = Paint.Align.CENTER
                            alpha = (starAlpha * 255).toInt()
                        },
                        offsetBelow = starSize + STAR_LABEL_OFFSET_FACTOR
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
                    textSize = CONSTELLATION_LABEL_TEXT_SIZE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    alpha = CONSTELLATION_LABEL_ALPHA
                }
            )
        }
    }
}

// ── Sun icon: filled yellow disc + radiating spikes ──────────────────────────
private fun DrawScope.drawSunIcon(center: Offset, radius: Float) {
    // Glow
    drawCircle(color = SUN_GLOW_COLOR.copy(alpha = 0.25f), radius = radius * SUN_GLOW_RADIUS_FACTOR, center = center)
    // Disc
    drawCircle(color = SUN_COLOR, radius = radius, center = center)
    // Rays
    val rayLen  = radius * SUN_RAY_LENGTH_FACTOR
    val rayStart = radius * SUN_RAY_START_FACTOR
    for (i in 0 until SUN_RAY_COUNT) {
        val angle = Math.toRadians(i * 45.0)
        val sx = center.x + (rayStart * cos(angle)).toFloat()
        val sy = center.y + (rayStart * sin(angle)).toFloat()
        val ex = center.x + ((rayStart + rayLen) * cos(angle)).toFloat()
        val ey = center.y + ((rayStart + rayLen) * sin(angle)).toFloat()
        drawLine(color = SUN_COLOR, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = SUN_RAY_STROKE_WIDTH)
    }
}

// ── Moon icon: crescent shape using two overlapping circles ──────────────────
// illumination: 0.0 = new moon (dark), 1.0 = full moon (bright)
private fun DrawScope.drawMoonIcon(center: Offset, radius: Float, illumination: Float) {
    // Soft glow
    drawCircle(color = MOON_COLOR.copy(alpha = 0.15f * illumination), radius = radius * MOON_GLOW_RADIUS_FACTOR, center = center)

    if (illumination > MOON_FULL_THRESHOLD) {
        // Full moon: solid bright disc
        drawCircle(color = MOON_COLOR, radius = radius, center = center)
        // Subtle crater-like ring
        drawCircle(color = Color.Black.copy(alpha = 0.08f), radius = radius * MOON_CRATER_RADIUS_FACTOR,
            center = Offset(center.x - radius * MOON_CRATER_OFFSET_FACTOR, center.y - radius * MOON_CRATER_OFFSET_FACTOR))
    } else {
        // Crescent: outer disc clipped by inner dark disc offset to one side
        // Draw outer (bright) circle
        drawCircle(color = MOON_COLOR.copy(alpha = 0.9f), radius = radius, center = center)
        // Overlay dark circle shifted to simulate shadow
        // Shadow offset: shifts more as illumination decreases
        val shadowShift = radius * (1f - illumination) * 1.4f
        drawCircle(
            color = MOON_DARK_COLOR,  // near-black to match sky background
            radius = radius * 1.05f,
            center = Offset(center.x + shadowShift, center.y)
        )
        // Thin outline to keep crescent visible against bright backgrounds
        drawCircle(
            color = MOON_COLOR.copy(alpha = MOON_OUTLINE_ALPHA),
            radius = radius,
            center = center,
            style = Stroke(width = MOON_OUTLINE_STROKE_WIDTH)
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
 *   worldZ = Up    = -sin(alt)               [negated to invert altitude axis]
 *
 * Screen projection:
 *   screenY uses ADDITION to make pitch match phone tilt direction.
 *   The worldZ negation inverts the vertical axis (above ↔ below horizon).
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
    // Altitude fix: negate worldZ (inverts vertical axis)
    val worldX = -(sin(azRad) * cos(altRad))  // East  (negated = fixes E/W mirror)
    val worldY = -(cos(azRad) * cos(altRad))  // North (negated = fixes N/S swap)
    val worldZ = -sin(altRad)                 // Up    (negated = inverts altitude axis)

    val screenX_vec = (rotationMatrix[0] * worldX + rotationMatrix[1] * worldY + rotationMatrix[2] * worldZ).toFloat()
    val screenY_vec = (rotationMatrix[4] * worldX + rotationMatrix[5] * worldY + rotationMatrix[6] * worldZ).toFloat()
    val screenZ_vec = (rotationMatrix[8] * worldX + rotationMatrix[9] * worldY + rotationMatrix[10] * worldZ).toFloat()

    if (screenZ_vec > 0) {
        val screenX = centerX + (screenX_vec / screenZ_vec) * scaleX * PROJECTION_SCALE
        val screenY = centerY + (screenY_vec / screenZ_vec) * scaleY * PROJECTION_SCALE  // ← FIXED: addition makes pitch match tilt
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
                    drawLine(color = CONSTELLATION_COLOR.copy(alpha = CONSTELLATION_LINE_ALPHA), start = p1, end = p2, strokeWidth = CONSTELLATION_LINE_WIDTH)
                    points.add(p1); points.add(p2)
                }
            }
        }
        if (points.isNotEmpty()) {
            onLabelReady(name, points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        }
    }
}
