package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stars")
data class Star(
    @PrimaryKey val id: Int,
    val ra: Double,        // Decimal hours
    val dec: Double,       // Decimal degrees
    val magnitude: Float,  // Brightness
    val commonName: String? = null,
    val constellation: String? = null
)
