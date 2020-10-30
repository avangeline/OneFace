package com.palindramein.oneface


import android.Manifest
import android.app.IntentService
import android.app.PendingIntent.getActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowInsets
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class OneDigital : CanvasWatchFaceService() {

    companion object {
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        /**
         * Updates rate in milliseconds for interactive mode. We update once a second since seconds
         * are displayed in interactive mode.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: OneDigital.Engine) : Handler() {
        private val mWeakReference: WeakReference<OneDigital.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var fusedLocationClient: FusedLocationProviderClient
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false

        private var mXOffset: Float = 0F
        private var mYOffset: Float = 0F

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mTextPaint: Paint

        private var aqi = -1;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false

        private var mBackgroundColor = Color.BLACK
        private var mTextColor = Color.WHITE
        private var mAqiColor = Color.WHITE

        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@OneDigital)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            val resources = this@OneDigital.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)

            // Initializes background.
            mBackgroundPaint = Paint().apply {
                color = ContextCompat.getColor(applicationContext, R.color.background)
            }

            // Initializes Watch Face.
            mTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = !mAmbient
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
            }

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext!!)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        var mBackgroundColorBuffer = Color.BLACK
        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)

            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mTextPaint.isAntiAlias = !inAmbientMode
            }

            if (mAmbient) {
                mBackgroundColorBuffer = mBackgroundColor
                mBackgroundColor = Color.BLACK
                mTextColor = Color.WHITE
            } else {
                mBackgroundColor = mBackgroundColorBuffer
                setupPermission()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            drawDigitalWatchFace(canvas)
        }

        private fun drawDigitalWatchFace(canvas: Canvas) {
            val mCenterX = canvas.width / 2f
            val mCenterY = canvas.height / 2f
            val mBackgroundPaint = Paint()
            mBackgroundPaint.color = mBackgroundColor
            canvas.drawRect(
                0f,
                0f,
                canvas.width.toFloat(),
                canvas.height.toFloat(),
                mBackgroundPaint
            )
            val mTimePaint = Paint()
            mTimePaint.textSize = 80f
            mTimePaint.style = Paint.Style.FILL
            mTimePaint.color = mTextColor
            mTimePaint.isAntiAlias = !mAmbient
            mTimePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            mTimePaint.letterSpacing = 0.025f
            val mPosX: Float = mCenterX / 4f
            val mPosY: Float = mCenterY - 20
            var timeFormat = "%02d:%02d";
            canvas.drawText(
                String.format(
                    timeFormat,
                    mCalendar[Calendar.HOUR_OF_DAY],
                    mCalendar[Calendar.MINUTE]
                ),
                mPosX - 5, mPosY - 10, mTimePaint
            )
            val mDatePaint = Paint()
            mDatePaint.color = mTextColor
            mDatePaint.isAntiAlias = !mAmbient
            mDatePaint.textSize = 30f
            mDatePaint.style = Paint.Style.FILL
            mDatePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            canvas.drawText(
                String.format(
                    "%s, %d %s", mCalendar.getDisplayName(
                        Calendar.DAY_OF_WEEK,
                        Calendar.SHORT,
                        Locale.getDefault()
                    ),
                    mCalendar[Calendar.DAY_OF_MONTH],
                    mCalendar.getDisplayName(
                        Calendar.MONTH,
                        Calendar.LONG,
                        Locale.getDefault()
                    )
                ),
                mPosX, mPosY + 60, mDatePaint
            )
            val mBattPaint = Paint()
            mBattPaint.color = mTextColor
            mBattPaint.isAntiAlias = !mAmbient
            mBattPaint.textSize = 20f
            mBattPaint.style = Paint.Style.FILL
            mBattPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            val bm =
                getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batString = String.format("%d%%", batLevel)
            canvas.drawText(batString, mPosX, mPosY + 90, mBattPaint)

            var bound = Rect();
            mBattPaint.getTextBounds(batString, 0, batString.length, bound)
            drawBattIcon(canvas, mPosX + bound.width() + 15, mPosY + 90, bound.height().toFloat(), batLevel)

            if (aqi >= 0 && !mAmbient) {
                mBattPaint.color = mAqiColor
                mBattPaint.textSize = 60f
                mBattPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(String.format("%d", aqi), mCenterX, mPosY + 150, mBattPaint)

                mBattPaint.textSize = 20f
                canvas.drawText("AQI US", mCenterX, mPosY + 180, mBattPaint)
            }
        }

        private fun setupPermission() {
            var permission = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
            if (permission == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            } else {
                AsyncTask.execute(Runnable {
                    getAirQualityIndex()
                })
            }
        }
        private val REQUEST_CODE = 101

        fun getLastKnownLocation() {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location->
                    if (location != null) {
                        AsyncTask.execute(Runnable{
                            getAirQualityIndex(location.latitude, location.longitude)
                        })
                    } else {
                        AsyncTask.execute(Runnable {
                            getAirQualityIndex()
                        })
                    }
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

                    aqi = aqiUs
                    setContentColor(aqiUs)
                }
            }catch(e: Exception){
                Log.e("One Digital", e.message)
            }
        }

        private fun setContentColor(aqi: Int) {
            var lv = getDangerousLevel(aqi)
            if (lv == 0) {
                mBackgroundColor = Color.parseColor("#a8e05f")
                mAqiColor = Color.parseColor("#718b3a")
            }
            else if (lv == 1) {
                mBackgroundColor = Color.parseColor("#fdd74b")
                mAqiColor = Color.parseColor("#a57f23")
            }
            else if (lv == 2) {
                mBackgroundColor = Color.parseColor("#fe9b57")
                mAqiColor = Color.parseColor("#b25826")
            }
            else if (lv == 3) {
                mBackgroundColor = Color.parseColor("#fe6a69")
                mAqiColor = Color.parseColor("#af2c3b")
            }
            else if (lv == 4) {
                mBackgroundColor = Color.parseColor("#a97abc")
                mAqiColor = Color.parseColor("#634675")
            }
            else {
                mBackgroundColor = Color.parseColor("#a87383")
                mAqiColor = Color.parseColor("#683e51")
            }
        }

        private fun getDangerousLevel(aqi: Int): Int {
            if (aqi <= 50) return 0
            else if (aqi <= 100) return 1
            else if (aqi <= 150) return 2
            else if (aqi <= 200) return 3
            else if (aqi <= 300) return 4
            else return 5
        }

        private fun drawBattIcon(canvas: Canvas, posX: Float = 0f, posY: Float = 0f, iconHeight: Float = 20f, percentage: Int = 100) {
            val battWidth = 10f
            val battIconPaint = Paint().apply {
                color = mTextColor
                isAntiAlias = !mAmbient
                strokeWidth = battWidth
            }
            canvas.drawLine(posX, posY, posX, posY - (iconHeight * 0.9f), battIconPaint)

            battIconPaint.strokeWidth /= 2
            canvas.drawLine(posX, posY - (iconHeight * 0.9f), posX, posY - iconHeight, battIconPaint)

            battIconPaint.color = mBackgroundColor
            battIconPaint.strokeWidth *= 2
            battIconPaint.alpha = 150
            canvas.drawLine(posX, posY - iconHeight, posX, posY - iconHeight + (iconHeight * ((100f - percentage.toFloat())/100f)), battIconPaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@OneDigital.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@OneDigital.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@OneDigital.resources
            val isRound = insets.isRound
            mXOffset = resources.getDimension(
                if (isRound)
                    R.dimen.digital_x_offset_round
                else
                    R.dimen.digital_x_offset
            )

            val textSize = resources.getDimension(
                if (isRound)
                    R.dimen.digital_text_size_round
                else
                    R.dimen.digital_text_size
            )

            mTextPaint.textSize = textSize
        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
