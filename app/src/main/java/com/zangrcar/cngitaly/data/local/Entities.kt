package com.zangrcar.cngitaly.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import androidx.room.Relation

@Entity(
    tableName = "stations",
    indices = [Index("latitude"), Index("longitude")]
)
data class StationEntity(
    @androidx.room.PrimaryKey val id: Int,
    val manager: String?,
    val brand: String?,
    val stationType: String?,
    val name: String,
    val address: String?,
    val municipality: String?,
    val province: String?,
    val latitude: Double,
    val longitude: Double
)

@Entity(
    tableName = "cng_prices",
    primaryKeys = ["stationId", "fuelName", "isSelf"],
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stationId")]
)
data class CngPriceEntity(
    val stationId: Int,
    val fuelName: String,
    val isSelf: Boolean,
    val price: Double,
    val communicatedAtEpochMillis: Long?
)

@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val stationDatasetDate: String?,
    val priceDatasetDate: String?,
    val lastSuccessfulRefreshEpochMillis: Long,
    val stationCount: Int
)

data class StationWithPrices(
    @Embedded val station: StationEntity,
    @Relation(parentColumn = "id", entityColumn = "stationId")
    val prices: List<CngPriceEntity>
)
