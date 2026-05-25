package world.saloris.donoff.util

import world.saloris.donoff.R

/* Toolbar */
// fragment to (title, back display)
val FRAGMENT_INFO = mapOf(
    R.id.registerFragment to Pair(R.string.register, true),
    R.id.mainFragment to Pair(R.string.app_name, false),
    R.id.scanFragment to Pair(R.string.monitor, true),
    R.id.graphFragment to Pair(R.string.graph, true),
    R.id.graphHrFragment to Pair(R.string.graph_hr, true),
    R.id.settingsFragment to Pair(R.string.setting, true),
    R.id.accountFragment to Pair(R.string.account, true)
)

/* BLE */
const val HEART_RATE_SERVICE_STRING = "0000180d-0000-1000-8000-00805f9b34fb"
const val HEART_RATE_MEASUREMENT_CHARACTERISTIC_STRING = "00002a37-0000-1000-8000-00805f9b34fb"
const val CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_STRING =
    "00002902-0000-1000-8000-00805f9b34fb"
const val SCAN_TIME = 10000L

const val BATTERY_SERVICE_STRING = "0000180f-0000-1000-8000-00805f9b34fb"
const val BATTERY_LEVEL_CHARACTERISTIC_STRING = "00002a19-0000-1000-8000-00805f9b34fb"

const val HEART_RATE_THRESHOLD = 67
const val MAX_WARNING_LEVEL = 10
const val MAX_FITTING_LEVEL = 10

/* Eye / fatigue (StateActivity) */
const val EAR_CLOSED_THRESHOLD = 0.13f
const val EYE_COUNT_STEP_MS = 1000L          // Blink → 1 → 2 → Long Closed Eyes 단계당 간격
const val EYE_LONG_CLOSE_WINDOW_MS = 60_000L // 1분 내 2번째 Long Closed Eyes → SLEEP 판정

/* Fatigue alarm (StateActivity + settings dialog) */
const val PREF_FATIGUE_ALARM = "fatigueAlarm"
const val KEY_FATIGUE_ALARM_SCREEN_BLINK = "screen_blink"
const val KEY_FATIGUE_ALARM_VIBRATION = "vibration"
const val KEY_FATIGUE_ALARM_SOUND = "sound"
// 화면 깜빡임 간격 (졸음: 느림 / 수면: 매우 빠름)
const val FATIGUE_BLINK_INTERVAL_DROWSY_MS = 750L
const val FATIGUE_BLINK_INTERVAL_SLEEP_MS = 180L

// 반복 경보음 간격 (졸음: 2.5초 / 수면: 0.8초)
const val FATIGUE_SOUND_INTERVAL_DROWSY_MS = 2500L
const val FATIGUE_SOUND_INTERVAL_SLEEP_MS = 800L

// 반복 진동 간격 (졸음: 3초 / 수면: 1.2초)
const val FATIGUE_VIB_INTERVAL_DROWSY_MS = 3000L
const val FATIGUE_VIB_INTERVAL_SLEEP_MS = 1200L

/* FaceMesh */
// Color
val RED_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
val BLACK_COLOR = floatArrayOf(0.9f, 0.9f, 0.9f, 1f)
val BLUE_COLOR = floatArrayOf(0.0f, 0.5f, 0.9f, 0.5f)
val ORANGE_COLOR = floatArrayOf(1f, 0.9f, 0.5f, 0.2f)
val WHITE_COLOR = floatArrayOf(0.75f, 0.75f, 0.75f, 0.5f)
val GREEN_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)

// 기본 마스킹
const val TESSELATION_THICKNESS = 5

// 오른 눈
const val RIGHT_EYE_THICKNESS = 8
const val RIGHT_EYEBROW_THICKNESS = 8

// 왼 눈
const val LEFT_EYE_THICKNESS = 8
const val LEFT_EYEBROW_THICKNESS = 8

// 테두리 입술, 얼굴 white
const val FACE_OVAL_THICKNESS = 8
const val LIPS_THICKNESS = 8
const val VERTEX_SHADER = "uniform mat4 uProjectionMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "   gl_Position = uProjectionMatrix * vPosition;" +
        "}"
const val FRAGMENT_SHADER = "precision mediump float;" +
        "uniform vec4 uColor;" +
        "void main() {" +
        "   gl_FragColor = uColor;" +
        "}"

val MOUTH_INDEX = listOf(78, 82, 13, 312, 30, 317, 14, 87, 61, 291) // 입 8 / 입술 2 (l, r)
val LEFT_EYE_INDEX = listOf(33, 158, 159, 133, 145, 153, 468) // 눈 6 (0: l, 2: u, 4: d) / 홍채 1
val RIGHT_EYE_INDEX = listOf(362, 385, 386, 263, 374, 380, 473) // 눈 6 (2: u, 3: r, 4: d) / 홍채 1
val BASE_INDEX = listOf(151, 6, 199) // 이마, 눈 중심, 턱
val FACE_PITCHING = listOf(10, 152, 237, 356)// 얼굴 상(이마), 하(턱), 좌(왼쪽 귀), 우(오른쪽 귀)

/* Graph */
const val INITIAL_ENTRY = 300

/* influxDB — clone 후 본인 Influx org / bucket 으로 수정 */
const val BUCKET = "DONOFF"
const val ORGANIZATION = "YOUR_INFLUX_ORG_ID"