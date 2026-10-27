package com.example.careguard.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.careguard.data.AsyncState

@Composable
fun VerifyEmailScreen(
    email: String,
    state: AsyncState,
    onResend: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("이메일 인증이 필요합니다", style = MaterialTheme.typography.headlineSmall)
                Text("보호자 연결과 안전 정보 접근을 보호하기 위해 가입한 이메일 소유를 확인합니다.")
                Text(email, style = MaterialTheme.typography.titleMedium)
                state.message?.let {
                    Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Button(onClick = onRefresh, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    else Text("인증 완료 확인")
                }
                OutlinedButton(onClick = onResend, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text("인증 이메일 다시 보내기")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
            }
        }
    }
}
