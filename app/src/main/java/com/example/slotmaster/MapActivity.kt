package com.example.slotmaster

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.util.Log
class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var tvLocationInfo: TextView
    private lateinit var btnCloseMap: Button
    private lateinit var locationOverlay: MyLocationNewOverlay

    private var currentLocation: Location? = null
    private val targetLocations = listOf(
        TargetLocation(49.6092, 20.7045, 100.0, false, "ANS"),
        TargetLocation(49.6251, 20.6912, 150.0, false, "Rynek"),
        TargetLocation(49.6092, 20.7134, 100.0, false, "Lidl")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_map_osm)

        // 🔽 ODBIERZ DANE Z MAINACTIVITY
        currentLocation = intent.getParcelableExtra("CURRENT_LOCATION")
        val visitedLocations = intent.getBooleanArrayExtra("VISITED_LOCATIONS")

        // 🔽 AKTUALIZUJ STAN LOKALIZACJI
        visitedLocations?.let { visited ->
            for (i in visited.indices) {
                if (i < targetLocations.size) {
                    targetLocations[i].visited = visited[i]
                }
            }
        }
        Log.d("MapActivity", "🎯 Stan lokalizacji:")
        targetLocations.forEachIndexed { index, target ->
            Log.d("MapActivity", "  ${index + 1}. ${target.name} - odwiedzona: ${target.visited}")
        }
        tvLocationInfo = findViewById(R.id.tvLocationInfo)
        btnCloseMap = findViewById(R.id.btnCloseMap)

        setupMap()

        btnCloseMap.setOnClickListener {
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupMap() {
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Lokalizacja użytkownika
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)

        // Kompas
        val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        // Ustaw widok na aktualną lokalizację
        currentLocation?.let { location ->
            val startPoint = GeoPoint(location.latitude, location.longitude)
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(startPoint)

            // Dodaj znacznik aktualnej lokalizacji
            val currentMarker = Marker(mapView)
            currentMarker.position = startPoint
            currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            currentMarker.title = "Twoja lokalizacja"
            currentMarker.icon = resources.getDrawable(R.drawable.ic_my_location, theme)
            mapView.overlays.add(currentMarker)

            // Dodaj target locations
            targetLocations.forEach { target ->
                val targetPoint = GeoPoint(target.latitude, target.longitude)

                // Koło zasięgu
                val circle = createCircle(targetPoint, target.radius)
                mapView.overlays.add(circle)

                // Znacznik lokalizacji
                val targetMarker = Marker(mapView)
                targetMarker.position = targetPoint
                targetMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                targetMarker.title = target.name
                targetMarker.snippet = "Promień: ${target.radius}m"
                targetMarker.icon = resources.getDrawable(
                    if (target.visited) R.drawable.ic_location_visited
                    else R.drawable.ic_location_target,
                    theme
                )
                mapView.overlays.add(targetMarker)
            }

            updateLocationInfo()
        }

        mapView.invalidate()
    }

    private fun createCircle(center: GeoPoint, radius: Double): Polygon {
        val circle = Polygon()
        circle.fillColor = 0x2200FF00  // Półprzezroczysty zielony
        circle.strokeColor = Color.GREEN
        circle.strokeWidth = 3.0f

        // 🔽 POPRAWIONE: Dokładniejsze koło z większą liczbą punktów
        val points = mutableListOf<GeoPoint>()
        val pointsCount = 60  // Więcej punktów = gładsze koło

        for (i in 0 until pointsCount) {
            val angle = Math.toRadians(i * 360.0 / pointsCount)

            // 🔽 POPRAWIONE: Dokładniejsza konwersja metrów na stopnie
            val latOffset = (radius * Math.cos(angle)) / 111320.0
            val lonOffset = (radius * Math.sin(angle)) / (111320.0 * Math.cos(Math.toRadians(center.latitude)))

            points.add(GeoPoint(center.latitude + latOffset, center.longitude + lonOffset))
        }

        circle.setPoints(points)
        return circle
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationInfo() {
        currentLocation?.let { location ->
            var info = "🗺️ Mapa Lokalizacji\n\n"
            info += "Twoja pozycja:\n"
            info += "Szerokość: ${"%.6f".format(location.latitude)}\n"
            info += "Długość: ${"%.6f".format(location.longitude)}\n\n"
            info += "🎯 Cele:\n"

            targetLocations.forEachIndexed { index, target ->
                val distance = calculateDistance(location, target)
                val visitedStatus = if (target.visited) "✅ ZDOBYTE" else "❌ DO ZDOBYCIA"
                info += "${index + 1}. ${target.name} - $visitedStatus\n"
                info += "   Odległość: ${"%.0f".format(distance)}m\n"
            }

            tvLocationInfo.text = info
        }
    }

    private fun calculateDistance(current: Location, target: TargetLocation): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            target.latitude, target.longitude,
            results
        )
        return results[0]
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
