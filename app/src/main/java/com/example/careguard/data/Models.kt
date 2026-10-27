package com.example.careguard.data

enum class AlertType(val wireName: String) {
    SOS("SOS"),
    VOICE("VOICE"),
    VIDEO("VIDEO"),
    FALL("FALL")
}

data class GuardianLink(
    val guardianUid: String,
    val guardianEmail: String,
    val guardianName: String,
    val status: String
)

data class GuardianInvite(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val status: String
)

data class PiPairingCredentials(
    val deviceId: String,
    val deviceName: String,
    val sharedSecret: String
)

data class AsyncState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
