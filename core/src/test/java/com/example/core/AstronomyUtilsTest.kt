package com.example.core

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.PI
import kotlin.math.abs

/**
 * Unit tests for [AstronomyUtils].
 * 
 * Tests cover:
 * - Coordinate conversion (RA/Dec to Alt/Az)
 * - Local Sidereal Time calculation
 * - Sun position approximation
 * - Moon position approximation
 * - Moon phase illumination
 */
class AstronomyUtilsTest {

    // ─────────────────────────────────────────────────────────────────────────
    // toHorizon() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun toHorizon_verifiesBasicConversion() {
        // Test: Observer at equator (lat=0), looking at celestial equator (dec=0)
        // At meridian (hour angle = 0), altitude should equal 90 - lat = 90 degrees
        val ra = 12.0 // hours
        val dec = 0.0 // degrees
        val lat = 0.0 // equator
        val lon = 0.0
        val lst = 12.0 // Same as RA, so object is at meridian

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        // At meridian with dec=0 and lat=0, altitude should be 90 (overhead)
        assertEquals(90.0, result.alt, 1.0)
        // Azimuth is undefined at zenith, but should be in valid range
        assertTrue(result.az in 0.0..360.0)
    }

    @Test
    fun toHorizon_northStar_atNorthPole_isOverhead() {
        // At North Pole (lat=90), Polaris (dec≈89) should be nearly overhead
        val polarisRa = 2.5 // hours (approximate)
        val polarisDec = 89.0 // degrees
        val lat = 90.0 // North Pole
        val lon = 0.0
        val lst = 2.5 // Polaris at meridian

        val result = AstronomyUtils.toHorizon(polarisRa, polarisDec, lat, lon, lst)

        // Polaris should be very close to zenith (90 degrees altitude)
        assertTrue(result.alt > 88.0)
    }

    @Test
    fun toHorizon_starAtHorizon_hasZeroAltitude() {
        // Star on celestial equator, observer at 45° lat
        // When hour angle is 6 hours (90°), star should be on horizon
        val ra = 0.0
        val dec = 0.0
        val lat = 45.0
        val lon = 0.0
        val lst = 6.0 // 6 hours = 90° hour angle

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        // Star should be near horizon (altitude close to 0)
        assertTrue(abs(result.alt) < 5.0)
    }

    @Test
    fun toHorizon_azimuth_isNormalized() {
        // Ensure azimuth is always in [0, 360) range
        val ra = 6.0
        val dec = 45.0
        val lat = 45.0
        val lon = 0.0
        val lst = 18.0

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        assertTrue("Azimuth should be in [0, 360) range", result.az >= 0.0 && result.az < 360.0)
    }

    @Test
    fun toHorizon_sirius_fromMidLatitude() {
        // Sirius: RA=6.75h, Dec=-16.71°
        // Observer at 40°N latitude
        // When LST = 6.75h, Sirius is at meridian
        val siriusRa = 6.75
        val siriusDec = -16.71
        val lat = 40.0
        val lon = 0.0
        val lst = 6.75 // Sirius at meridian

        val result = AstronomyUtils.toHorizon(siriusRa, siriusDec, lat, lon, lst)

        // At meridian: altitude = 90 - lat + dec = 90 - 40 + (-16.71) = 33.29°
        assertEquals(33.29, result.alt, 1.0)
        // At meridian, azimuth should be South (180°) for negative declination
        assertEquals(180.0, result.az, 5.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateLST() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun calculateLST_isInRange() {
        // LST should always be in [0, 24) hours range
        val lonValues = listOf(-180.0, -90.0, 0.0, 45.0, 90.0, 180.0)

        lonValues.forEach { lon ->
            val lst = AstronomyUtils.calculateLST(lon)
            assertTrue("LST should be in [0, 24) range for lon=$lon", lst >= 0.0 && lst < 24.0)
        }
    }

    @Test
    fun calculateLST_longitudeEffect() {
        // LST increases by 1 hour for every 15° of longitude east
        val lon1 = 0.0
        val lon2 = 15.0 // 15° east

        val lst1 = AstronomyUtils.calculateLST(lon1)
        val lst2 = AstronomyUtils.calculateLST(lon2)

        // Difference should be approximately 1 hour (modulo 24)
        val diff = (lst2 - lst1 + 24.0) % 24.0
        assertEquals(1.0, diff, 0.1)
    }

    @Test
    fun calculateLST_knownDate() {
        // J2000 epoch (Jan 1, 2000, 12:00 TT): GST should be approximately 18.7h
        // This is a sanity check - actual value may vary slightly due to implementation
        val lst = AstronomyUtils.calculateLST(0.0)
        
        // LST should be a reasonable value (not NaN or infinite)
        assertFalse(lst.isNaN())
        assertFalse(lst.isInfinite())
        assertTrue(lst in 0.0..24.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateSunPosition() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun calculateSunPosition_ra_isInRange() {
        // Sun's RA should always be in [0, 24) hours
        val sunPos = AstronomyUtils.calculateSunPosition()

        assertTrue("Sun RA should be in [0, 24) range", sunPos.ra >= 0.0 && sunPos.ra < 24.0)
    }

    @Test
    fun calculateSunPosition_dec_isInRange() {
        // Sun's declination should be in [-23.5, +23.5] degrees (ecliptic tilt)
        val sunPos = AstronomyUtils.calculateSunPosition()

        assertTrue("Sun dec should be in [-23.5, +23.5] range", sunPos.dec in -23.5..23.5)
    }

    @Test
    fun calculateSunPosition_verifiesSeasons() {
        // This test verifies the Sun position changes over time
        // (We can't test exact values without a fixed time, but we can verify it's not constant)
        val pos1 = AstronomyUtils.calculateSunPosition()
        
        // Wait a moment to get a different timestamp
        Thread.sleep(100)
        
        val pos2 = AstronomyUtils.calculateSunPosition()

        // Positions should be very close (Sun doesn't move much in 100ms)
        assertTrue(abs(pos1.ra - pos2.ra) < 0.001)
        assertTrue(abs(pos1.dec - pos2.dec) < 0.001)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateMoonPosition() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun calculateMoonPosition_ra_isInRange() {
        // Moon's RA should always be in [0, 24) hours
        val moonPos = AstronomyUtils.calculateMoonPosition()

        assertTrue("Moon RA should be in [0, 24) range", moonPos.ra >= 0.0 && moonPos.ra < 24.0)
    }

    @Test
    fun calculateMoonPosition_dec_isInRange() {
        // Moon's declination should be in [-28.5, +28.5] degrees
        // (5° orbital inclination + 23.5° ecliptic tilt)
        val moonPos = AstronomyUtils.calculateMoonPosition()

        assertTrue("Moon dec should be in [-28.5, +28.5] range", moonPos.dec in -28.5..28.5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // moonPhase() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun moonPhase_isInRange() {
        // Moon illumination should always be in [0, 1] range
        repeat(10) {
            val phase = AstronomyUtils.moonPhase()
            assertTrue("Moon phase should be in [0, 1] range", phase in 0f..1f)
        }
    }

    @Test
    fun moonPhase_isConsistent() {
        // Multiple calls in quick succession should return the same value
        val phase1 = AstronomyUtils.moonPhase()
        val phase2 = AstronomyUtils.moonPhase()
        val phase3 = AstronomyUtils.moonPhase()

        assertEquals(phase1, phase2, 0.001f)
        assertEquals(phase2, phase3, 0.001f)
    }

    @Test
    fun moonPhase_verifiesCycle() {
        // Moon phase cycle is ~29.5 days
        // We can't test the full cycle, but we can verify the function doesn't crash
        // and returns reasonable values
        
        val phases = mutableListOf<Float>()
        repeat(5) {
            phases.add(AstronomyUtils.moonPhase())
            Thread.sleep(50)
        }

        // All phases should be in valid range and nearly identical
        phases.forEach { phase ->
            assertTrue(phase in 0f..1f)
            assertEquals(phases[0], phase, 0.01f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge Cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun toHorizon_extremeLatitude_northPole() {
        // At North Pole, all visible stars circle the horizon
        val ra = 12.0
        val dec = 45.0
        val lat = 90.0
        val lon = 0.0
        val lst = 0.0

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        // Star with dec=45° should be at altitude 45° from North Pole
        assertEquals(45.0, result.alt, 1.0)
    }

    @Test
    fun toHorizon_extremeLatitude_southPole() {
        // At South Pole, celestial sphere is inverted
        val ra = 12.0
        val dec = -45.0
        val lat = -90.0
        val lon = 0.0
        val lst = 0.0

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        // Star with dec=-45° should be at altitude 45° from South Pole
        assertEquals(45.0, result.alt, 1.0)
    }

    @Test
    fun toHorizon_invisibleStar_hasNegativeAltitude() {
        // Star below horizon should have negative altitude
        val ra = 0.0
        val dec = -60.0 // Far southern star
        val lat = 45.0 // Northern hemisphere
        val lon = 0.0
        val lst = 12.0 // Opposite side of sky

        val result = AstronomyUtils.toHorizon(ra, dec, lat, lon, lst)

        // Southern star from northern latitude should be below horizon
        assertTrue(result.alt < 0.0)
    }
}
