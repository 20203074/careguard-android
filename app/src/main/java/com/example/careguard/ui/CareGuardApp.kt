package com.example.careguard.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.careguard.data.AuthViewModel
import com.example.careguard.data.DeviceRepository
import com.example.careguard.data.GuardianViewModel
import com.example.careguard.data.SafetyViewModel
import com.example.careguard.ui.auth.AuthScreen
import com.example.careguard.ui.camera.CameraScreen
import com.example.careguard.ui.home.HomeScreen
import com.example.careguard.ui.profile.ProfileScreen
import com.example.careguard.ui.voice.VoiceScreen
import com.example.careguard.ui.auth.VerifyEmailScreen

private object Route {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val VOICE = "voice"
    const val PROFILE = "profile"
}

@Composable
fun CareGuardApp(authViewModel: AuthViewModel = viewModel()) {
    val user by authViewModel.user.collectAsStateWithLifecycle()
    val emailVerified by authViewModel.emailVerified.collectAsStateWithLifecycle()
    if (user == null) {
        val state by authViewModel.state.collectAsStateWithLifecycle()
        AuthScreen(
            state = state,
            onSignIn = authViewModel::signIn,
            onSignUp = authViewModel::signUp
        )
    } else if (!emailVerified) {
        val state by authViewModel.state.collectAsStateWithLifecycle()
        VerifyEmailScreen(
            email = user?.email.orEmpty(),
            state = state,
            onResend = authViewModel::resendVerificationEmail,
            onRefresh = authViewModel::refreshEmailVerification,
            onSignOut = authViewModel::signOut
        )
    } else {
        AuthenticatedApp(
            userId = user!!.uid,
            displayName = user?.displayName ?: user?.email?.substringBefore('@') ?: "사용자",
            onSignOut = authViewModel::signOut
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(userId: String, displayName: String, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val safetyViewModel: SafetyViewModel = viewModel(key = "safety-$userId")
    val guardianViewModel: GuardianViewModel = viewModel(key = "guardian-$userId")
    val safetyState by safetyViewModel.state.collectAsStateWithLifecycle()
    val guardianState by guardianViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        runCatching { DeviceRepository().registerCurrentInstallation() }
    }
    LaunchedEffect(safetyState.message) {
        safetyState.message?.let {
            snackbarHostState.showSnackbar(it)
            safetyViewModel.consumeMessage()
        }
    }
    LaunchedEffect(guardianState.message) {
        guardianState.message?.let {
            snackbarHostState.showSnackbar(it)
            guardianViewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    displayName = displayName,
                    isSending = safetyState.isLoading,
                    onOpenCamera = { navController.navigate(Route.CAMERA) },
                    onOpenVoice = { navController.navigate(Route.VOICE) },
                    onOpenProfile = { navController.navigate(Route.PROFILE) },
                    onSendSos = safetyViewModel::sendSos
                )
            }
            composable(Route.CAMERA) {
                CameraScreen(
                    isUploading = safetyState.isLoading,
                    onBack = { navController.popBackStack() },
                    onUpload = safetyViewModel::uploadEvidenceVideo
                )
            }
            composable(Route.VOICE) {
                VoiceScreen(
                    isSending = safetyState.isLoading,
                    onBack = { navController.popBackStack() },
                    onConfirmDanger = safetyViewModel::sendVoiceAlert
                )
            }
            composable(Route.PROFILE) {
                ProfileScreen(
                    viewModel = guardianViewModel,
                    onBack = { navController.popBackStack() },
                    onSignOut = onSignOut
                )
            }
        }
    }
}
