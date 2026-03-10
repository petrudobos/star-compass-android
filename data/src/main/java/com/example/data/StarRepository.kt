package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StarRepository(private val context: Context) {
    private val starDao = StarDatabase.getDatabase(context).starDao()

    fun getAllStars(): Flow<List<Star>> = starDao.getAllStars()

    suspend fun seedDatabase() = withContext(Dispatchers.IO) {
        val count = starDao.getCount()
        if (count == 0) {
            val initialStars = mutableListOf(
                Star(1, 6.75, -16.71, -1.46f, "Sirius", "Canis Major"),
                Star(2, 18.61, 38.78, 0.03f, "Vega", "Lyra"),
                Star(3, 20.69, 45.28, 1.25f, "Deneb", "Cygnus"),
                Star(4, 5.24, -8.20, 0.12f, "Rigel", "Orion"),
                Star(5, 5.91, 7.40, 0.42f, "Betelgeuse", "Orion"),
                Star(6, 14.26, 19.17, -0.05f, "Arcturus", "Boötes"),
                Star(7, 4.59, 16.51, 0.85f, "Aldebaran", "Taurus"),
                Star(8, 7.65, 28.02, 1.14f, "Pollux", "Gemini"),
                Star(9, 7.57, 31.88, 1.58f, "Castor", "Gemini"),
                Star(10, 10.14, 11.97, 1.35f, "Regulus", "Leo"),
                Star(11, 13.42, -11.16, 0.98f, "Spica", "Virgo"),
                Star(12, 16.49, -26.43, 0.96f, "Antares", "Scorpius"),
                Star(13, 19.85, 8.87, 0.77f, "Altair", "Aquila"),
                Star(14, 22.96, -29.62, 1.16f, "Fomalhaut", "Piscis Austrinus"),
                Star(15, 5.28, 46.00, 0.08f, "Capella", "Auriga"),
                Star(16, 2.32, 89.26, 1.97f, "Polaris", "Ursa Minor"),
                
                // Big Dipper (Ursa Major)
                Star(17, 11.06, 61.75, 1.81f, "Dubhe", "Ursa Major"),
                Star(18, 11.03, 56.38, 2.34f, "Merak", "Ursa Major"),
                Star(19, 11.89, 53.69, 2.41f, "Phecda", "Ursa Major"),
                Star(20, 12.25, 57.03, 3.32f, "Megrez", "Ursa Major"),
                Star(21, 12.90, 55.96, 1.76f, "Alioth", "Ursa Major"),
                Star(22, 13.40, 54.93, 2.23f, "Mizar", "Ursa Major"),
                Star(23, 13.79, 49.31, 1.85f, "Alkaid", "Ursa Major"),

                // Cassiopeia
                Star(24, 0.67, 56.54, 2.24f, "Schedar", "Cassiopeia"),
                Star(25, 0.15, 59.15, 2.28f, "Caph", "Cassiopeia"),
                Star(26, 0.95, 60.72, 2.47f, "Tsih", "Cassiopeia"),
                Star(27, 1.43, 60.18, 2.68f, "Ruchbah", "Cassiopeia"),
                Star(28, 1.90, 63.67, 3.35f, "Segin", "Cassiopeia"),

                // Orion (rest of it)
                Star(29, 5.59, -5.39, 2.23f, "Saiph", "Orion"),
                Star(30, 5.42, 6.35, 1.64f, "Bellatrix", "Orion"),
                Star(31, 5.60, -1.20, 1.69f, "Alnilam", "Orion"),
                Star(32, 5.53, -0.30, 2.25f, "Alnitak", "Orion"),
                Star(33, 5.53, -0.18, 2.21f, "Mintaka", "Orion"),

                // Crux (Southern Cross) - for Southern Hemisphere
                Star(34, 12.44, -63.09, 0.77f, "Acrux", "Crux"),
                Star(35, 12.52, -59.68, 1.25f, "Mimosa", "Crux"),
                Star(36, 12.25, -57.11, 1.59f, "Gacrux", "Crux"),
                Star(37, 12.17, -58.75, 2.79f, "Imai", "Crux"),

                // Leo
                Star(38, 11.82, 14.57, 2.14f, "Denebola", "Leo"),
                Star(39, 10.33, 19.84, 2.01f, "Algieba", "Leo"),
                Star(40, 11.23, 20.52, 2.56f, "Zosma", "Leo")
            )
            starDao.insertStars(initialStars)
        }
    }
}
