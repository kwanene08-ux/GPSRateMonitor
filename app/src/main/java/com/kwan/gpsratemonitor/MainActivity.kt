package com.kwan.gpsratemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var gpsMonitor: GpsMonitor

    private val permissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gpsMonitor = GpsMonitor(this)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            App(gpsMonitor)
        }
    }

    override fun onDestroy() {
        gpsMonitor.stop()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(gpsMonitor: GpsMonitor) {

    var page by remember { mutableStateOf("home") }
    var snapshot by remember {
        mutableStateOf(gpsMonitor.snapshot())
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = gpsMonitor.snapshot()
            delay(250L)
        }
    }

    MaterialTheme {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("GPS Rate Monitor")
                    }
                )
            }
        ) { pad ->

            Column(
                Modifier
                    .padding(pad)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {

                if (page == "home") {

                    Text(
                        "GPS Update Rate",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        if (snapshot.running)
                            "● กำลังวัด GPS จริง"
                        else
                            "○ ยังไม่ได้เริ่มวัด"
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        StatCard(
                            "Hz",
                            "%.2f".format(snapshot.hz),
                            Modifier.weight(1f)
                        )

                        StatCard(
                            "Accuracy",
                            if (snapshot.accuracyM > 0)
                                "%.1f m".format(snapshot.accuracyM)
                            else
                                "--",
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        StatCard(
                            "Avg",
                            "%.2f".format(snapshot.averageHz),
                            Modifier.weight(1f)
                        )

                        StatCard(
                            "Interval",
                            if (snapshot.intervalMs > 0)
                                "${snapshot.intervalMs} ms"
                            else
                                "--",
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (snapshot.hzHistory.isNotEmpty()) {

                        Text(
                            "Hz แบบ Real-time",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(6.dp))

                        HzGraph(
                            snapshot.hzHistory,
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                if (snapshot.running) {
                                    gpsMonitor.stop()
                                } else {
                                    gpsMonitor.start()
                                }

                                snapshot =
                                    gpsMonitor.snapshot()

                            } catch (_: SecurityException) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Icon(
                            if (snapshot.running)
                                Icons.Default.Stop
                            else
                                Icons.Default.GpsFixed,
                            null
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            if (snapshot.running)
                                "หยุดวัด GPS"
                            else
                                "เริ่มวัด GPS"
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            page = "details"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Analytics, null)
                        Spacer(Modifier.width(8.dp))
                        Text("รายละเอียด GPS / GNSS")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            page = "update"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SystemUpdate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ตรวจสอบอัปเดตแอป")
                    }

                } else if (page == "details") {

                    Text(
                        "GPS / GNSS",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    Detail("Updates", "${snapshot.updateCount}")
                    Detail("GPS drops", "${snapshot.dropCount}")
                    Detail(
                        "Min Hz",
                        "%.2f".format(snapshot.minHz)
                    )
                    Detail(
                        "Max Hz",
                        "%.2f".format(snapshot.maxHz)
                    )
                    Detail(
                        "Average Hz",
                        "%.2f".format(snapshot.averageHz)
                    )
                    Detail(
                        "Accuracy",
                        if (snapshot.accuracyM > 0)
                            "%.1f m".format(snapshot.accuracyM)
                        else "--"
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Satellites",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Detail(
                        "Visible",
                        "${snapshot.satellitesVisible}"
                    )
                    Detail(
                        "Used in fix",
                        "${snapshot.satellitesUsed}"
                    )
                    Detail(
                        "GPS",
                        "${snapshot.gpsCount}"
                    )
                    Detail(
                        "GLONASS",
                        "${snapshot.glonassCount}"
                    )
                    Detail(
                        "Galileo",
                        "${snapshot.galileoCount}"
                    )
                    Detail(
                        "BeiDou",
                        "${snapshot.beidouCount}"
                    )
                    Detail(
                        "Other",
                        "${snapshot.otherCount}"
                    )
                    Detail(
                        "Average C/N0",
                        "%.1f dB-Hz".format(snapshot.cn0Average)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            page = "home"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("กลับหน้าหลัก")
                    }

                } else {

                    UpdatePage(
                        pageBack = {
                            page = "home"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UpdatePage(pageBack: () -> Unit) {

    var state by remember {
        mutableStateOf<UpdateState>(UpdateState.Idle)
    }

    var info by remember {
        mutableStateOf<UpdateInfo?>(null)
    }

    val scope = rememberCoroutineScope()
    val context =
        androidx.compose.ui.platform.LocalContext.current

    Text(
        "ศูนย์อัปเดตแอป",
        style = MaterialTheme.typography.headlineSmall
    )

    Spacer(Modifier.height(12.dp))

    Text(
        "ติดตั้งอยู่: ${
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    0
                ).versionName ?: "ไม่ทราบ"
            } catch (_: Exception) {
                "ไม่ทราบ"
            }
        }"
    )

    Spacer(Modifier.height(12.dp))

    Text(
        when (val current = state) {

            UpdateState.Idle ->
                "กดตรวจสอบเพื่อค้นหาเวอร์ชันใหม่"

            UpdateState.Checking ->
                "กำลังตรวจสอบ..."

            UpdateState.UpToDate ->
                "✓ เป็นเวอร์ชันล่าสุด"

            is UpdateState.Available ->
                "มีอัปเดต ${info?.versionName}"

            is UpdateState.Downloading ->
                "กำลังดาวน์โหลด ${current.progress}%"

            UpdateState.Ready ->
                "ตรวจสอบไฟล์แล้ว พร้อมติดตั้ง"

            is UpdateState.Error ->
                "ผิดพลาด: ${current.message}"
        }
    )

    Spacer(Modifier.height(12.dp))

    info?.let { updateInfo ->

        if (updateInfo.isUpdateAvailable) {

            Text(
                "เวอร์ชันใหม่: ${updateInfo.versionName}"
            )

            updateInfo.releaseNotes.forEach {
                Text("• $it")
            }
        }
    }

    val downloading =
        state as? UpdateState.Downloading

    if (downloading != null) {

        LinearProgressIndicator(
            progress = {
                downloading.progress / 100f
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
    }

    Button(
        onClick = {

            scope.launch {

                state = UpdateState.Checking

                val result =
                    UpdateManager.check()

                info = result

                state =
                    when {
                        result.error != null ->
                            UpdateState.Error(
                                result.error
                            )

                        result.isUpdateAvailable ->
                            UpdateState.Available

                        else ->
                            UpdateState.UpToDate
                    }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled =
            state !is UpdateState.Checking &&
            state !is UpdateState.Downloading
    ) {

        Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("ตรวจสอบอัปเดต")
    }

    if (
        info?.isUpdateAvailable == true &&
        state is UpdateState.Available
    ) {

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {

                scope.launch {

                    val updateInfo =
                        info ?: return@launch

                    state =
                        UpdateState.Downloading(0)

                    val result =
                        UpdateManager.downloadAndVerify(
                            context,
                            updateInfo
                        ) { progress ->

                            state =
                                UpdateState.Downloading(
                                    progress
                                )
                        }

                    state =
                        if (result.ok) {
                            UpdateState.Ready
                        } else {
                            UpdateState.Error(
                                result.error
                                    ?: "ดาวน์โหลดไม่สำเร็จ"
                            )
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("ดาวน์โหลดอัปเดต")
        }
    }

    if (state is UpdateState.Ready) {

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                UpdateManager.install(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(
                Icons.Default.InstallMobile,
                null
            )

            Spacer(Modifier.width(8.dp))

            Text("ติดตั้งอัปเดต")
        }
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = pageBack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("กลับหน้าหลัก")
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(modifier) {

        Column(
            Modifier.padding(12.dp)
        ) {

            Text(
                title,
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                value,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun Detail(
    title: String,
    value: String
) {

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(title)

        Text(
            value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun HzGraph(
    values: List<Float>,
    modifier: Modifier = Modifier
) {

    Canvas(modifier) {

        if (values.size < 2) return@Canvas

        val maxValue =
            maxOf(
                values.maxOrNull() ?: 1f,
                1f
            )

        val path = Path()

        values.forEachIndexed { index, value ->

            val x =
                if (values.size == 1)
                    0f
                else
                    size.width *
                        index /
                        (values.size - 1)

            val y =
                size.height -
                    (value / maxValue)
                        .coerceIn(0f, 1f) *
                    size.height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
    color = Color(0xFF00E5FF),
path = path,
    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
)

        values.takeLast(1).forEach { value ->

            val x = size.width

            val y =
                size.height -
                    (value / maxValue)
                        .coerceIn(0f, 1f) *
                    size.height

            drawCircle(
    color = Color(0xFF00E5FF),
radius = 6f,
                center = Offset(x, y
)
            )
        }
    }
}

sealed class UpdateState {

    data object Idle : UpdateState()

    data object Checking : UpdateState()

    data object UpToDate : UpdateState()

    data object Available : UpdateState()

    data class Downloading(
        val progress: Int
    ) : UpdateState()

    data object Ready : UpdateState()

    data class Error(
        val message: String
    ) : UpdateState()
}
