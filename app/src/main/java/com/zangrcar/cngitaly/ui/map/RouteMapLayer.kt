package com.zangrcar.cngitaly.ui.map

import android.graphics.Color
import com.zangrcar.cngitaly.data.routing.GeoPoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

class RouteMapLayer(private val map: MapLibreMap, style: Style) {
    init {
        style.addSource(GeoJsonSource(SOURCE_ID))
        style.addLayer(LineLayer(CASING_ID, SOURCE_ID).withProperties(
            PropertyFactory.lineColor(Color.WHITE), PropertyFactory.lineWidth(8f),
            PropertyFactory.lineOpacity(.9f)
        ))
        style.addLayer(LineLayer(LINE_ID, SOURCE_ID).withProperties(
            PropertyFactory.lineColor(Color.rgb(24, 78, 140)), PropertyFactory.lineWidth(5.5f)
        ))
    }

    fun update(points: List<GeoPoint>) {
        map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(
            Feature.fromGeometry(LineString.fromLngLats(points.map {
                org.maplibre.geojson.Point.fromLngLat(it.longitude, it.latitude)
            }))
        )
    }

    fun clear() {
        map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    companion object {
        private const val SOURCE_ID = "cng-route"
        private const val CASING_ID = "cng-route-casing"
        private const val LINE_ID = "cng-route-line"
    }
}
