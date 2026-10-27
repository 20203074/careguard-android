package com.example.careguard.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.careguard.data.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: GuardianViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val guardians by viewModel.guardians.collectAsStateWithLifecycle()
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var guardianEmail by remember { mutableStateOf("") }
    var piName by remember { mutableStateOf("거실 라즈베리 파이") }
    var showPairDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("마이페이지") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("보호자 등록", style = MaterialTheme.typography.titleLarge)
                    Text("보호자가 먼저 같은 앱에서 가입해야 합니다. 이메일로 요청을 보내고, 보호자가 수락해야 알림을 받습니다.")
                    OutlinedTextField(
                        value = guardianEmail,
                        onValueChange = { guardianEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("보호자 이메일") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Button(
                        onClick = { viewModel.invite(guardianEmail); guardianEmail = "" },
                        enabled = guardianEmail.isNotBlank() && !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("보호자 요청 보내기") }
                    if (guardians.isEmpty()) {
                        Text("아직 등록된 보호자가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        guardians.forEach { guardian ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(guardian.guardianName.ifBlank { guardian.guardianEmail })
                                    Text(guardian.guardianEmail, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (guardian.status == "ACCEPTED") "승인됨" else "수락 대기", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (invites.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("받은 보호자 요청", style = MaterialTheme.typography.titleLarge)
                        invites.forEach { invite ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${invite.patientName} 님의 보호자가 되기")
                                Button(onClick = { viewModel.accept(invite.id) }, enabled = !state.isLoading) { Text("수락") }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("라즈베리 파이 낙상 감지기", style = MaterialTheme.typography.titleLarge)
                    Text("Pi에서 이미 탐지한 낙상 이벤트를 안전 서버에 보낼 수 있도록 기기별 공유 비밀키를 한 번 발급합니다.")
                    OutlinedButton(onClick = { showPairDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pi 페어링 키 만들기")
                    }
                    Text("공유 비밀키는 Pi 설정 파일에만 입력하고, 메신저나 소스 저장소에 올리지 마세요.", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
        }
    }

    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Pi 페어링 키 만들기") },
            text = {
                OutlinedTextField(
                    value = piName,
                    onValueChange = { piName = it },
                    label = { Text("기기 이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { showPairDialog = false; viewModel.makePiPairing(piName) }) { Text("발급") }
            },
            dismissButton = { OutlinedButton(onClick = { showPairDialog = false }) { Text("취소") } }
        )
    }

    pairing?.let { credentials ->
        AlertDialog(
            onDismissRequest = viewModel::clearPairing,
            title = { Text("Pi 설정에 한 번만 복사하세요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DEVICE_ID", style = MaterialTheme.typography.labelLarge)
                    Text(credentials.deviceId, style = MaterialTheme.typography.bodySmall)
                    Text("SHARED_SECRET", style = MaterialTheme.typography.labelLarge)
                    Text(credentials.sharedSecret, style = MaterialTheme.typography.bodySmall)
                    Text("이 대화창을 닫으면 공유 비밀키를 앱에서 다시 보여 주지 않습니다.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(onClick = viewModel::clearPairing) { Text("안전하게 기록했습니다") } }
        )
    }
}
