package com.example.core

import kotlin.math.*

data class CelestialCoords(val ra: Double, val dec: Double) // RA in hours, Dec in degrees
data class HorizonCoords(val az: Double, val alt: Double)   // Degrees

object AstronomyUtils {
    /**
     * Converts Right Ascension and Declination to Local Horizon Coordinates (Alt/Az).
     * ra: Right Ascension in decimal hours
     * dec: Declination in decimal degrees
     * lat: Observer latitude in degrees
     * lon: Observer longitude in degrees
     * lst: Local Sidereal Time in decimal hours
     */
    fun toHorizon(ra: Double, dec: Double, lat: Double, lon: Double, lst: Double): HorizonCoords {
        val raRad = Math.toRadians(ra * 15.0)
        val decRad = Math.toRadians(dec)
        val latRad = Math.toRadians(lat)
        val lstRad = Math.toRadians(lst * 15.0)

        val hourAngleRad = lstRad - raRad

        val altRad = asin(sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(hourAngleRad))
        val azRad = atan2(
            -cos(decRad) * sin(hourAngleRad),
            sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(hourAngleRad)
        )

        return HorizonCoords(
            az = (Math.toDegrees(azRad) + 360.0) % 360.0,
            alt = Math.toDegrees(altRad)
        )
    }

    /**
     * Simple Local Sidereal Time approximation.
     */
    fun calculateLST(lon: Double): Double {
        val now = System.currentTimeMillis() / 1000.0
        val j2000 = 946728000.0 // Seconds since epoch for J2000
        val d = (now - j2000) / 86400.0
        val gst = (18.697374558 + 24.06570982441908 * d) % 24.0
        return (gst + lon / 15.0 + 24.0) % 24.0
    }

    /**
     * Calculates the approximate position of the Sun.
     * Returns RA in hours and Dec in degrees.
     */
    fun calculateSunPosition(): CelestialCoords {
        val now = System.currentTimeMillis() / 1000.0
        val j2000 = 946728000.0
        val d = (now - j2000) / 86400.0
        
        val l = (280.460 + 0.9856474 * d) % 360.0
        val g = Math.toRadians((357.528 + 0.9856003 * d) % 360.0)
        val lambda = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        
        val epsilon = Math.toRadians(23.439 - 0.0000004 * d)
        
        val raRad = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val decRad = asin(sin(epsilon) * sin(lambda))
        
        var ra = Math.toDegrees(raRad) / 15.0
        if (ra < 0) ra += 24.0
        
        return CelestialCoords(ra, Math.toDegrees(decRad))
    }
}
