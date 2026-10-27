package com.example.careguard.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()
    private val auth = FirebaseAuth.getInstance()
    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()
    private val _emailVerified = MutableStateFlow(auth.currentUser?.isEmailVerified == true)
    val emailVerified: StateFlow<Boolean> = _emailVerified.asStateFlow()
    private val _state = MutableStateFlow(AsyncState())
    val state: StateFlow<AsyncState> = _state.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener {
        _user.value = it.currentUser
        _emailVerified.value = it.currentUser?.isEmailVerified == true
    }

    init {
        auth.addAuthStateListener(listener)
    }

    fun signIn(email: String, password: String) = runAuth { repository.signIn(email, password) }

    fun signUp(displayName: String, email: String, password: String) =
        runAuth { repository.signUp(displayName, email, password) }

    fun signOut() = repository.signOut()

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching { requireNotNull(auth.currentUser) { "로그인이 필요합니다." }.sendEmailVerification().await() }
                .onSuccess { _state.value = AsyncState(message = "인증 이메일을 다시 보냈습니다.") }
                .onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "인증 이메일을 보내지 못했습니다.", isError = true) }
        }
    }

    fun refreshEmailVerification() {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching {
                val current = requireNotNull(auth.currentUser) { "로그인이 필요합니다." }
                current.reload().await()
                auth.currentUser?.also {
                    _user.value = it
                    _emailVerified.value = it.isEmailVerified
                }
            }.onSuccess { refreshed ->
                _state.value = AsyncState(
                    message = if (refreshed?.isEmailVerified == true) "이메일 인증이 확인되었습니다." else "이메일 인증이 아직 확인되지 않았습니다. 받은 편지함을 확인해 주세요.",
                    isError = refreshed?.isEmailVerified != true
                )
            }.onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "인증 상태를 확인하지 못했습니다.", isError = true) }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun runAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching { block() }
                .onSuccess { _state.value = AsyncState(message = "완료되었습니다.") }
                .onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "요청을 처리하지 못했습니다.", isError = true) }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
    }
}

class SafetyViewModel : ViewModel() {
    private val repository = AlertRepository()
    private val _state = MutableStateFlow(AsyncState())
    val state: StateFlow<AsyncState> = _state.asStateFlow()

    fun sendSos() = send { repository.createAlert(AlertType.SOS, "사용자가 SOS 긴급 알림을 요청했습니다.") }

    fun sendVoiceAlert(transcript: String) = send {
        repository.createAlert(
            type = AlertType.VOICE,
            message = "위험 음성이 확인되어 긴급 알림을 요청했습니다.",
            transcript = transcript
        )
    }

    fun uploadEvidenceVideo(file: File, labels: List<String>) = send {
        repository.createAlert(
            type = AlertType.VIDEO,
            message = "사용자가 현장 영상을 전송했습니다.",
            labels = labels,
            videoUri = Uri.fromFile(file)
        )
        file.delete()
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun send(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching { block() }
                .onSuccess { _state.value = AsyncState(message = "보호자 알림 요청을 전송했습니다.") }
                .onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "전송에 실패했습니다.", isError = true) }
        }
    }
}

class GuardianViewModel : ViewModel() {
    private val repository = GuardianRepository()
    private val _guardians = MutableStateFlow<List<GuardianLink>>(emptyList())
    val guardians: StateFlow<List<GuardianLink>> = _guardians.asStateFlow()
    private val _invites = MutableStateFlow<List<GuardianInvite>>(emptyList())
    val invites: StateFlow<List<GuardianInvite>> = _invites.asStateFlow()
    private val _pairing = MutableStateFlow<PiPairingCredentials?>(null)
    val pairing: StateFlow<PiPairingCredentials?> = _pairing.asStateFlow()
    private val _state = MutableStateFlow(AsyncState())
    val state: StateFlow<AsyncState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.observeMyGuardians().collectLatest { _guardians.value = it } }
        viewModelScope.launch { repository.observeReceivedInvites().collectLatest { _invites.value = it } }
    }

    fun invite(email: String) = runRequest("보호자 초대를 보냈습니다.") { repository.inviteGuardian(email) }
    fun accept(inviteId: String) = runRequest("보호자 요청을 수락했습니다.") { repository.acceptInvite(inviteId) }

    fun makePiPairing(deviceName: String) {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching { repository.createPiPairing(deviceName) }
                .onSuccess {
                    _pairing.value = it
                    _state.value = AsyncState(message = "라즈베리 파이용 공유 비밀키를 만들었습니다.")
                }
                .onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "페어링 생성에 실패했습니다.", isError = true) }
        }
    }

    fun clearPairing() { _pairing.value = null }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    private fun runRequest(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = AsyncState(isLoading = true)
            runCatching { block() }
                .onSuccess { _state.value = AsyncState(message = successMessage) }
                .onFailure { _state.value = AsyncState(message = it.localizedMessage ?: "요청에 실패했습니다.", isError = true) }
        }
    }
}
