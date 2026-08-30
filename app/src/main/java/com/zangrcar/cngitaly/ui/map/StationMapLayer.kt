package com.zangrcar.cngitaly.ui.map

import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import com.zangrcar.cngitaly.data.MapStation

class StationMapLayer(
    private val map: MapLibreMap
) : MapLibreMap.OnMapClickListener {
    init {
        map.addOnMapClickListener(this)
    }

    fun render(stations: List<MapStation>): Boolean {
        val style = map.style ?: return false
        val featureCollection = stations.toFeatureCollection()
        val geoJson = featureCollection.toJson()
        Log.d(LOG_TAG, "Recreating source: stations=${stations.size} jsonLength=${geoJson.length}")

        removeStationStyleObjects(style)
        style.addSource(
            GeoJsonSource(
                SOURCE_ID,
                geoJson,
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(50)
                    .withClusterMaxZoom(14)
            )
        )
        Log.d(LOG_TAG, "Source recreated: sourceExists=${style.getSource(SOURCE_ID) != null}")
        addStationLayers(style)
        Log.d(
            LOG_TAG,
            "Layers recreated: " +
                "cluster=${style.getLayer(CLUSTER_CIRCLE_LAYER_ID) != null} " +
                "count=${style.getLayer(CLUSTER_COUNT_LAYER_ID) != null} " +
                "station=${style.getLayer(STATION_CIRCLE_LAYER_ID) != null} " +
                "price=${style.getLayer(STATION_PRICE_LAYER_ID) != null}"
        )
        return true
    }

    private fun removeStationStyleObjects(style: Style) {
        listOf(
            CLUSTER_COUNT_LAYER_ID,
            CLUSTER_CIRCLE_LAYER_ID,
            STATION_PRICE_LAYER_ID,
            STATION_CIRCLE_LAYER_ID
        ).forEach { layerId ->
            style.getLayer(layerId)?.let(style::removeLayer)
        }
        style.getSource(SOURCE_ID)?.let(style::removeSource)
    }

    private fun addStationLayers(style: Style) {
        style.addLayer(
            CircleLayer(STATION_CIRCLE_LAYER_ID, SOURCE_ID)
                .withFilter(isNotCluster())
                .withProperties(
                    PropertyFactory.circleColor(Color.rgb(24, 52, 89)),
                    PropertyFactory.circleRadius(27f),
                    PropertyFactory.circleOpacity(1f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
        )
        style.addLayer(
            SymbolLayer(STATION_PRICE_LAYER_ID, SOURCE_ID)
                .withFilter(isNotCluster())
                .withProperties(
                    PropertyFactory.textField(Expression.get("priceLabel")),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor(Color.WHITE),
                    PropertyFactory.textHaloColor(Color.BLACK),
                    PropertyFactory.textHaloWidth(0.6f),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
        )
        style.addLayer(
            CircleLayer(CLUSTER_CIRCLE_LAYER_ID, SOURCE_ID)
                .withFilter(isCluster())
                .withProperties(
                    PropertyFactory.circleColor(Color.rgb(0, 92, 72)),
                    PropertyFactory.circleRadius(22f),
                    PropertyFactory.circleOpacity(1f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
        )
        style.addLayer(
            SymbolLayer(CLUSTER_COUNT_LAYER_ID, SOURCE_ID)
                .withFilter(isCluster())
                .withProperties(
                    PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textColor(Color.WHITE),
                    PropertyFactory.textHaloColor(Color.BLACK),
                    PropertyFactory.textHaloWidth(0.6f),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
        )
    }

    fun logRenderDiagnostics(width: Int, height: Int) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        val sourceFeatures = source?.querySourceFeatures(null).orEmpty()
        Log.d(LOG_TAG, "Source query: features=${sourceFeatures.size}")
        val renderedFeatures = if (width > 0 && height > 0) {
            map.queryRenderedFeatures(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                STATION_CIRCLE_LAYER_ID,
                STATION_PRICE_LAYER_ID,
                CLUSTER_CIRCLE_LAYER_ID,
                CLUSTER_COUNT_LAYER_ID
            )
        } else {
            emptyList()
        }
        Log.d(LOG_TAG, "Rendered query: features=${renderedFeatures.size}")
        logLayer(style, CLUSTER_CIRCLE_LAYER_ID)
        logLayer(style, CLUSTER_COUNT_LAYER_ID)
        logLayer(style, STATION_CIRCLE_LAYER_ID)
        logLayer(style, STATION_PRICE_LAYER_ID)
    }

    private fun logLayer(style: Style, layerId: String) {
        val layer = style.getLayer(layerId)
        val filter = when (layer) {
            is CircleLayer -> layer.filter
            is SymbolLayer -> layer.filter
            else -> null
        }
        Log.d(
            LOG_TAG,
            "Layer $layerId exists=${layer != null} " +
                "visibility=${layer?.visibility?.value} filter=$filter"
        )
    }

    override fun onMapClick(point: LatLng): Boolean {
        val cluster = map.queryRenderedFeatures(
            map.projection.toScreenLocation(point),
            CLUSTER_CIRCLE_LAYER_ID
        ).firstOrNull() ?: return false
        val clusterPoint = cluster.geometry() as? Point ?: return false
        val source = map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return false
        val expansionZoom = source.getClusterExpansionZoom(cluster)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(clusterPoint.latitude(), clusterPoint.longitude()),
                expansionZoom.toDouble()
            ),
            500
        )
        return true
    }

    fun destroy() {
        map.removeOnMapClickListener(this)
    }

    companion object {
        private const val LOG_TAG = "CngMap"
        private const val SOURCE_ID = "cng-stations"
        private const val CLUSTER_CIRCLE_LAYER_ID = "cng-clusters"
        private const val CLUSTER_COUNT_LAYER_ID = "cng-cluster-count"
        private const val STATION_CIRCLE_LAYER_ID = "cng-station-circles"
        private const val STATION_PRICE_LAYER_ID = "cng-station-prices"

        private fun isCluster(): Expression = Expression.has("point_count")

        private fun isNotCluster(): Expression = Expression.not(Expression.has("point_count"))
    }
}

internal fun List<MapStation>.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(map { station ->
        Feature.fromGeometry(Point.fromLngLat(station.longitude, station.latitude)).apply {
            addNumberProperty("stationId", station.id)
            addStringProperty("stationName", station.name)
            addNumberProperty("price", station.displayPrice)
            addStringProperty("priceLabel", station.priceLabel)
            addBooleanProperty("isSelf", station.displayPriceIsSelf)
        }
    })
