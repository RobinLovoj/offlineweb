package com.lovoj.androidoffline.Offlinewebview

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val json: String // Store the whole product object as JSON
) 