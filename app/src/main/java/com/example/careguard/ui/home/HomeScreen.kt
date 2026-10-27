package com.example.careguard.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    displayName: String,
    isSending: Boolean,
    onOpenCamera: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenProfile: () -> Unit,
    onSendSos: () -> Unit
) {
    var showSosConfirm by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("안전 홈") },
            actions = {
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "마이페이지")
                }
            }
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("$displayName 님, 연결 상태를 확인하세요.", style = MaterialTheme.typography.headlineSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("라즈베리 파이 낙상 탐지", style = MaterialTheme.typography.titleMedium)
                    Text("낙상 이벤트는 Pi가 Firebase 안전 서버에 기록하면 승인된 보호자에게 자동 전송됩니다.")
                    OutlinedButton(onClick = onOpenProfile) { Text("Pi 페어링 정보 보기") }
                }
            }
            ElevatedButton(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Videocam, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("현장 영상 녹화·전송")
            }
            ElevatedButton(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("음성 인식으로 도움 요청")
            }
            Button(
                onClick = { showSosConfirm = true },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("보호자에게 SOS 보내기")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("이 기기 알림 허용") }
            }
            Text(
                "음성 인식은 상시 감청하지 않습니다. 사용자가 시작한 짧은 세션의 최종 결과만 확인하며, 위험 문구가 감지되어도 전송 전 다시 확인합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSosConfirm) {
        AlertDialog(
            onDismissRequest = { showSosConfirm = false },
            title = { Text("SOS를 전송할까요?") },
            text = { Text("승인된 보호자에게 현재 사용자의 긴급 요청이 전달됩니다.") },
            confirmButton = {
                Button(onClick = { showSosConfirm = false; onSendSos() }) { Text("전송") }
            },
            dismissButton = { OutlinedButton(onClick = { showSosConfirm = false }) { Text("취소") } }
        )
    }
}
