package com.palindramein.oneface

import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_aqi.view.*
import org.json.JSONObject
import java.net.URL
import android.Manifest;
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class AqiActivity : WearableActivity() {

    private val REQUEST_CODE = 101
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aqi)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext!!)
    }

    override fun onResume() {
        super.onResume()
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.contentLinearLayout).visibility = View.GONE
        setupPermission()
    }

    fun getLastKnownLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location->
                if (location != null) {
                    AsyncTask.execute(Runnable {
                        getAirQualityIndex(location.latitude, location.longitude)
                    })
                } else {
                    AsyncTask.execute(Runnable {
                        getAirQualityIndex()
                    })
                }
            }

    }

    private fun setupPermission() {
        var permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            makeRequest()
        } else {
            getLastKnownLocation()
        }
    }

    private fun getAirQualityIndex(lat: Double? = null, lon: Double? = null) {
        var url = getString(R.string.aqi_url)
        if (lat != null && lon != null) {
            url = "$url&lat=$lat&lon=$lon"
        }
        try{
            var txt = URL(url).readText()
            var res = JSONObject(txt)
            if (res.getString("status") == "success"){
                var aqiUs = res.getJSONObject("data")
                    .getJSONObject("current")
                    .getJSONObject("pollution")
                    .getInt("aqius")

                var city = res.getJSONObject("data")
                    .getString("city")

                runOnUiThread {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                    findViewById<LinearLayout>(R.id.contentLinearLayout).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.aqiTextView).setText(aqiUs.toString())
                    findViewById<TextView>(R.id.areaTextView).setText(city)
                    findViewById<TextView>(R.id.suggestionTextView).setText(getSuggestion(aqiUs))
                    setContentColor(aqiUs)
                }
            }
        } catch (e: Exception){
            Log.e("Aqi", e.message)
            runOnUiThread {
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                findViewById<LinearLayout>(R.id.contentLinearLayout).visibility = View.VISIBLE
                findViewById<TextView>(R.id.areaTextView).setText("No Data")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    AsyncTask.execute(Runnable {
                        getAirQualityIndex()
                    })
                    Log.i("Location Access", "Permission has been denied by user")
                } else {
                    getLastKnownLocation()
                    Log.i("Location Access", "Permission has been granted by user")
                }
            }
        }
    }

    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE)
    }

    private fun getSuggestion(aqi: Int): String {
        if (aqi <= 50) return "Good"
        else if (aqi <= 100) return "Moderate"
        else if (aqi <= 150) return "Unhealthy for Sensitive Groups"
        else if (aqi <= 200) return "Unhealthy"
        else if (aqi <= 300) return "Very Unhealthy"
        else return "Hazardous"
    }

    private fun getDangerousLevel(aqi: Int): Int {
        if (aqi <= 50) return 0
        else if (aqi <= 100) return 1
        else if (aqi <= 150) return 2
        else if (aqi <= 200) return 3
        else if (aqi <= 300) return 4
        else return 5
    }

    private fun setContentColor(aqi: Int) {
        var lv = getDangerousLevel(aqi)
        if (lv == 0) {
            setContentColor("#a8e05f", "#718b3a")
        }
        else if (lv == 1) {
            setContentColor("#fdd74b", "#a57f23")
        }
        else if (lv == 2) {
            setContentColor("#fe9b57", "#b25826")
        }
        else if (lv == 3) {
            setContentColor("#fe6a69", "#af2c3b")
        }
        else if (lv == 4) {
            setContentColor("#a97abc", "#634675")
        }
        else {
            setContentColor("#a87383", "#683e51")
        }
    }

    private fun setContentColor(bgColor: String, txtColor: String) {
        var contentLinearLayout = findViewById<LinearLayout>(R.id.contentLinearLayout)
        contentLinearLayout.setBackgroundColor(Color.parseColor(bgColor))
        contentLinearLayout.areaTextView.setTextColor(Color.parseColor(txtColor))
        contentLinearLayout.aqiTextView.setTextColor(Color.parseColor(txtColor))
        contentLinearLayout.unitTextView.setTextColor(Color.parseColor(txtColor))
        contentLinearLayout.suggestionTextView.setTextColor(Color.parseColor(txtColor))
    }
}
