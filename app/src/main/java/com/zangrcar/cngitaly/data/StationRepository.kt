package com.zangrcar.cngitaly.data

import android.util.Log
import com.zangrcar.cngitaly.data.local.CngPriceEntity
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import com.zangrcar.cngitaly.data.local.StationDao
import com.zangrcar.cngitaly.data.local.StationEntity
import com.zangrcar.cngitaly.data.mimit.MimitDownloader
import kotlinx.coroutines.flow.Flow

class StationRepository(
    private val dao: StationDao,
    private val downloader: MimitDownloader = MimitDownloader()
) {
    val metadata: Flow<DatasetMetaEntity?> = dao.observeMeta()

    suspend fun hasLocalData(): Boolean = dao.getMeta() != null

    suspend fun getStoredStationCounts(): Pair<Int?, Int> =
        dao.getMeta()?.stationCount to dao.getStationCount()

    suspend fun getStationsInBounds(bounds: MapBounds): List<MapStation> {
        val roomStations = dao.getStationsInBounds(
            north = bounds.north,
            south = bounds.south,
            east = bounds.east,
            west = bounds.west
        )
        Log.d("CngMap", "Room result: stations=${roomStations.size}")
        return roomStations.mapNotNull { it.toMapStation() }
    }

    suspend fun refresh() {
        val snapshot = downloader.downloadSnapshot()
        require(snapshot.stations.isNotEmpty()) { "No usable CNG stations in MIMIT snapshot" }

        val stations = snapshot.stations.map {
            StationEntity(
                id = it.id,
                manager = it.manager,
                brand = it.brand,
                stationType = it.stationType,
                name = it.name,
                address = it.address,
                municipality = it.municipality,
                province = it.province,
                latitude = it.latitude,
                longitude = it.longitude
            )
        }
        val prices = snapshot.prices.map {
            CngPriceEntity(
                stationId = it.stationId,
                fuelName = it.fuelName,
                isSelf = it.isSelf,
                price = it.price,
                communicatedAtEpochMillis = it.communicatedAtEpochMillis
            )
        }
        val meta = DatasetMetaEntity(
            stationDatasetDate = snapshot.stationDatasetDate,
            priceDatasetDate = snapshot.priceDatasetDate,
            lastSuccessfulRefreshEpochMillis = System.currentTimeMillis(),
            stationCount = stations.size
        )
        dao.replaceSnapshot(stations, prices, meta)
    }
}
