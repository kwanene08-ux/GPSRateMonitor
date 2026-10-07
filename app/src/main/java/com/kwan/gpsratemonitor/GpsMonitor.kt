package com.kwan.gpsratemonitor

import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlin.math.max

data class GpsSnapshot(
    val running: Boolean = false,
    val hz: Double = 0.0,
    val averageHz: Double = 0.0,
    val minHz: Double = 0.0,
    val maxHz: Double = 0.0,
    val intervalMs: Long = 0L,
    val accuracyM: Float = 0f,
    val updateCount: Long = 0L,
    val dropCount: Long = 0L,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val gpsCount: Int = 0,
    val glonassCount: Int = 0,
    val galileoCount: Int = 0,
    val beidouCount: Int = 0,
    val otherCount: Int = 0,
    val cn0Average: Float = 0f,
    val hzHistory: List<Float> = emptyList()
)

class GpsMonitor(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var lastElapsedNs = 0L
    private var lastUpdateWallMs = 0L

    private val hzValues = ArrayList<Double>()
    private val history = ArrayDeque<Float>()

    private var updateCount = 0L
    private var dropCount = 0L

    private var currentHz = 0.0
    private var currentAverageHz = 0.0
    private var minHz = Double.MAX_VALUE
    private var maxHz = 0.0
    private var intervalMs = 0L
    private var accuracy = 0f

    private var visible = 0
    private var used = 0
    private var gps = 0
    private var glonass = 0
    private var galileo = 0
    private var beidou = 0
    private var other = 0
    private var cn0Average = 0f

    private var running = false

    private val listener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            val nowNs = System.nanoTime()
            val nowMs = System.currentTimeMillis()

            if (lastElapsedNs != 0L) {
                val deltaNs = nowNs - lastElapsedNs

                if (deltaNs > 0L) {
                    intervalMs = deltaNs / 1_000_000L
                    currentHz = 1_000_000_000.0 / deltaNs.toDouble()

                    if (currentHz.isFinite() && currentHz > 0.0) {
                        hzValues.add(currentHz)
                        if (hzValues.size > 300) {
                            hzValues.removeAt(0)
                        }

                        minHz = minOf(minHz, currentHz)
                        maxHz = maxOf(maxHz, currentHz)
                        currentAverageHz =
                            hzValues.average()

                        history.addLast(currentHz.toFloat())
                        while (history.size > 60) {
                            history.removeFirst()
                        }
                    }
                }
            }

            if (lastUpdateWallMs != 0L) {
                val wallDelta = nowMs - lastUpdateWallMs

                // Drop = gap longer than 2 seconds.
                if (wallDelta > 2000L) {
                    dropCount++
                }
            }

            lastElapsedNs = nowNs
            lastUpdateWallMs = nowMs
            updateCount++

            accuracy = location.accuracy
        }

        override fun onProviderDisabled(provider: String) {
        }

        override fun onProviderEnabled(provider: String) {
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var visibleCount = 0
            var usedCount = 0
            var gpsCount = 0
            var glonassCount = 0
            var galileoCount = 0
            var beidouCount = 0
            var otherCount = 0
            var cn0Total = 0f
            var cn0Samples = 0

            for (i in 0 until status.satelliteCount) {
                visibleCount++

                if (status.usedInFix(i)) {
                    usedCount++
                }

                when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> gpsCount++
                    GnssStatus.CONSTELLATION_GLONASS -> glonassCount++
                    GnssStatus.CONSTELLATION_GALILEO -> galileoCount++
                    GnssStatus.CONSTELLATION_BEIDOU -> beidouCount++
                    else -> otherCount++
                }

                val cn0 = status.getCn0DbHz(i)

                if (cn0.isFinite() && cn0 >= 0f) {
                    cn0Total += cn0
                    cn0Samples++
                }
            }

            visible = visibleCount
            used = usedCount
            gps = gpsCount
            glonass = glonassCount
            galileo = galileoCount
            beidou = beidouCount
            other = otherCount

            cn0Average =
                if (cn0Samples > 0) {
                    cn0Total / cn0Samples
                } else {
                    0f
                }
        }
    }

    fun start() {
        if (running) return

        lastElapsedNs = 0L
        lastUpdateWallMs = 0L
        hzValues.clear()
        history.clear()

        updateCount = 0L
        dropCount = 0L
        currentHz = 0.0
        currentAverageHz = 0.0
        minHz = Double.MAX_VALUE
        maxHz = 0.0
        intervalMs = 0L
        accuracy = 0f

        running = true

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )

            if (android.os.Build.VERSION.SDK_INT >= 24) {
                locationManager.registerGnssStatusCallback(
                    gnssCallback,
                    android.os.Handler(Looper.getMainLooper())
                )
            }
        } catch (e: SecurityException) {
            running = false
            throw e
        }
    }

    fun stop() {
        if (!running) return

        locationManager.removeUpdates(listener)

        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                locationManager.unregisterGnssStatusCallback(
                    gnssCallback
                )
            } catch (_: Exception) {
            }
        }

        running = false
    }

    fun snapshot(): GpsSnapshot {
        return GpsSnapshot(
            running = running,
            hz = currentHz,
            averageHz = currentAverageHz,
            minHz =
                if (minHz == Double.MAX_VALUE) 0.0 else minHz,
            maxHz = maxHz,
            intervalMs = intervalMs,
            accuracyM = accuracy,
            updateCount = updateCount,
            dropCount = dropCount,
            satellitesVisible = visible,
            satellitesUsed = used,
            gpsCount = gps,
            glonassCount = glonass,
            galileoCount = galileo,
            beidouCount = beidou,
            otherCount = other,
            cn0Average = cn0Average,
            hzHistory = history.toList()
        )
    }
}
