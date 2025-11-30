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
