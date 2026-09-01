package com.zangrcar.cngitaly.ui.map

import android.graphics.Color
import com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult
import com.zangrcar.cngitaly.data.routing.RouteEndpoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class PlaceWaypointMapLayer(
    private val map: MapLibreMap,
    style: Style,
    private val textLabelsEnabled: Boolean,
    private val onWaypointClick: (RouteEndpoint) -> Unit,
    private val onPlaceClick: (PlaceSearchResult) -> Unit
) : MapLibreMap.OnMapClickListener {
    private var waypointEndpoints = emptyList<RouteEndpoint>()
    private var place: PlaceSearchResult? = null

    init {
        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(CircleLayer(CIRCLE_ID, SOURCE_ID).withProperties(
            PropertyFactory.circleColor(Expression.match(Expression.get("kind"), Expression.literal("place"),
                Expression.color(Color.rgb(208, 72, 42)), Expression.color(Color.rgb(98, 54, 150)))),
            PropertyFactory.circleRadius(16f), PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleOpacity(1f)
        ))
        if (textLabelsEnabled) {
            style.addLayer(SymbolLayer(TEXT_ID, SOURCE_ID).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")), PropertyFactory.textSize(13f),
                PropertyFactory.textColor(Color.WHITE), PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            ))
        }
        map.addOnMapClickListener(this)
    }

    fun updateWaypoints(endpoints: List<RouteEndpoint>) { waypointEndpoints = endpoints; update() }
    fun updatePlace(value: PlaceSearchResult?) { place = value; update() }

    private fun update() {
        val waypointFeatures = waypointEndpoints.mapIndexedNotNull { index, endpoint ->
            if (index == 0 && endpoint.isCurrentLocation) null else Feature.fromGeometry(
                Point.fromLngLat(endpoint.longitude, endpoint.latitude)
            ).apply {
                addStringProperty("kind", "waypoint"); addNumberProperty("index", index)
                addStringProperty("label", when (index) { 0 -> "A"; waypointEndpoints.lastIndex -> "B"; else -> index.toString() })
            }
        }
        val placeFeature = place?.let { selected -> Feature.fromGeometry(Point.fromLngLat(selected.longitude, selected.latitude)).apply {
            addStringProperty("kind", "place"); addStringProperty("label", "●")
        } }
        map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(
            FeatureCollection.fromFeatures(waypointFeatures + listOfNotNull(placeFeature))
        )
    }

    override fun onMapClick(point: LatLng): Boolean {
        val feature = map.queryRenderedFeatures(
            map.projection.toScreenLocation(point),
            *interactionLayerIds(textLabelsEnabled)
        ).firstOrNull() ?: return false
        return if (feature.getStringProperty("kind") == "place") {
            place?.let(onPlaceClick); place != null
        } else {
            feature.getNumberProperty("index")?.toInt()?.let(waypointEndpoints::getOrNull)?.let(onWaypointClick); true
        }
    }

    fun destroy() = map.removeOnMapClickListener(this)

    companion object {
        private const val SOURCE_ID = "cng-route-points"
        internal const val CIRCLE_ID = "cng-route-point-circles"
        internal const val TEXT_ID = "cng-route-point-labels"

        internal fun interactionLayerIds(textLabelsEnabled: Boolean): Array<String> =
            if (textLabelsEnabled) arrayOf(TEXT_ID, CIRCLE_ID) else arrayOf(CIRCLE_ID)
    }
}
