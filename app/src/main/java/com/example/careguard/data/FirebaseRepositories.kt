package com.example.careguard.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

private const val FUNCTIONS_REGION = "asia-northeast3"

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun signUp(displayName: String, email: String, password: String) {
        require(displayName.trim().length >= 2) { "이름은 두 글자 이상 입력해 주세요." }
        require(password.length >= 6) { "비밀번호는 6자 이상이어야 합니다." }

        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        ensureProfile(displayName.trim())
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        ensureProfile(null)
    }

    suspend fun ensureProfile(preferredName: String?) {
        val user = requireNotNull(auth.currentUser) { "로그인이 필요합니다." }
        val ref = db.collection("users").document(user.uid)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) {
            ref.set(
                mapOf(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                    "displayName" to (preferredName ?: user.displayName ?: "사용자"),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun signOut() = auth.signOut()
}

class DeviceRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** FID는 문서 ID로, 최신 FCM 등록 토큰은 안전한 서버 전송 대상으로 저장한다. */
    suspend fun registerCurrentInstallation() {
        val uid = auth.currentUser?.uid ?: return
        val fid = FirebaseInstallations.getInstance().id.await()
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(uid).collection("devices").document(fid)
            .set(
                mapOf(
                    "fid" to fid,
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }
}

class GuardianRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
) {
    private fun uid(): String = requireNotNull(auth.currentUser?.uid) { "로그인이 필요합니다." }

    fun observeMyGuardians(): Flow<List<GuardianLink>> = callbackFlow {
        val registration = db.collection("users").document(uid()).collection("guardians")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val links = value?.documents.orEmpty().map { document ->
                    GuardianLink(
                        guardianUid = document.id,
                        guardianEmail = document.getString("guardianEmail").orEmpty(),
                        guardianName = document.getString("guardianName").orEmpty(),
                        status = document.getString("status").orEmpty()
                    )
                }.sortedBy { it.guardianName.ifBlank { it.guardianEmail } }
                trySend(links)
            }
        awaitClose { registration.remove() }
    }

    fun observeReceivedInvites(): Flow<List<GuardianInvite>> = callbackFlow {
        val registration = db.collection("guardianInvites")
            .whereEqualTo("guardianUid", uid())
            .addSnapshotListener { value, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val invites = value?.documents.orEmpty()
                    .map { document ->
                        GuardianInvite(
                            id = document.id,
                            patientUid = document.getString("patientUid").orEmpty(),
                            patientName = document.getString("patientName").orEmpty(),
                            status = document.getString("status").orEmpty()
                        )
                    }
                    .filter { it.status == "PENDING" }
                trySend(invites)
            }
        awaitClose { registration.remove() }
    }

    suspend fun inviteGuardian(email: String) {
        val normalized = email.trim().lowercase(Locale.ROOT)
        require(normalized.contains("@")) { "보호자의 이메일 주소를 확인해 주세요." }
        functions.getHttpsCallable("createGuardianInvite")
            .call(mapOf("guardianEmail" to normalized))
            .await()
    }

    suspend fun acceptInvite(inviteId: String) {
        functions.getHttpsCallable("acceptGuardianInvite")
            .call(mapOf("inviteId" to inviteId))
            .await()
    }

    suspend fun createPiPairing(deviceName: String): PiPairingCredentials {
        require(deviceName.trim().isNotEmpty()) { "라즈베리 파이 이름을 입력해 주세요." }
        @Suppress("UNCHECKED_CAST")
        val data = functions.getHttpsCallable("createPiPairing")
            .call(mapOf("deviceName" to deviceName.trim()))
            .await()
            .data as? Map<String, Any?> ?: error("페어링 정보를 받지 못했습니다.")

        return PiPairingCredentials(
            deviceId = data["deviceId"] as? String ?: error("deviceId가 없습니다."),
            deviceName = data["deviceName"] as? String ?: deviceName,
            sharedSecret = data["sharedSecret"] as? String ?: error("공유 비밀키가 없습니다.")
        )
    }
}

class AlertRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    suspend fun createAlert(
        type: AlertType,
        message: String,
        transcript: String? = null,
        labels: List<String> = emptyList(),
        videoUri: Uri? = null
    ) {
        val uid = requireNotNull(auth.currentUser?.uid) { "로그인이 필요합니다." }
        val alertRef = db.collection("alerts").document()
        val videoPath = if (videoUri != null) {
            val path = "evidence/$uid/${alertRef.id}.mp4"
            val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
            storage.reference.child(path).putFile(videoUri, metadata).await()
            path
        } else {
            null
        }

        val payload = mutableMapOf<String, Any>(
            "patientId" to uid,
            "createdBy" to uid,
            "source" to "android",
            "type" to type.wireName,
            "message" to message.take(120),
            "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp()
        )
        transcript?.let { payload["transcript"] = it.take(300) }
        if (labels.isNotEmpty()) payload["labels"] = labels.take(10)
        videoPath?.let { payload["videoPath"] = it }
        alertRef.set(payload).await()
    }
}
