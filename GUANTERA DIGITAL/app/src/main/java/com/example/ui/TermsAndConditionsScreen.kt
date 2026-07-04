package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StateVigenteBg
import com.example.ui.theme.StateVigenteDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onAccept: (String) -> Unit
) {
    var isChecked by remember { mutableStateOf(false) }
    var nameState by remember { mutableStateOf("") }
    
    // Main outer scroll state to ensure screen fits compact devices
    val outerScrollState = rememberScrollState()
    
    // Mandated scroll state specifically for the legal content block
    val legalScrollState = rememberScrollState()

    // Dynamically calculate if the user has reached the bottom of the legal content
    val hasScrolledToBottom by remember {
        derivedStateOf {
            legalScrollState.value >= (legalScrollState.maxValue - 10) && legalScrollState.maxValue > 0
        }
    }

    val isNameValid = nameState.trim().isNotEmpty()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ONBOARDING DE SEGURIDAD",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(outerScrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Security Emblem
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Shield Security Emblem",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "GUANTERA DIGITAL",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Términos de Uso y Resguardo de Privacidad",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text("Nombre del Propietario") },
                    placeholder = { Text("Ej: Juan Pérez") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_name_input")
                        .padding(bottom = 16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtle visual scroll guidance indicator banner
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (hasScrolledToBottom) StateVigenteBg
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (hasScrolledToBottom) StateVigenteDot else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Scroll info indicator",
                            tint = if (hasScrolledToBottom) StateVigenteDot else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasScrolledToBottom) {
                                "✓ Lectura obligatoria completada. Puede aceptar los términos."
                            } else {
                                "↓ Deslice hasta el final del documento para habilitar la aceptación."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasScrolledToBottom) StateVigenteDot else MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Legal Content Box with MANDATORY vertical scroll constraint
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Fixed height to enforce scrolling to bottom
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp, 
                            if (hasScrolledToBottom) StateVigenteDot.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary, 
                            RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp)
                        .verticalScroll(legalScrollState)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Security Alert",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. RESGUARDO OFFLINE-FIRST",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Text(
                        text = "La aplicación 'GUANTERA DIGITAL' opera bajo un modelo prioritariamente local (offline-first). Esto significa que todos los documentos agregados (como DNI, Licencia de Conducir, SOAT, CITV) se copian físicamente en el almacenamiento seguro de la aplicación y se guardan dentro de la base de datos local SQLite (Room). Ninguno de sus archivos personales ni de identificación se sube a servidores externos sin su consentimiento explícito.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gavel,
                            contentDescription = "Gavel Policy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. RESPONSABILIDAD DEL USUARIO",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Text(
                        text = "Al utilizar esta guantera digital, usted reconoce que la autenticidad de los documentos que vincula y almacena es de su entera responsabilidad. El dispositivo móvil debe mantenerse bajo resguardo físico y con contraseñas de seguridad activas para evitar accesos no autorizados a su información de tránsito.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Al presionar 'Continuar', usted aprueba que el sistema inicialice la estructura local de datos y cree las carpetas de resguardo físico en su teléfono bajo el nuevo estándar oficial offline.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Checkbox confirmation Row (strictly disabled until scrolled to bottom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (hasScrolledToBottom) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                !hasScrolledToBottom -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                isChecked -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked && hasScrolledToBottom,
                        onCheckedChange = { isChecked = it },
                        enabled = hasScrolledToBottom,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            disabledUncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("accept_terms_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "He leído y acepto los términos de uso y el resguardo de privacidad.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (hasScrolledToBottom) MaterialTheme.colorScheme.onSurface 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Continue Button (strictly disabled until scrolled to bottom AND checked AND valid name)
                Button(
                    onClick = { onAccept(nameState.trim()) },
                    enabled = hasScrolledToBottom && isChecked && isNameValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("continue_to_wallet_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "CONTINUAR A LA GUANTERA",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (hasScrolledToBottom && isChecked && isNameValid) Color.White 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
