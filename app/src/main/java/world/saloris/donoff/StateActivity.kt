package world.saloris.donoff


import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.mediapipe.solutioncore.CameraInput
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import kotlinx.coroutines.*
import world.saloris.donoff.database.driverState.DriverState
import world.saloris.donoff.database.driverState.DriverStateDao
import world.saloris.donoff.database.heartRate.HeartRate
import world.saloris.donoff.database.heartRate.HeartRateDao
import world.saloris.donoff.databinding.ActivityStateBinding
import world.saloris.donoff.util.*
import world.saloris.donoff.util.facemesh.FaceMeshResultGlRenderer
import world.saloris.donoff.util.user.MakeToast
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.pow
import kotlin.math.sqrt

private const val LONG_CLOSED_EYE_TEXT = "Long Closed Eyes!!"

class StateActivity : AppCompatActivity() {
    /* Toast */
    private val toast = MakeToast()

    /* User Authentication */
    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String

    /* Permission */
    private val locationPermissionList = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private val bluetoothPermissionList = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val cameraPermissionList = arrayOf(
        Manifest.permission.CAMERA
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedList = result.filter { !it.value }.map { it.key }
            Log.d("State", "$deniedList")
            if (deniedList.isNotEmpty()) {
                if (deniedList.any { it == Manifest.permission.CAMERA }) {
                    isCameraOn = false
                }

                AlertDialog.Builder(this)
                    .setTitle("알림")
                    .setMessage("권한이 거부되었습니다. 사용을 원하시면 설정에서 해당 권한을 직접 허용하셔야 합니다.")
                    .setPositiveButton("설정") { _, _ -> openAndroidSetting() }
                    .setNegativeButton("취소", null)
                    .create()
                    .show()
            } else {
                isCameraOn = true
            }
        }

    private fun openAndroidSetting() {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:${packageName}")
        }
        startActivity(intent)
    }

    /* BLE */
    private var isHeartRateValid = false
    private var hr = 0
    private var time = 0

    private lateinit var name: String
    private lateinit var address: String
    private var device: BluetoothDevice? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager =
            this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var isBluetoothOn = false
    private var bluetoothGatt: BluetoothGatt? = null
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGatt()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGatt()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                /* Permission */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            baseContext, Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissionLauncher.launch(bluetoothPermissionList)
                        return
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(
                            baseContext, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissionLauncher.launch(locationPermissionList)
                        return
                    }
                }

                isBluetoothOn = true
                timerTask.cancel()
                if (!isCameraOn) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.state.visibility = View.VISIBLE
                        binding.stateFitting.visibility = View.VISIBLE
                    }
                }
                Log.d("State", "Connected to the GATT server")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isBluetoothOn = false
                isHeartRateValid = false
                toNewTimerTask()
                timer.schedule(timerTask, SCAN_TIME)
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.hrState.visibility = View.INVISIBLE
                    binding.deviceState.visibility = View.VISIBLE
                    binding.textDeviceState.text = getString(R.string.connecting)
                    binding.hrStateFitting.visibility = View.INVISIBLE
                    binding.deviceStateFitting.visibility = View.VISIBLE
                    binding.textDeviceStateFitting.text = getString(R.string.connecting_inline)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("State", "Device service discovery failed, status: $status")
                return
            }
            Log.d("State", "Services discovery is successful")

            if (gatt == null) {
                Log.e("State", "Unable to find gatt")
                return
            }

            val responseCharacteristic = findHrmResponseCharacteristic(gatt)
            if (responseCharacteristic == null) {
                Log.e("State", "Unable to find response characteristic")
                disconnectGatt()
                return
            }
            /* Permission */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        baseContext, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(bluetoothPermissionList)
                    return
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        baseContext, Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(locationPermissionList)
                    return
                }
            }

            gatt.setCharacteristicNotification(responseCharacteristic, true)

            val descriptor: BluetoothGattDescriptor = responseCharacteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_STRING)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        private fun findHrmResponseCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
            val serviceList = gatt.services ?: return null
            val hrService =
                serviceList.find { it.uuid.toString() == HEART_RATE_SERVICE_STRING }
                    ?: return null
            val hrmResponseCharacteristic = hrService.characteristics.find {
                it.uuid.toString() == HEART_RATE_MEASUREMENT_CHARACTERISTIC_STRING
            } ?: return null
            return hrmResponseCharacteristic
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
//            Log.d("State", "characteristic changed: " + characteristic.uuid.toString())
            readCharacteristic(characteristic)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("State", "Characteristic read successfully")
                readCharacteristic(characteristic)
            } else {
                Log.e("State", "Characteristic read unsuccessful, status: $status")
            }
        }

        private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
            hr = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1)
            Log.d("HR", "$hr")
            if (hr == 0) {
                isHeartRateValid = false
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.hrState.visibility = View.INVISIBLE
                    binding.hrStateFitting.visibility = View.INVISIBLE
                    binding.deviceState.visibility = View.VISIBLE
                    binding.deviceStateFitting.visibility = View.VISIBLE
                    binding.textDeviceState.text = getString(R.string.waiting)
                    binding.textDeviceStateFitting.text = getString(R.string.waiting)
                }
            } else {
                isHeartRateValid = true
                displayHeartRate(hr)
            }
        }
    }

    private fun connectGatt() {
        /* Permission */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    baseContext, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(bluetoothPermissionList)
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    baseContext, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(locationPermissionList)
                return
            }
        }

        bluetoothGatt = device?.connectGatt(this, true, gattCallback)
    }

    private fun disconnectGatt() {
        isBluetoothOn = false

        /* Permission */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    baseContext, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    baseContext, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        if (bluetoothAdapter!!.isEnabled) {
            Log.d("State", "Disconnecting Gatt connection")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
    }

    private fun displayHeartRate(hr: Int) {
        val hrString = hr.toString()

        lifecycleScope.launch(Dispatchers.Main) {
            binding.deviceState.visibility = View.GONE
            binding.deviceStateFitting.visibility = View.GONE
            binding.hrState.visibility = View.VISIBLE
            binding.hrStateFitting.visibility = View.VISIBLE
            binding.heartRate.text = hrString
            binding.heartRateFitting.text = hrString
            chart.addEntry(hr)
        }
    }

    /* Graph */
    private lateinit var chart: LineChart
    private val lineColors = ArrayList<Int>()

    private fun initChart() {
        chart = binding.graphHrRealtime
        with(chart) {
            setDrawGridBackground(false)
            setTouchEnabled(false)
            setNoDataText(null)
            setViewPortOffsets(0f, 0f, 0f, 0f)
            description.isEnabled = false
            legend.isEnabled = false

            with(xAxis) {
                setDrawGridLines(false)
                setDrawLabels(false)
                setDrawAxisLine(false)
            }
            with(axisLeft) {
                setDrawGridLines(false)
                setDrawLabels(false)
                setDrawAxisLine(false)
            }
            axisRight.isEnabled = false

            val dataSet = LineDataSet(null, "HeartRate")
            with(dataSet) {
                setDrawValues(false)
                setDrawCircles(false)
                lineWidth = 1.5f
                colors = lineColors
                mode = LineDataSet.Mode.LINEAR
            }
            data = LineData(dataSet)
        }
    }

    private fun LineChart.addEntry(hr: Int) {
        data.addEntry(Entry(time.toFloat(), hr.toFloat()), 0)
        data.notifyDataChanged()
        lineColors.add(
            ContextCompat.getColor(
                context, if (hr >= 67) R.color.white else R.color.drowsy
            )
        )
        time++

        setVisibleXRange(120f, 120f)

        with(axisLeft) {
            axisMinimum = hr.coerceAtMost(60).toFloat()
            axisMaximum = hr.coerceAtLeast(100).toFloat()
        }

        notifyDataSetChanged()
        moveViewToX(xChartMax)
    }

    /* FaceMesh */
    private var isCameraOn = false

    private var isFaceOn = false
    private var isFaceGood = false
    private var isFaceValid = false

    private lateinit var prefs: SharedPreferences
    private lateinit var faceMeshSettings: BooleanArray
    private lateinit var faceMeshColors: ArrayList<FloatArray>

    private lateinit var faceMesh: FaceMesh

    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<FaceMeshResult>

    private var blink: Int = 0          // 현재 눈 감은상태
    private var totalBlink: Int = 0     // 누적 눈 깜빡임
    private var face: String = ""       // 현재 얼굴방향
    private var leftEye: String = ""    // 왼쪽 눈 방향
    private var rightEye: String = ""   // 오른쪽 눈 방향
    private var ear: Float = 0.0f
    private var mar: Float = 0.0f
    private var moe: Float = 0.0f

    private enum class FatigueEyeState { NORMAL, DROWSY, SLEEP }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var fatigueEyeState = FatigueEyeState.NORMAL

    // 눈 감김 상태 추적
    private var eyeWasClosed = false          // 직전 프레임에서 눈을 감고 있었는지
    private var longCloseWindowAnchorMs = -1L  // 첫 번째 Long Closed Eyes 발생 시각
    private var resetLongCloseWindowRunnable: Runnable? = null
    private var postBlinkSequenceJob: Job? = null

    private lateinit var fatigueAlarmPrefs: SharedPreferences
    private var fatigueOverlayRedVisible = false

    // object : Runnable 형태로 선언해야 run() 내부의 this가 Runnable 자신을 가리킴

    /** 화면 깜빡임: 졸음 750ms / 수면 180ms 간격으로 반복 */
    private val fatigueOverlayBlinkRunnable = object : Runnable {
        override fun run() {
            if (fatigueEyeState == FatigueEyeState.NORMAL) return
            if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_SCREEN_BLINK, true)) {
                binding.eyeRedOverlay.visibility = View.VISIBLE
                return
            }
            fatigueOverlayRedVisible = !fatigueOverlayRedVisible
            binding.eyeRedOverlay.visibility =
                if (fatigueOverlayRedVisible) View.VISIBLE else View.GONE
            mainHandler.postDelayed(this, fatigueBlinkIntervalMs())
        }
    }

    /** 반복 경보음: 졸음 2.5초 / 수면 0.8초 간격으로 반복 */
    private val fatigueAlarmSoundRunnable = object : Runnable {
        override fun run() {
            if (fatigueEyeState == FatigueEyeState.NORMAL) return
            if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_SOUND, true)) return
            when (fatigueEyeState) {
                FatigueEyeState.DROWSY -> {
                    drowsyEyeTone.startTone(ToneGenerator.TONE_SUP_DIAL_TONE, 350)
                    mainHandler.postDelayed(this, FATIGUE_SOUND_INTERVAL_DROWSY_MS)
                }
                FatigueEyeState.SLEEP -> {
                    sleepEyeTone.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 600)
                    mainHandler.postDelayed(this, FATIGUE_SOUND_INTERVAL_SLEEP_MS)
                }
                else -> {}
            }
        }
    }

    /** 반복 진동: 졸음 3초 / 수면 1.2초 간격으로 반복 */
    private val fatigueAlarmVibRunnable = object : Runnable {
        override fun run() {
            if (fatigueEyeState == FatigueEyeState.NORMAL) return
            if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_VIBRATION, true)) return
            when (fatigueEyeState) {
                FatigueEyeState.DROWSY -> {
                    vibrateMedium()
                    mainHandler.postDelayed(this, FATIGUE_VIB_INTERVAL_DROWSY_MS)
                }
                FatigueEyeState.SLEEP -> {
                    vibrateStrong()
                    mainHandler.postDelayed(this, FATIGUE_VIB_INTERVAL_SLEEP_MS)
                }
                else -> {}
            }
        }
    }

    private fun fatigueBlinkIntervalMs(): Long = when (fatigueEyeState) {
        FatigueEyeState.SLEEP -> FATIGUE_BLINK_INTERVAL_SLEEP_MS
        else -> FATIGUE_BLINK_INTERVAL_DROWSY_MS
    }

    private fun colorLoad(value: Int): FloatArray {
        return when (value) {
            1 -> WHITE_COLOR
            2 -> ORANGE_COLOR
            3 -> BLUE_COLOR
            4 -> RED_COLOR
            5 -> GREEN_COLOR

            else -> BLACK_COLOR
        }
    }

    private fun initFaceMesh() {
        // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
        // refineLandmark - 눈, 입술 주변으로 분석 추가.
        faceMesh = FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(true)
                .build()
        )
        faceMesh.setErrorListener { message: String, _: RuntimeException? ->
            Log.e("State", "MediaPipe Face Mesh error:$message")
            toast.makeToast(this, "인식이 되지 않습니다.")
        }
    }

    private var fittingLevel = 0
    private var timerCheck = true

    private fun initGlSurfaceView() {
        // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
        glSurfaceView = SolutionGlSurfaceView(this, faceMesh.glContext, faceMesh.glMajorVersion)
        glSurfaceView.setSolutionResultRenderer(
            FaceMeshResultGlRenderer(faceMeshSettings, faceMeshColors)
        )
        faceMesh.setResultListener { faceMeshResult: FaceMeshResult? ->
            checkLandmark(faceMeshResult)

            lifecycleScope.launch(Dispatchers.Main) {
                if (isFaceValid) {
                    with(binding.guideline) {
                        if (this@StateActivity::guidelineAnimation.isInitialized)
                            guidelineAnimation.stop()
                        setBackgroundResource(R.drawable.face_guideline_complete)
                    }
                    binding.faceFittingWarningText.text =
                        getString(R.string.face_fitting_complete)
                    // 인식 완료 1초 후 화면 변경
                    if (timerCheck) {
                        timer2 = timer(period = 100) {
                            timerCheck = false
                            fittingLevel += 1
                            Log.d("fittingLevel", "$fittingLevel")
                            if (fittingLevel >= MAX_FITTING_LEVEL) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding.faceFitting.visibility = View.GONE
                                    if (isBluetoothOn) {
                                        binding.state.visibility = View.VISIBLE
                                    }
                                }
                                cancel()
                            }
                        }
                    }
                } else {
                    reset()
                    binding.faceFitting.visibility = View.VISIBLE
                    binding.state.visibility = View.GONE
                    if (isBluetoothOn) {
                        binding.stateFitting.visibility = View.VISIBLE
                    }
                    with(binding.guideline) {
                        setBackgroundResource(R.drawable.face_guideline)
                        guidelineAnimation = background as AnimationDrawable
                        guidelineAnimation.start()
                    }
                    if (!isFaceOn) {
                        binding.faceFittingWarningText.text = getString(R.string.face_fitting_init)
                    }
                    if (!isFaceGood) {
                        binding.faceFittingWarningText.text = getString(R.string.face_fitting_warn)
                    }
                }
            }

            glSurfaceView.setRenderData(faceMeshResult, true)
            glSurfaceView.requestRender()
        }
    }

    private fun postGlSurfaceView() {
        cameraInput = CameraInput(this)
        cameraInput.setNewFrameListener { faceMesh.send(it) }

        glSurfaceView.post { startCamera() }
        glSurfaceView.visibility = View.VISIBLE
    }

    private fun startCamera() {
        cameraInput.start(this, faceMesh.glContext, CameraInput.CameraFacing.FRONT, 480, 640)
    }

    // landmark 분석
    private fun checkLandmark(result: FaceMeshResult?) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            isFaceOn = false
            isFaceGood = true
            isFaceValid = false
            resetEyeFatigueTracking()
            return
        }

        with(result.multiFaceLandmarks()[0].landmarkList) {
            val mouth = MOUTH_INDEX.map { get(it) }
            val lEye = LEFT_EYE_INDEX.map { get(it) }
            val rEye = RIGHT_EYE_INDEX.map { get(it) }
            val base = BASE_INDEX.map { get(it) }
            val pitching = FACE_PITCHING.map { get(it) }

            // 얼굴이 가이드라인 안에 있는지 0.10 0.95 0.26 0.86
            isFaceOn =
                !(pitching[0].y < 0.08 || pitching[1].y > 0.97 || pitching[2].x < 0.24 || pitching[3].x > 0.88)

            val nose = result.multiFaceLandmarks()[0].getLandmark(4).z
            isFaceGood = -0.14 < nose && nose < -0.04

            isFaceValid = isFaceOn && isFaceGood
            if (!isFaceValid) {
                resetEyeFatigueTracking()
                return
            }

            val leftEAR = ((distance(lEye[1].x, lEye[5].x, lEye[1].y, lEye[5].y))
                    + (distance(lEye[2].x, lEye[4].x, lEye[2].y, lEye[4].y))) /
                    (2 * (distance(lEye[0].x, lEye[3].x, lEye[0].y, lEye[3].y)))
            val rightEAR = ((distance(rEye[1].x, rEye[5].x, rEye[1].y, rEye[5].y))
                    + (distance(rEye[2].x, rEye[4].x, rEye[2].y, rEye[4].y))) /
                    (2 * (distance(rEye[0].x, rEye[3].x, rEye[0].y, rEye[3].y)))

            ear = (leftEAR + rightEAR) / 2
            mar = ((distance(mouth[1].x, mouth[7].x, mouth[1].y, mouth[7].y))
                    + (distance(mouth[2].x, mouth[6].x, mouth[2].y, mouth[6].y))
                    + (distance(mouth[3].x, mouth[5].x, mouth[3].y, mouth[5].y))) /
                    (2 * (distance(mouth[0].x, mouth[4].x, mouth[0].y, mouth[4].y)))
            moe = mar / ear

            // 좌, 우 눈 길이
            val leftEyeDistance = distance(lEye[6].x, base[1].x, lEye[6].y, base[1].y).pow(2)
            val rightEyeDistance = distance(rEye[6].x, base[1].x, rEye[6].y, base[1].y).pow(2)

            // 얼굴 방향 측정
            face = faceDirection(mouth[8].z, mouth[9].z, lEye[0].z, rEye[3].z, base[0].z, base[2].z)

            // EAR 기준 미달 시 양 눈 모두 blink 표시 (blink 카운트는 trackEyeClosureAndFatigue에서 처리)
            if (ear < EAR_CLOSED_THRESHOLD) {
                leftEye = getString(R.string.blink)
                rightEye = getString(R.string.blink)
            } else {
                if (leftEAR < 0.22) {
                    leftEye = getString(R.string.blink)
                    rightEye = eyeDirection(leftEyeDistance, rightEyeDistance)
                } else if (rightEAR < 0.22) {
                    leftEye = eyeDirection(leftEyeDistance, rightEyeDistance)
                    rightEye = getString(R.string.blink)
                } else {
                    leftEye = eyeDirection(leftEyeDistance, rightEyeDistance)
                    rightEye = eyeDirection(leftEyeDistance, rightEyeDistance)
                }
            }
            trackEyeClosureAndFatigue(ear)
        }
    }

    private fun distance(rx: Float, lx: Float, ry: Float, ly: Float): Float {
        return sqrt((rx - lx).pow(2) + (ry - ly).pow(2))
    }

    private fun eyeDirection(ld: Float, rd: Float): String {
        return if ((ld - rd) > 0.004)
            getString(R.string.left)
        else if ((ld - rd) < -0.0035)
            getString(R.string.right)
        else
            getString(R.string.front)

    }

    private fun faceDirection(
        lez: Float, rez: Float, lmz: Float, rmz: Float, hp: Float, cp: Float
    ): String {
        val fdRatio = (lez + lmz) - (rez + rmz)

        return if (hp - cp < -0.05)
            getString(R.string.down)
        else {
            if (fdRatio > 0.15)
                getString(R.string.left)
            else if (fdRatio < -0.15)
                getString(R.string.right)
            else
                getString(R.string.front)
        }
    }

    /* Warn */
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 200)
    private val drowsyEyeTone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val sleepEyeTone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private var warningLevel = 0

    private fun resetEyeFatigueTracking() {
        if (eyeWasClosed) {
            eyeWasClosed = false
            cancelEyeClosedCountdown()
            mainHandler.post { binding.eyeEventText.text = "" }
        }
    }

    private fun defaultVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        val vibrator = defaultVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun vibrateMedium() = vibrateOneShot(450)

    private fun vibrateStrong() = vibrateOneShot(900, 220)

    private fun stopFatigueOverlayBlink() {
        mainHandler.removeCallbacks(fatigueOverlayBlinkRunnable)
        binding.eyeRedOverlay.visibility = View.GONE
        fatigueOverlayRedVisible = false
    }

    private fun restartFatigueOverlayBlink() {
        if (fatigueEyeState == FatigueEyeState.NORMAL) {
            stopFatigueOverlayBlink()
            return
        }
        mainHandler.removeCallbacks(fatigueOverlayBlinkRunnable)
        if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_SCREEN_BLINK, true)) {
            binding.eyeRedOverlay.visibility = View.VISIBLE
            return
        }
        fatigueOverlayRedVisible = false
        mainHandler.post(fatigueOverlayBlinkRunnable)
    }

    private fun stopFatigueAlarmSound() {
        mainHandler.removeCallbacks(fatigueAlarmSoundRunnable)
        drowsyEyeTone.stopTone()
        sleepEyeTone.stopTone()
    }

    private fun restartFatigueAlarmSound() {
        mainHandler.removeCallbacks(fatigueAlarmSoundRunnable)
        if (fatigueEyeState == FatigueEyeState.NORMAL) return
        if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_SOUND, true)) return
        mainHandler.post(fatigueAlarmSoundRunnable)
    }

    private fun stopFatigueAlarmVibration() {
        mainHandler.removeCallbacks(fatigueAlarmVibRunnable)
    }

    private fun restartFatigueAlarmVibration() {
        mainHandler.removeCallbacks(fatigueAlarmVibRunnable)
        if (fatigueEyeState == FatigueEyeState.NORMAL) return
        if (!fatigueAlarmPrefs.getBoolean(KEY_FATIGUE_ALARM_VIBRATION, true)) return
        mainHandler.post(fatigueAlarmVibRunnable)
    }

    /** 모든 피로 경보(화면 깜빡임 + 소리 + 진동)를 즉시 중단 */
    private fun stopAllFatigueAlarms() {
        stopFatigueOverlayBlink()
        stopFatigueAlarmSound()
        stopFatigueAlarmVibration()
    }

    /** 현재 fatigueEyeState에 맞춰 모든 경보를 재시작 */
    private fun restartAllFatigueAlarms() {
        restartFatigueOverlayBlink()
        restartFatigueAlarmSound()
        restartFatigueAlarmVibration()
    }

    private fun setFatigueEyeState(newState: FatigueEyeState) {
        if (newState == fatigueEyeState) return
        fatigueEyeState = newState
        mainHandler.post { applyFatigueEyeStateUi() }
    }

    private fun applyFatigueEyeStateUi() {
        // 이전 상태의 경보를 모두 중단한 뒤 새 상태에 맞춰 재시작
        stopAllFatigueAlarms()
        when (fatigueEyeState) {
            FatigueEyeState.NORMAL -> {
                binding.drowsiness.text = getString(R.string.normal)
                binding.drowsiness.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.drowsinessFitting.text = getString(R.string.normal)
                binding.drowsinessFitting.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            FatigueEyeState.DROWSY -> {
                binding.warningFilter.visibility = View.GONE
                (binding.warningFilter.drawable as? AnimationDrawable)?.stop()
                binding.drowsiness.text = getString(R.string.drowsy)
                binding.drowsiness.setTextColor(ContextCompat.getColor(this, R.color.drowsy))
                binding.drowsinessFitting.text = getString(R.string.drowsy)
                binding.drowsinessFitting.setTextColor(ContextCompat.getColor(this, R.color.drowsy))
                restartAllFatigueAlarms()
            }
            FatigueEyeState.SLEEP -> {
                binding.warningFilter.visibility = View.GONE
                (binding.warningFilter.drawable as? AnimationDrawable)?.stop()
                binding.drowsiness.text = getString(R.string.sleep_state)
                binding.drowsiness.setTextColor(ContextCompat.getColor(this, R.color.red))
                binding.drowsinessFitting.text = getString(R.string.sleep_state)
                binding.drowsinessFitting.setTextColor(ContextCompat.getColor(this, R.color.red))
                restartAllFatigueAlarms()
            }
        }
    }

    /**
     * 눈을 감는 순간부터 카운트다운을 시작합니다:
     * 즉시 "Blink" → 1초 후 "1" → 2초 후 "2" → 3초 후 "Long Closed Eyes!!" + 상태 전환
     * 코루틴이 취소되면(눈을 뜨면) 자동으로 중단됩니다.
     */
    private fun startEyeClosedCountdown() {
        postBlinkSequenceJob?.cancel()
        postBlinkSequenceJob = lifecycleScope.launch(Dispatchers.Main) {
            binding.eyeEventText.text = "Blink"
            Log.d("EyeState", "Blink")
            delay(EYE_COUNT_STEP_MS)
            binding.eyeEventText.text = "1"
            delay(EYE_COUNT_STEP_MS)
            binding.eyeEventText.text = "2"
            delay(EYE_COUNT_STEP_MS)
            binding.eyeEventText.text = LONG_CLOSED_EYE_TEXT
            Log.d("EyeState", LONG_CLOSED_EYE_TEXT)
            onLongCloseEvent()
        }
    }

    private fun cancelEyeClosedCountdown() {
        postBlinkSequenceJob?.cancel()
        postBlinkSequenceJob = null
    }

    /**
     * Long Closed Eyes 발생 시 상태를 결정합니다:
     * - 1분 내 첫 번째: DROWSY (+ 1분 타이머 시작)
     * - 1분 내 두 번째: SLEEP  (타이머 취소)
     * - 1분 타이머 만료: 윈도우 초기화 (다음 Long Closed Eyes는 다시 DROWSY부터 시작)
     */
    private fun onLongCloseEvent() {
        val now = SystemClock.elapsedRealtime()
        if (longCloseWindowAnchorMs >= 0L && now - longCloseWindowAnchorMs <= EYE_LONG_CLOSE_WINDOW_MS) {
            resetLongCloseWindowRunnable?.let { mainHandler.removeCallbacks(it) }
            resetLongCloseWindowRunnable = null
            longCloseWindowAnchorMs = -1L
            setFatigueEyeState(FatigueEyeState.SLEEP)
        } else {
            resetLongCloseWindowRunnable?.let { mainHandler.removeCallbacks(it) }
            longCloseWindowAnchorMs = now
            setFatigueEyeState(FatigueEyeState.DROWSY)
            val runnable = Runnable {
                longCloseWindowAnchorMs = -1L
                resetLongCloseWindowRunnable = null
            }
            resetLongCloseWindowRunnable = runnable
            mainHandler.postDelayed(runnable, EYE_LONG_CLOSE_WINDOW_MS)
        }
    }

    /**
     * EAR 값을 기반으로 눈 상태 변화를 감지합니다:
     * - 눈 감음 (open→closed): 카운트다운 시작
     * - 눈 뜸  (closed→open): 카운트다운 취소 및 텍스트 초기화
     */
    private fun trackEyeClosureAndFatigue(earNow: Float) {
        val isClosed = earNow < EAR_CLOSED_THRESHOLD
        when {
            isClosed && !eyeWasClosed -> {
                eyeWasClosed = true
                blink = 1
                totalBlink++
                startEyeClosedCountdown()
            }
            !isClosed && eyeWasClosed -> {
                eyeWasClosed = false
                blink = 0
                cancelEyeClosedCountdown()
                mainHandler.post { binding.eyeEventText.text = "" }
            }
        }
    }

    // 심박수 기반 경고 (눈 피로 상태와 독립적으로 동작)
    private fun startWarning() {
        vibrateOneShot(500)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
        lifecycleScope.launch(Dispatchers.Main) {
            if (fatigueEyeState == FatigueEyeState.NORMAL) {
                binding.drowsiness.apply {
                    text = getString(R.string.drowsy)
                    setTextColor(ContextCompat.getColor(this@StateActivity, R.color.drowsy))
                }
                binding.drowsinessFitting.apply {
                    text = getString(R.string.drowsy)
                    setTextColor(ContextCompat.getColor(this@StateActivity, R.color.drowsy))
                }
            }
            binding.warningFilter.apply {
                visibility = View.VISIBLE
                (drawable as AnimationDrawable).start()
            }
        }
    }

    private fun stopWarning() {
        toneGenerator.stopTone()
        lifecycleScope.launch(Dispatchers.Main) {
            if (fatigueEyeState == FatigueEyeState.NORMAL) {
                binding.drowsiness.apply {
                    text = getString(R.string.normal)
                    setTextColor(ContextCompat.getColor(this@StateActivity, R.color.white))
                }
                binding.drowsinessFitting.apply {
                    text = getString(R.string.normal)
                    setTextColor(ContextCompat.getColor(this@StateActivity, R.color.white))
                }
            }
            binding.warningFilter.apply {
                visibility = View.GONE
                (drawable as AnimationDrawable).stop()
            }
        }
    }

    /* Save (influxDB) */
    private fun updateNetworkWarningUi(insertSucceeded: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (insertSucceeded) {
                binding.networkWarning.visibility = View.GONE
                binding.networkWarningText.visibility = View.GONE
            } else {
                binding.networkWarning.visibility = View.VISIBLE
            }
        }
    }

    private fun saveHeartRate() {
        val heartRate = HeartRate(uid, hr)
        lifecycleScope.launch(Dispatchers.IO) {
            val isInserted = HeartRateDao().insert(heartRate)
            updateNetworkWarningUi(isInserted)
        }
    }

    private fun saveDriverState() {
        val driverState =
            DriverState(uid, blink, totalBlink, rightEye, leftEye, ear, mar, moe, face)
        lifecycleScope.launch(Dispatchers.IO) {
            val isInserted = DriverStateDao().insert(driverState)
            updateNetworkWarningUi(isInserted)
        }
    }

    private fun saveAndWarn() {
        // Save
        if (isFaceValid) saveDriverState()
        if (isHeartRateValid) saveHeartRate()

        // Warn
        if (isHeartRateValid) {
            warningLevel =
                if (hr < HEART_RATE_THRESHOLD)  // TODO: 카메라 정보 반영
                    Integer.min(warningLevel + 1, MAX_WARNING_LEVEL)
                else
                    Integer.max(warningLevel - 1, 0)
            Log.d("State", "Warning Level: $warningLevel")
            if (warningLevel >= MAX_WARNING_LEVEL) {
                Log.d("State", "심박수 경고")
                startWarning()
            } else {
                stopWarning()
            }
        }
    }

    /* View */
    private val timer = Timer(true)
    private lateinit var timerTask: TimerTask

    private fun toNewTimerTask() {
        timerTask = object : TimerTask() {
            override fun run() {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.state.visibility = View.GONE
                    binding.stateFitting.visibility = View.INVISIBLE
                }
            }
        }
    }

    private lateinit var guidelineAnimation: AnimationDrawable

    private lateinit var binding: ActivityStateBinding

    private var timer2 = Timer(true)

    private fun reset() {
        timer2.cancel()
        timerCheck = true
        fittingLevel = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* View */
        binding = ActivityStateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fatigueAlarmPrefs = getSharedPreferences(PREF_FATIGUE_ALARM, MODE_PRIVATE)

        /* Status Bar & Navigation Bar */
        val barColor = ContextCompat.getColor(this, R.color.black)
        with(window) {
            statusBarColor = barColor
            navigationBarColor = barColor
        }
        with(WindowInsetsControllerCompat(window, window.decorView)) {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        TutorialPrefs.runTutorialOnceIfNeeded(this, "state")

        /* Toolbar */
        with(binding.layoutToolbar.toolbarTitle) {
            text = "모니터링"
            setTextColor(ContextCompat.getColor(this@StateActivity, R.color.white))
        }
        val back = ContextCompat.getDrawable(this, R.drawable.ic_back)
        back?.mutate()?.setTint(ContextCompat.getColor(this, R.color.white))
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.toolbar_menu, menu)
                menu.findItem(R.id.menu_date)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        disconnectGatt()
                        finish()
                    }
                }
                return true
            }
        })
        setSupportActionBar(binding.layoutToolbar.toolbar)
        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.black)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(back)
        }

        // 화면 항상 켜짐
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        name = intent.getStringExtra("name") ?: "noname"
        address = intent.getStringExtra("address") ?: ""

        /* User Authentication */
        auth = Firebase.auth
        uid = auth.currentUser!!.uid

        // 심박수 정보 표시창 타이머
        toNewTimerTask()
        timer.schedule(timerTask, SCAN_TIME)

        /* Graph */
        initChart()

        binding.networkWarningIcon.setOnClickListener {
            binding.networkWarningText.visibility =
                if (binding.networkWarningText.visibility == View.GONE) View.VISIBLE
                else View.GONE
        }

        binding.networkWarningText.setOnClickListener { it.visibility = View.GONE }

        /* BLE */
        if (bluetoothAdapter == null) {
            binding.state.visibility = View.GONE
            binding.stateFitting.visibility = View.INVISIBLE
            toast.makeToast(this, "블루투스를 지원하지 않습니다.")
        } else {
            if (bluetoothAdapter!!.isEnabled) {
                if (address.isNotEmpty()) {
                    device = bluetoothAdapter!!.getRemoteDevice(address)
                    isBluetoothOn = true
                }
                Log.d("Device", "$device")
            } else {
                binding.state.visibility = View.GONE
                binding.stateFitting.visibility = View.INVISIBLE
                toast.makeToast(this, "블루투스가 꺼져 있습니다.")
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        val bluetoothChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val action = intent.action
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            Log.i("State", "Bluetooth off")
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            Log.i("State", "Turning Bluetooth off...")

                            binding.warningFilter.visibility = View.GONE
                            binding.state.visibility = View.GONE
                            binding.stateFitting.visibility = View.INVISIBLE
                        }
                        BluetoothAdapter.STATE_ON -> {
                            Log.i("State", "Bluetooth on")

                            if (address.isNotEmpty()) {
                                device = bluetoothAdapter!!.getRemoteDevice(address)
                            }
                            Log.d("Device", "$device")

                            disconnectGatt()
                            connectGatt()
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            Log.i("State", "Turning Bluetooth on...")

                            if (!isCameraOn) {
                                binding.state.visibility = View.VISIBLE
                                binding.stateFitting.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
        registerReceiver(bluetoothChangeReceiver, filter)

        /* Permission - Camera */
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(cameraPermissionList)
        }

        /* FaceMesh */
        prefs = baseContext.getSharedPreferences("faceSetting", Context.MODE_PRIVATE)
        faceMeshSettings = booleanArrayOf(
            prefs.getBoolean("eye", false),
            prefs.getBoolean("eyeBrow", false),
            prefs.getBoolean("eyePupil", false),
            prefs.getBoolean("lib", false),
            prefs.getBoolean("faceMesh", false),
            prefs.getBoolean("faceLine", true)
        )
        faceMeshColors = arrayListOf(
            colorLoad(prefs.getInt("eyeColor", 5)),
            colorLoad(prefs.getInt("eyeBrowColor", 4)),
            colorLoad(prefs.getInt("eyePupilColor", 1)),
            colorLoad(prefs.getInt("libColor", 3)),
            colorLoad(prefs.getInt("faceMeshColor", 1)),
            colorLoad(prefs.getInt("faceLineColor", 1))
        )

        initFaceMesh()
        initGlSurfaceView()
        postGlSurfaceView()
        with(binding.preview) {
            removeAllViewsInLayout()
            addView(glSurfaceView)
            requestLayout()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                saveAndWarn()
                delay(1000)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectGatt()
    }

    override fun onResume() {
        super.onResume()

        fatigueAlarmPrefs = getSharedPreferences(PREF_FATIGUE_ALARM, MODE_PRIVATE)
        if (fatigueEyeState != FatigueEyeState.NORMAL) {
            mainHandler.post { restartAllFatigueAlarms() }
        }

        isCameraOn = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        /* FaceMesh */
        if (isCameraOn) {
            binding.faceFitting.visibility = View.VISIBLE
            postGlSurfaceView()
        } else {
            binding.faceFitting.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)

        stopAllFatigueAlarms()

        postBlinkSequenceJob?.cancel()
        postBlinkSequenceJob = null

        /* FaceMesh */
        if (isCameraOn) {
            glSurfaceView.visibility = View.GONE
            cameraInput.close()
        }
        timer2.cancel()
    }

    override fun onStop() {
        super.onStop()
        disconnectGatt()
    }

    override fun onDestroy() {
        resetLongCloseWindowRunnable?.let { mainHandler.removeCallbacks(it) }
        resetLongCloseWindowRunnable = null
        stopAllFatigueAlarms()
        cancelEyeClosedCountdown()
        eyeWasClosed = false
        toneGenerator.release()
        drowsyEyeTone.release()
        sleepEyeTone.release()
        super.onDestroy()
    }
}