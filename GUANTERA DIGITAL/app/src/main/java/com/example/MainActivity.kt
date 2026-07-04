package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DashboardScreen
import com.example.ui.TermsAndConditionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WalletViewModel
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {

    enum class BiometricAuthState {
        IDLE,
        AUTHENTICATING,
        SUCCESS,
        FAILED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val walletViewModel: WalletViewModel = viewModel()
            val isDarkMode by walletViewModel.isDarkMode.collectAsState()
            val hasAcceptedTerms by walletViewModel.hasAcceptedTerms.collectAsState()

            var authState by remember { mutableStateOf(BiometricAuthState.IDLE) }
            var authErrorMessage by remember { mutableStateOf("") }

            val context = this
            val executor = remember { ContextCompat.getMainExecutor(context) }

            // Trigger biometric prompt when terms are accepted and auth state is IDLE
            LaunchedEffect(hasAcceptedTerms, authState) {
                if (hasAcceptedTerms == true && authState == BiometricAuthState.IDLE) {
                    authState = BiometricAuthState.AUTHENTICATING
                    showBiometricPrompt(
                        activity = context,
                        executor = executor,
                        onSuccess = {
                            authState = BiometricAuthState.SUCCESS
                        },
                        onFailure = { error ->
                            authErrorMessage = error
                            authState = BiometricAuthState.FAILED
                        }
                    )
                }
            }

            MyApplicationTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (hasAcceptedTerms) {
                        null -> {
                            // Loading state: display an elegant premium loader
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        false -> {
                            // Terms and Conditions onboarding flow with name entry
                            TermsAndConditionsScreen(
                                onAccept = { enteredName ->
                                    walletViewModel.setUserName(enteredName)
                                    walletViewModel.acceptTerms()
                                }
                            )
                        }
                        true -> {
                            when (authState) {
                                BiometricAuthState.AUTHENTICATING -> {
                                    // Elegant verification overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Verificando Identidad...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                                BiometricAuthState.SUCCESS -> {
                                    // Fully active automotive smart-wallet dashboard
                                    DashboardScreen(
                                        viewModel = walletViewModel,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                BiometricAuthState.FAILED -> {
                                    // Premium Access Denied Screen with retry option
                                    AccessDeniedScreen(
                                        errorMessage = authErrorMessage,
                                        onRetry = {
                                            authState = BiometricAuthState.IDLE
                                        }
                                    )
                                }
                                BiometricAuthState.IDLE -> {
                                    // Will transition to AUTHENTICATING immediately via LaunchedEffect
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(
        activity: FragmentActivity,
        executor: Executor,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Requerida")
            .setSubtitle("Acceda a su Guantera Digital")
            .setDescription("Use la biometría de su dispositivo o PIN de seguridad para continuar.")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    activity.runOnUiThread {
                        onSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activity.runOnUiThread {
                        onFailure(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Android OS triggers onAuthenticationFailed for non-fatal/transient matches.
                    // Ultimate cancellation or failure will yield onAuthenticationError.
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun AccessDeniedScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock Icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ACCESO DENEGADO",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                    letterSpacing = 1.5.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No se pudo verificar su identidad.\nMotivo: ${if (errorMessage.isEmpty()) "Cancelado por el usuario" else errorMessage}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    lineHeight = 22.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "REINTENTAR AUTENTICACIÓN",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}
