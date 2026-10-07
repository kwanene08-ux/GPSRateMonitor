package com.kwan.gpsratemonitor

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        setContent { App() }
    }
}

@Composable
fun App() {
    var page by remember { mutableStateOf("home") }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("GPS Rate Monitor") }) }) { pad ->
            Column(Modifier.padding(pad).padding(20.dp).fillMaxSize()) {
                if (page == "home") {
                    Text("GPS Update Rate", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {}, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("อัปเดต GPS")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { page = "update" }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("ตรวจสอบอัปเดตแอป")
                    }
                } else {
                    Text("ศูนย์อัปเดตแอป", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("ติดตั้งอยู่: 1.1.0")
                    Spacer(Modifier.height(12.dp))
                    Text(when (state) {
                        UpdateState.Idle -> "กดตรวจสอบเพื่อค้นหาเวอร์ชันใหม่"
                        UpdateState.Checking -> "กำลังตรวจสอบ..."
                        UpdateState.UpToDate -> "✓ เป็นเวอร์ชันล่าสุด"
                        is UpdateState.Available -> "มีอัปเดต ${info?.versionName}"
                        is UpdateState.Downloading -> "กำลังดาวน์โหลด ${state.progress}%"
                        UpdateState.Ready -> "ตรวจสอบไฟล์แล้ว พร้อมติดตั้ง"
                        is UpdateState.Error -> "ผิดพลาด: ${state.message}"
                    })
                    Spacer(Modifier.height(12.dp))
                    info?.let { if (it.isUpdateAvailable) {
                        Text("เวอร์ชันใหม่: ${it.versionName}")
                        it.releaseNotes.forEach { n -> Text("• $n") }
                    }}
                    if (state is UpdateState.Downloading) {
                        LinearProgressIndicator(progress = { state.progress / 100f }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(onClick = {
                        scope.launch {
                            state = UpdateState.Checking
                            val r = UpdateManager.check()
                            info = r
                            state = if (r.error != null) UpdateState.Error(r.error)
                            else if (r.isUpdateAvailable) UpdateState.Available else UpdateState.UpToDate
                        }
                    }, Modifier.fillMaxWidth(), enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("ตรวจสอบอัปเดต")
                    }
                    if (info?.isUpdateAvailable == true && state is UpdateState.Available) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                val i = info ?: return@launch
                                state = UpdateState.Downloading(0)
                                val r = UpdateManager.downloadAndVerify(context, i) { p -> state = UpdateState.Downloading(p) }
                                state = if (r.ok) UpdateState.Ready else UpdateState.Error(r.error ?: "ดาวน์โหลดไม่สำเร็จ")
                            }
                        }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("ดาวน์โหลดอัปเดต") }
                    }
                    if (state is UpdateState.Ready) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { UpdateManager.install(context) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("ติดตั้งอัปเดต")
                        }
                    }
                }
            }
        }
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data object Available : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data object Ready : UpdateState()
    data class Error(val message: String) : UpdateState()
}
