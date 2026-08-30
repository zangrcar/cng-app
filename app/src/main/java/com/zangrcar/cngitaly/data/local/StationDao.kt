package com.zangrcar.cngitaly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM dataset_meta WHERE id = 1")
    fun observeMeta(): Flow<DatasetMetaEntity?>

    @Query("SELECT * FROM dataset_meta WHERE id = 1")
    suspend fun getMeta(): DatasetMetaEntity?

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Transaction
    @Query(
        """
        SELECT * FROM stations
        WHERE latitude BETWEEN :south AND :north
          AND (
            (:west <= :east AND longitude BETWEEN :west AND :east)
            OR
            (:west > :east AND (longitude >= :west OR longitude <= :east))
          )
        """
    )
    suspend fun getStationsInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double
    ): List<StationWithPrices>

    @Insert
    suspend fun insertStations(stations: List<StationEntity>)

    @Insert
    suspend fun insertPrices(prices: List<CngPriceEntity>)

    @Insert
    suspend fun insertMeta(meta: DatasetMetaEntity)

    @Query("DELETE FROM cng_prices")
    suspend fun deletePrices()

    @Query("DELETE FROM stations")
    suspend fun deleteStations()

    @Query("DELETE FROM dataset_meta")
    suspend fun deleteMeta()

    @Transaction
    suspend fun replaceSnapshot(
        stations: List<StationEntity>,
        prices: List<CngPriceEntity>,
        meta: DatasetMetaEntity
    ) {
        deletePrices()
        deleteStations()
        deleteMeta()
        insertStations(stations)
        insertPrices(prices)
        insertMeta(meta)
    }
}
