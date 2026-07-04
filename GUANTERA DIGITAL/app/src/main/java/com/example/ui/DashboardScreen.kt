package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.core.content.FileProvider
import androidx.compose.foundation.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import com.example.model.Document
import com.example.model.DocumentStatus
import com.example.model.Vehicle
import com.example.model.WalletValidators
import com.example.ui.theme.*
import com.example.util.FileStorageUtil
import com.example.viewmodel.WalletViewModel
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    viewModel: WalletViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedIndex by viewModel.selectedVehicleIndex.collectAsState()
    val selectedDocId by viewModel.selectedDocumentId.collectAsState()
    val isModoIntervencion by viewModel.isModoIntervencionActive.collectAsState()
    val systemMessage by viewModel.systemStatusMessage.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val userName by viewModel.userName.collectAsState()

    var isWizardOpen by remember { mutableStateOf(false) }
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }
    var currentScreen by remember { mutableStateOf("dashboard") }
    var showWelcome by remember { mutableStateOf(true) }

    var showAddCustomDocDialog by remember { mutableStateOf(false) }
    var customDocNameInput by remember { mutableStateOf("") }
    var targetVehicleForCustomDocId by remember { mutableStateOf("") }

    val activeVehicle = vehicles.getOrNull(selectedIndex)
    val activeDocument = activeVehicle?.documentos?.find { it.id == selectedDocId }

    // --- Real Camera / Gallery Infrastructure ---
    var pendingDocId by remember { mutableStateOf<String?>(null) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var showViewerDialog by remember { mutableStateOf(false) }
    var viewerFilePath by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingDocId != null && currentPhotoFile != null) {
            viewModel.updateDocumentPath(pendingDocId!!, currentPhotoFile!!.absolutePath, "IMAGE")
        }
        pendingDocId = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentPhotoUri?.let { cameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permiso de camara requerido para capturar fotos de documentos", Toast.LENGTH_LONG).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        try {
            if (uri != null && pendingDocId != null) {
                val mimeType = context.contentResolver.getType(uri)
                val path = uri.path
                val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true || 
                            path?.endsWith(".pdf", ignoreCase = true) == true
                val extension = if (isPdf) ".pdf" else ".jpg"
                val assetType = if (isPdf) "PDF" else "IMAGE"
                val destFile = FileStorageUtil.saveUriToFile(context, uri, "DOC_${pendingDocId}_${System.currentTimeMillis()}$extension")
                if (destFile != null) {
                    viewModel.updateDocumentPath(pendingDocId!!, destFile.absolutePath, assetType)
                    Toast.makeText(context, "Archivo guardado exitosamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar el archivo", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error inesperado al importar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            pendingDocId = null
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            galleryLauncher.launch("*/*")
        } else {
            Toast.makeText(context, "Permiso de almacenamiento requerido para seleccionar documentos", Toast.LENGTH_LONG).show()
        }
    }

    val launchCameraForDoc: (String) -> Unit = remember {
        { docId: String ->
            try {
                pendingDocId = docId
                val photoFile = FileStorageUtil.createImageFile(context)
                val photoUri = FileStorageUtil.getUriForFile(context, photoFile)
                currentPhotoUri = photoUri
                currentPhotoFile = photoFile
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(photoUri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al preparar la cámara: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val launchGalleryForDoc: (String) -> Unit = remember {
        { docId: String ->
            try {
                pendingDocId = docId
                val permissionToCheck = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    "android.permission.READ_MEDIA_IMAGES"
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                if (ContextCompat.checkSelfPermission(context, permissionToCheck) == PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("*/*")
                } else {
                    storagePermissionLauncher.launch(permissionToCheck)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al abrir la galería: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(systemMessage) {
        systemMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissSystemStatusMessage()
        }
    }

    val currentTab = remember { mutableStateOf(0) }

    // Synchronize currentScreen and bottom navigation currentTab
    LaunchedEffect(currentScreen) {
        if (currentScreen == "settings") {
            currentTab.value = 2
        } else if (currentScreen == "dashboard" && currentTab.value == 2) {
            currentTab.value = 0
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (vehicles.isNotEmpty()) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.navigationBarsPadding().height(64.dp)
                ) {
                    val items = listOf(
                        Triple("documentos", if (currentTab.value == 0) Icons.Filled.Description else Icons.Outlined.Description, 0),
                        Triple("vehículos", if (currentTab.value == 1) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar, 1),
                        Triple("perfil", if (currentTab.value == 2) Icons.Filled.Person else Icons.Outlined.Person, 2)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = currentTab.value == index,
                            onClick = {
                                if (index == 1) {
                                    isWizardOpen = true
                                } else if (index == 2) {
                                    currentTab.value = 2
                                    currentScreen = "settings"
                                } else {
                                    currentTab.value = 0
                                    currentScreen = "dashboard"
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (currentTab.value == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF111827),
                                selectedTextColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF111827),
                                unselectedIconColor = if (isDarkMode) Color(0xFF64748B) else Color(0xFF9CA3AF),
                                unselectedTextColor = if (isDarkMode) Color(0xFF64748B) else Color(0xFF9CA3AF),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (vehicles.isEmpty()) {
                AnimatedContent(
                    targetState = showWelcome,
                    transitionSpec = {
                        slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                        slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    },
                    label = "WelcomeToWizardTransition"
                ) { isShowingWelcome ->
                    if (isShowingWelcome) {
                        WelcomeSplashScreen(
                            onStart = { showWelcome = false },
                            viewModel = viewModel
                        )
                    } else {
                        OnboardingInitializationWizard(viewModel = viewModel)
                    }
                }
            } else if (currentScreen == "dashboard") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) {
                    DashboardHeader(
                        isDarkMode = isDarkMode,
                        userName = userName,
                        onAvatarClick = { currentScreen = "settings" },
                        onToggleTheme = { viewModel.toggleTheme() }
                    )

                    VehicleCarouselSection(
                        vehicles = vehicles,
                        selectedIndex = selectedIndex,
                        onSelected = { viewModel.selectVehicle(it) },
                        onAddNewVehicleClick = { isWizardOpen = true },
                        onDeleteVehicleClick = { vehicleToDelete = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedContent(
                        targetState = selectedIndex,
                        transitionSpec = {
                            slideInHorizontally { width -> width / 3 } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width / 3 } + fadeOut()
                        },
                        label = "DocumentSectionTransition"
                    ) { targetIndex ->
                        val vehicle = vehicles.getOrNull(targetIndex)
                        if (vehicle != null) {
                            Column {
                                DigitalBilletSection(
                                    activeVehicle = vehicle,
                                    selectedDocId = selectedDocId,
                                    onDocSelect = { viewModel.selectDocument(it) },
                                    onViewLocalPath = { path ->
                                        viewerFilePath = path
                                        showViewerDialog = true
                                    },
                                    onAddDocumentClick = {
                                        targetVehicleForCustomDocId = vehicle.vehiculoId
                                        customDocNameInput = ""
                                        showAddCustomDocDialog = true
                                    },
                                    onDeleteDocumentClick = { docId ->
                                        viewModel.updateDocumentPath(docId, "", "CAMERA")
                                    },
                                    onOpenCamera = { launchCameraForDoc(it) },
                                    onOpenGallery = { launchGalleryForDoc(it) }
                                )

                                val activeDoc = vehicle.documentos.find { it.id == selectedDocId }
                                if (activeDoc != null) {
                                    DocumentOperationsPanel(
                                        doc = activeDoc,
                                        onOpenCamera = { launchCameraForDoc(activeDoc.id) },
                                        onOpenGallery = { launchGalleryForDoc(activeDoc.id) },
                                        onViewDocument = {
                                            viewerFilePath = activeDoc.rutaArchivoLocal
                                            showViewerDialog = true
                                        },
                                        onIntervencion = { viewModel.toggleModoIntervencion() }
                                    )
                                }
                            }
                        } else {
                            EmptyStateCard()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (vehicles.isNotEmpty() && activeDocument != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { viewModel.toggleModoIntervencion() },
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("modo_intervencion_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Modo Intervención",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                SettingsAndManagementScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "dashboard" },
                    onDeleteVehicleClick = { vehicleToDelete = it },
                    onAddNewVehicleClick = { isWizardOpen = true }
                )
            }

            ImmersiveVehicleWizard(
                isOpen = isWizardOpen,
                onDismiss = { isWizardOpen = false },
                onSubmitVehicle = { tipo, placa, marca, modelo, col, docPaths ->
                    val error = viewModel.createAndAddVehicle(tipo, placa, marca, modelo, col, docPaths)
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (isModoIntervencion && activeDocument != null) {
                FullscreenIntervencionOverlay(
                    doc = activeDocument,
                    vehiclePlaca = activeVehicle?.placa ?: "SIN-PLACA",
                    onClose = { viewModel.closeModoIntervencion() }
                )
            }

            if (showViewerDialog && viewerFilePath.isNotEmpty()) {
                DocumentViewerDialog(
                    rutaLocal = viewerFilePath,
                    vehicles = vehicles,
                    onDismiss = {
                        showViewerDialog = false
                        viewerFilePath = ""
                    }
                )
            }

            if (vehicleToDelete != null) {
                DeleteVehicleBottomSheet(
                    vehicle = vehicleToDelete!!,
                    onConfirmDelete = {
                        viewModel.deleteVehicle(vehicleToDelete!!.vehiculoId)
                        vehicleToDelete = null
                    },
                    onDismiss = { vehicleToDelete = null }
                )
            }

            if (showAddCustomDocDialog) {
                AlertDialog(
                    onDismissRequest = { showAddCustomDocDialog = false },
                    title = {
                        Text(
                            text = "Agregar Documento",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF111827)
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Escribe el nombre del documento que deseas agregar a tu vehículo:",
                                fontSize = 14.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF6B7280)
                            )
                            OutlinedTextField(
                                value = customDocNameInput,
                                onValueChange = { customDocNameInput = it },
                                label = { Text("Nombre del documento") },
                                placeholder = { Text("Ej: Revisión Técnica, Seguro Particular") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (customDocNameInput.isNotBlank()) {
                                    viewModel.addCustomDocument(targetVehicleForCustomDocId, customDocNameInput.trim())
                                    showAddCustomDocDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF1D70B8),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Agregar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAddCustomDocDialog = false }
                        ) {
                            Text(
                                text = "Cancelar",
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF6B7280)
                            )
                        }
                    },
                    containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// ============================================================
// DOCUMENT VIEWER DIALOG - Real image display from captured photos
// ============================================================
@Composable
fun DocumentViewerDialog(
    rutaLocal: String,
    onDismiss: () -> Unit,
    vehicles: List<Vehicle> = emptyList()
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondary = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary

    // Find matching document if any, to display nice title and metadata
    val doc = remember(rutaLocal, vehicles) {
        vehicles.flatMap { it.documentos }.find { it.rutaArchivoLocal == rutaLocal }
    }

    val friendlyName = doc?.friendlyName ?: "DOCUMENTO ADJUNTO"
    val docType = doc?.tipo ?: "LOCAL"
    val fechaVencimiento = doc?.fechaVencimiento ?: "N/A"
    val identificadorVisual = doc?.identificadorVisual ?: if (rutaLocal.contains("/")) rutaLocal.substringAfterLast("/") else rutaLocal
    val assetType = doc?.assetType ?: if (rutaLocal.endsWith(".pdf", ignoreCase = true)) "PDF" else "IMAGE"
    val status = doc?.status ?: DocumentStatus.VALID

    val bitmap = remember(rutaLocal) {
        try {
            val file = File(rutaLocal)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    val pdfBitmap = remember(rutaLocal) {
        if (rutaLocal.endsWith(".pdf", ignoreCase = true)) {
            try {
                val file = File(rutaLocal)
                if (file.exists()) {
                    val fileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                    val page = pdfRenderer.openPage(0)
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pdfRenderer.close()
                    fileDescriptor.close()
                    b
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) DarkBackground else Color.Black.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) DarkSurface else Color(0xFF1A1A2E))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = friendlyName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$docType | Venc: $fechaVencimiento",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar visor",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        rutaLocal.endsWith(".pdf", ignoreCase = true) -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pdfBitmap != null) {
                                        Image(
                                            bitmap = pdfBitmap.asImageBitmap(),
                                            contentDescription = friendlyName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PictureAsPdf,
                                            contentDescription = "PDF Icon",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "📄 Vista previa de documento PDF resguardado localmente",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (rutaLocal.contains("/")) rutaLocal.substringAfterLast("/") else rutaLocal,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = friendlyName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (doc != null) {
                                    DigitalReplicaCard(doc = doc)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.ImageNotSupported,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Archivo no encontrado localmente.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = rutaLocal,
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nota: No se encontró imagen o archivo físico. Se muestra la réplica digital local autorizada.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) DarkSurface else Color(0xFF1A1A2E))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rutaLocal.endsWith(".pdf", ignoreCase = true)) {
                        Button(
                            onClick = {
                                try {
                                    val file = File(rutaLocal)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Abrir PDF con..."))
                                    } else {
                                        Toast.makeText(context, "Archivo local no encontrado en el almacenamiento para apertura externa.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al abrir archivo PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(0.85f).height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir PDF con visor externo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val statusColor = when (status) {
                        DocumentStatus.VALID -> StateVigenteDot
                        DocumentStatus.EXPIRING -> StatePorVencerDot
                        DocumentStatus.EXPIRED -> StateVencidoDot
                    }
                    val statusText = when (status) {
                        DocumentStatus.VALID -> "Vigente"
                        DocumentStatus.EXPIRING -> "Por vencer"
                        DocumentStatus.EXPIRED -> "Vencido"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = identificadorVisual,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DigitalReplicaCard(doc: Document, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    val cardBorder = if (isDark) Color(0xFF333333) else Color(0xFFE2E8F0)
    val labelColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.6f)
    val valueColor = if (isDark) Color.White else Color.Black

    val statusColor = when (doc.status) {
        DocumentStatus.VALID -> StateVigenteDot
        DocumentStatus.EXPIRING -> StatePorVencerDot
        DocumentStatus.EXPIRED -> StateVencidoDot
    }

    val docTypeTitle = when (doc.tipo) {
        "DNI" -> "Documento Nacional de Identidad"
        "LICENCIA" -> "Licencia de Conducir"
        "TIV" -> "Tarjeta de Propiedad (TIV)"
        "SOAT" -> "Certificado SOAT Electrónico"
        "CITV" -> "Certificado Inspección Técnica (CITV)"
        "PESOS" -> "Certificado de Pesos y Medidas"
        else -> doc.friendlyName
    }

    val institution = when (doc.tipo) {
        "DNI" -> "Reniec - República del Perú"
        "LICENCIA" -> "MTC - Ministerio de Transportes y Comunicaciones"
        "TIV" -> "Sunarp - Registro de Propiedad"
        "SOAT" -> "Apeseg - Certificado Virtual"
        "CITV" -> "MTC - Inspección Técnica"
        "PESOS" -> "MTC - Pesos y Medidas"
        else -> "Guantera Digital"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(cardBg, RoundedCornerShape(16.dp))
            .border(2.dp, cardBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = institution,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = docTypeTitle,
                        color = valueColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 16.dp)
                        .background(Color(0xFFD91C1C))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                            .align(Alignment.Center)
                            .background(Color.White)
                    )
                }
            }

            Divider(color = cardBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text("Identificador", color = labelColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = doc.identificadorVisual,
                            color = valueColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text("Fecha de vencimiento", color = labelColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = doc.fechaVencimiento,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column {
                        Text("Descripción local", color = labelColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = doc.friendlyName,
                            color = valueColor.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val size = this.size.width
                            val steps = 7
                            val stepSize = size / steps
                            drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(stepSize * 2, stepSize * 2))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(stepSize * 0.4f, stepSize * 0.4f), size = androidx.compose.ui.geometry.Size(stepSize * 1.2f, stepSize * 1.2f))
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(stepSize * 0.7f, stepSize * 0.7f), size = androidx.compose.ui.geometry.Size(stepSize * 0.6f, stepSize * 0.6f))

                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(size - stepSize * 2, 0f), size = androidx.compose.ui.geometry.Size(stepSize * 2, stepSize * 2))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size - stepSize * 1.6f, stepSize * 0.4f), size = androidx.compose.ui.geometry.Size(stepSize * 1.2f, stepSize * 1.2f))
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(size - stepSize * 1.3f, stepSize * 0.7f), size = androidx.compose.ui.geometry.Size(stepSize * 0.6f, stepSize * 0.6f))

                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, size - stepSize * 2), size = androidx.compose.ui.geometry.Size(stepSize * 2, stepSize * 2))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(stepSize * 0.4f, size - stepSize * 1.6f), size = androidx.compose.ui.geometry.Size(stepSize * 1.2f, stepSize * 1.2f))
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(stepSize * 0.7f, size - stepSize * 1.3f), size = androidx.compose.ui.geometry.Size(stepSize * 0.6f, stepSize * 0.6f))

                            val random = java.util.Random(doc.id.hashCode().toLong())
                            for (i in 0 until steps) {
                                for (j in 0 until steps) {
                                    if ((i < 2 && j < 2) || (i > steps - 3 && j < 2) || (i < 2 && j > steps - 3)) continue
                                    if (random.nextBoolean()) {
                                        drawRect(
                                            Color.Black,
                                            topLeft = androidx.compose.ui.geometry.Offset(i * stepSize, j * stepSize),
                                            size = androidx.compose.ui.geometry.Size(stepSize * 0.9f, stepSize * 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SECURE MTC",
                            color = Color(0xFF94A3B8),
                            fontSize = 6.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📱 Vista digital offline autorizada",
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// DELETE VEHICLE BOTTOM SHEET
// ============================================================
@Composable
fun DeleteVehicleBottomSheet(
    vehicle: Vehicle,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val CardBg = if (isDark) DarkSurface else LightSurface
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF111827)
    val textSecondaryColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)
    val crimsonRedColor = StateVencidoDot

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.2.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f))
                )

                Text(
                    text = "Deseas eliminar este vehículo?",
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF151515) else Color(0xFFF2F1F6)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${vehicle.marca} ${vehicle.modelo}",
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tipo: ${vehicle.tipoVehiculo} | Color: ${vehicle.color}",
                                color = textSecondaryColor,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isDark) Color(0xFF1E293B) else Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = vehicle.placa,
                                color = if (isDark) Color.White else LightTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = crimsonRedColor.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, crimsonRedColor.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Alerta de Seguridad",
                            tint = crimsonRedColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Esta acción eliminará permanentemente el registro de este vehículo y el acceso a todos sus documentos locales.",
                            color = if (isDark) Color.White.copy(alpha = 0.85f) else LightTextPrimary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = crimsonRedColor, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_delete_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Eliminar registro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_delete_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textSecondaryColor),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
                ) {
                    Text("Conservar vehículo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    isDarkMode: Boolean,
    userName: String,
    onAvatarClick: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top row: Hamburger & theme toggle & Bell
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAvatarClick,
                modifier = Modifier.size(44.dp).testTag("jd_avatar_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menú",
                    tint = textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Theme toggle button in header
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Cambiar Tema",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { Toast.makeText(context, "No tienes nuevas notificaciones", Toast.LENGTH_SHORT).show() }) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notificaciones",
                            tint = textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Dot indicator
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Greeting: "Hola," bold + "{userName}" light
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hola, ",
                color = textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userName.ifEmpty { "Alex" },
                color = textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle + QR Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Todo en orden. Conduce seguro.",
                color = textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { Toast.makeText(context, "Escáner QR iniciado", Toast.LENGTH_SHORT).show() },
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF3F4F6))
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = "Escanear QR",
                    tint = textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun VehicleCarouselSection(
    vehicles: List<Vehicle>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onAddNewVehicleClick: () -> Unit,
    onDeleteVehicleClick: (Vehicle) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)

    if (vehicles.size == 1) {
        val vehicle = vehicles[0]
        val isSelected = selectedIndex == 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            VehiclePlateCard(
                vehicle = vehicle,
                isSelected = isSelected,
                onClick = { onSelected(0) },
                onDeleteClick = { onDeleteVehicleClick(vehicle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plate_card_0")
            )
        }
    } else if (vehicles.isNotEmpty()) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis vehículos",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${vehicles.size} registrados",
                    color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAddNewVehicleClick() }
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(vehicles) { index, vehicle ->
                    val isSelected = index == selectedIndex
                    val cardScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.95f,
                        label = "cardScale_$index"
                    )
                    val cardAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.7f,
                        label = "cardAlpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .scale(cardScale)
                            .alpha(cardAlpha)
                    ) {
                        VehiclePlateCard(
                            vehicle = vehicle,
                            isSelected = isSelected,
                            onClick = { onSelected(index) },
                            onDeleteClick = { onDeleteVehicleClick(vehicle) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plate_card_$index")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VehiclePlateCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF8E9AA6) else Color(0xFF6B7280)

    val cardBg = if (isDark) {
        if (isSelected) Color(0xFF2C2C2C) else Color(0xFF181818)
    } else {
        if (isSelected) Color(0xFFE5E5E5) else Color(0xFFFAFAFA)
    }

    val elevation = if (isSelected) 6.dp else 1.dp

    Card(
        modifier = modifier
            .height(120.dp)
            .shadow(elevation = elevation, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Background vehicle type icon: larger and semi-transparent
            Icon(
                imageVector = when (vehicle.tipoVehiculo) {
                    "MOTO" -> Icons.Rounded.TwoWheeler
                    "AUTO" -> Icons.Rounded.DirectionsCar
                    else -> Icons.Rounded.LocalShipping
                },
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f),
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 15.dp)
                    .then(
                        if (vehicle.tipoVehiculo == "MOTO") Modifier.scale(scaleX = -1f, scaleY = 1f)
                        else Modifier
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Brand & Model, and More Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${vehicle.marca} ${vehicle.modelo} — ${vehicle.color}",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_vehicle_button_${vehicle.placa}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Opciones de Vehículo",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Middle Row: Plate + Active Dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = vehicle.placa,
                        color = textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                    }
                }

                // Bottom Row: Status text and Document Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) "Seleccionado" else "Guardado localmente",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Text(
                        text = "${vehicle.documentos.size} documentos",
                        color = textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DigitalBilletSection(
    activeVehicle: Vehicle,
    selectedDocId: String?,
    onDocSelect: (String) -> Unit,
    onViewLocalPath: (String) -> Unit,
    onAddDocumentClick: () -> Unit,
    onDeleteDocumentClick: (String) -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenGallery: (String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mis documentos",
                color = textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Ver todos >",
                color = if (isDark) Color.White else Color(0xFF111111),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    // Quick click action
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            activeVehicle.documentos.forEach { doc ->
                DocumentListCard(
                    doc = doc,
                    isSelected = doc.id == selectedDocId,
                    onClick = { onDocSelect(doc.id) },
                    onViewLocalPath = onViewLocalPath,
                    vehicleName = "${activeVehicle.marca} ${activeVehicle.modelo}",
                    onDeleteClick = { onDeleteDocumentClick(doc.id) },
                    onOpenCamera = { onOpenCamera(doc.id) },
                    onOpenGallery = { onOpenGallery(doc.id) },
                    modifier = Modifier.fillMaxWidth().testTag("doc_card_${doc.id}")
                )
            }

            AddDocumentListCard(
                onClick = onAddDocumentClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AddDocumentListCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF8E9AA6) else Color(0xFF6B7280)
    val cardBg = if (isDark) DarkSurface else Color(0xFFFFFFFF)

    Card(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Agregar",
                    tint = textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Agregar documento",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Guarda tus documentos importantes",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun DocumentListCard(
    doc: Document,
    isSelected: Boolean,
    onClick: () -> Unit,
    onViewLocalPath: (String) -> Unit,
    vehicleName: String,
    onDeleteClick: (() -> Unit)? = null,
    onOpenCamera: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val isPending = doc.rutaArchivoLocal.isEmpty()

    var showMenu by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }

    val textPrimary = if (isDark) Color.White else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    val statusColor = if (isPending) {
        Color(0xFF94A3B8)
    } else {
        when (doc.status) {
            DocumentStatus.VALID -> StateVigenteDot
            DocumentStatus.EXPIRING -> StatePorVencerDot
            DocumentStatus.EXPIRED -> StateVencidoDot
        }
    }

    val badgeBg = statusColor.copy(alpha = 0.15f)
    val badgeContentColor = statusColor

    val statusText = if (isPending) {
        "PENDIENTE"
    } else {
        when (doc.status) {
            DocumentStatus.VALID -> "Vigente"
            DocumentStatus.EXPIRING -> "Por vencer"
            DocumentStatus.EXPIRED -> "Vencido"
        }
    }

    val elevation = if (isSelected) 6.dp else 2.dp

    Card(
        modifier = modifier
            .shadow(elevation = elevation, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) DarkSurface else Color.White)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (doc.tipo) {
                                "DNI" -> Icons.Outlined.PermIdentity
                                "LICENCIA" -> Icons.Outlined.CardMembership
                                "SOAT" -> Icons.Outlined.VerifiedUser
                                "TIV" -> Icons.Outlined.Assignment
                                "CITV" -> Icons.Outlined.DirectionsCar
                                else -> Icons.Outlined.Inventory2
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.friendlyName,
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = doc.identificadorVisual,
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeContentColor)
                            )
                            Text(
                                text = statusText,
                                color = badgeContentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isPending) {
                            IconButton(
                                onClick = {
                                    onViewLocalPath(doc.rutaArchivoLocal)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Visualizar Documento",
                                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF374151),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(28.dp).testTag("doc_menu_button_${doc.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opciones de documento",
                                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF374151),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .background(if (isDark) Color(0xFF1E2235) else Color.White)
                                        .border(
                                            1.dp,
                                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Modificar",
                                                color = if (isDark) Color.White else Color(0xFF111827),
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showReplaceDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Eliminar",
                                                color = if (isDark) Color.White else Color(0xFF111827),
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onDeleteClick?.invoke()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = StateVencidoDot,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (showReplaceDialog) {
                        AlertDialog(
                            onDismissRequest = { showReplaceDialog = false },
                            title = {
                                Text(
                                    text = "Reemplazar documento",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF111827)
                                )
                            },
                            text = {
                                Text(
                                    text = "¿Cómo deseas capturar el nuevo documento para reemplazar el archivo actual?",
                                    fontSize = 14.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)
                                )
                            },
                            confirmButton = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = {
                                            showReplaceDialog = false
                                            onOpenCamera?.invoke()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cámara", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            showReplaceDialog = false
                                            onOpenGallery?.invoke()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Galería", fontSize = 12.sp)
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReplaceDialog = false }) {
                                    Text("Cancelar", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280))
                                }
                            },
                            containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    when (doc.tipo) {
                        "SOAT" -> {
                            MetadataColumn(
                                label = "Aseguradora",
                                value = "Pacífico SOAT",
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataColumn(
                                label = "Válido hasta",
                                value = doc.fechaVencimiento,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "LICENCIA" -> {
                            MetadataColumn(
                                label = "Categoría",
                                value = "Particular A-I",
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataColumn(
                                label = "Vencimiento",
                                value = doc.fechaVencimiento,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "TIV" -> {
                            MetadataColumn(
                                label = "Vehículo",
                                value = vehicleName,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataColumn(
                                label = "Propietario",
                                value = "Alex Ramírez",
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "DNI" -> {
                            MetadataColumn(
                                label = "DNI Propietario",
                                value = doc.identificadorVisual,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataColumn(
                                label = "Vencimiento",
                                value = doc.fechaVencimiento,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> {
                            MetadataColumn(
                                label = "Documento",
                                value = doc.friendlyName,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetadataColumn(
                                label = "Vencimiento",
                                value = doc.fechaVencimiento,
                                textColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (isPending) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenCamera?.invoke() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isDark) Color.White else Color(0xFF1E1E1E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cámara", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = { onOpenGallery?.invoke() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isDark) Color.White else Color(0xFF1E1E1E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galería", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataColumn(
    label: String,
    value: String,
    textColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// DOCUMENT OPERATIONS PANEL - Real camera and gallery
// ============================================================
@Composable
fun DocumentOperationsPanel(
    doc: Document,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onViewDocument: () -> Unit,
    onIntervencion: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val surfaceColor = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) Color.White else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)
    val btnBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF3F4F6)
    val defaultIconTint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OperationIconButton(
                    icon = Icons.Filled.CameraAlt,
                    label = "Cámara",
                    onClick = onOpenCamera,
                    btnBgColor = btnBgColor,
                    iconTint = defaultIconTint,
                    textColor = textPrimary
                )
                OperationIconButton(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Galería",
                    onClick = onOpenGallery,
                    btnBgColor = btnBgColor,
                    iconTint = defaultIconTint,
                    textColor = textPrimary
                )
                OperationIconButton(
                    icon = Icons.Filled.Visibility,
                    label = "Ver",
                    onClick = onViewDocument,
                    btnBgColor = btnBgColor,
                    iconTint = defaultIconTint,
                    textColor = textPrimary
                )
                OperationIconButton(
                    icon = Icons.Filled.Bolt,
                    label = "Intervención",
                    onClick = onIntervencion,
                    btnBgColor = Color(0xFFEF4444),
                    iconTint = Color.White,
                    textColor = textPrimary
                )
            }
        }
    }
}

@Composable
fun OperationIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    btnBgColor: Color,
    iconTint: Color,
    textColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(btnBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label.lowercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyStateCard() {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkSurface else LightSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = if (isDark) TextSecondary else LightTextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ningun vehiculo seleccionado",
                color = if (isDark) TextPrimary else LightTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Inscribe o selecciona un vehiculo para visualizar su documentacion.",
                color = if (isDark) TextSecondary else LightTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModoIntervencionFab(
    isActive: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val CarbonAccent = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val CrimsonRed = StateVencidoDot

    Button(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier
            .width(280.dp)
            .height(54.dp)
            .border(2.dp, if (isActive) Color.White else Color.Transparent, RoundedCornerShape(27.dp))
            .testTag("modo_intervencion_fab"),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) CrimsonRed else MutedPurpleAccent,
            contentColor = Color.White,
            disabledContainerColor = CarbonAccent
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(27.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MODO INTERVENCION",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun FullscreenIntervencionOverlay(
    doc: Document,
    vehiclePlaca: String,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("MODO INTERVENCION ACTIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("BRILLO: 100%", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("DOCUMENTO OFICIAL VEHICULAR", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(3.dp, Color.Black, RoundedCornerShape(6.dp))
                            .padding(vertical = 8.dp, horizontal = 24.dp)
                    ) {
                        Text(vehiclePlaca, color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color.Black)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("TIPO: ${doc.tipo} (${doc.friendlyName})", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("VENCIMIENTO: ${doc.fechaVencimiento}", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ID: ${doc.identificadorVisual}", color = Color.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(4.dp, Color.Black, RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.QrCode, contentDescription = "QR", tint = Color.Black, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("VALIDACION OFFLINE CON ENCRIPTACION", color = Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(27.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("close_intervencion_button")
                ) {
                    Text("RESTAURAR Y REGRESAR", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============================================================
// IMMERSIVE VEHICLE WIZARD - Real camera/gallery integration
// ============================================================
@Composable
fun ImmersiveVehicleWizard(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSubmitVehicle: (tipo: String, placa: String, marca: String, modelo: String, color: String, customPaths: Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val docPaths = remember { mutableStateMapOf<String, String>() }
    var wizardPendingDocType by remember { mutableStateOf<String?>(null) }
    var wizardPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var wizardPhotoFile by remember { mutableStateOf<File?>(null) }

    val wizardCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && wizardPendingDocType != null && wizardPhotoFile != null) {
            docPaths[wizardPendingDocType!!] = wizardPhotoFile!!.absolutePath
        }
        wizardPendingDocType = null
    }

    val wizardCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wizardPhotoUri?.let { wizardCameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permiso de camara requerido", Toast.LENGTH_SHORT).show()
        }
    }

    val wizardGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        try {
            if (uri != null && wizardPendingDocType != null) {
                val mimeType = context.contentResolver.getType(uri)
                val path = uri.path
                val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true || 
                            path?.endsWith(".pdf", ignoreCase = true) == true
                val ext = if (isPdf) ".pdf" else ".jpg"
                val destFile = FileStorageUtil.saveUriToFile(context, uri, "DOC_${wizardPendingDocType}_${System.currentTimeMillis()}$ext")
                if (destFile != null) {
                    docPaths[wizardPendingDocType!!] = destFile.absolutePath
                    Toast.makeText(context, "Archivo cargado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al procesar archivo", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al importar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            wizardPendingDocType = null
        }
    }

    val wizardStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wizardGalleryLauncher.launch("*/*")
        } else {
            Toast.makeText(context, "Permiso de almacenamiento requerido", Toast.LENGTH_SHORT).show()
        }
    }

    if (!isOpen) return

    var currentStep by remember { mutableStateOf(1) }
    var selectedType by remember { mutableStateOf("AUTO") }
    var placaInput by remember { mutableStateOf("") }
    var marcaInput by remember { mutableStateOf("") }
    var modeloInput by remember { mutableStateOf("") }
    var colorInput by remember { mutableStateOf("") }
    var dniInput by remember { mutableStateOf("") }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimary = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondary = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurface else Color.White

    val isValidPlaca = remember(selectedType, placaInput) {
        if (selectedType == "MOTO") WalletValidators.validateMotorcyclePlate(placaInput)
        else WalletValidators.validateAutoPlate(placaInput)
    }
    val isValidDni = remember(dniInput) { dniInput.length == 8 && dniInput.all { it.isDigit() } }
    val isFormValid = isValidPlaca && marcaInput.isNotEmpty() && modeloInput.isNotEmpty() && colorInput.isNotEmpty() && isValidDni

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) DarkBackground.copy(alpha = 0.95f) else LightBackground.copy(alpha = 0.95f))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Paso $currentStep de 2", color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(
                            text = if (currentStep == 1) "Tipo de vehículo" else "Datos y requisitos",
                            color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                ) {
                    Box(modifier = Modifier.fillMaxHeight().weight(if (currentStep == 1) 0.5f else 1.0f).background(if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)))
                    if (currentStep == 1) Spacer(modifier = Modifier.weight(0.5f))
                }

                Spacer(modifier = Modifier.height(30.dp))

                AnimatedContent(targetState = currentStep, label = "WizardStepAnimation") { step ->
                    if (step == 1) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Seleccione el tipo de unidad a registrar:", color = textSecondary, fontSize = 14.sp)
                            WizardTypeCard(title = "Moto", description = "Unidades menores de dos o tres ruedas", icon = Icons.Rounded.TwoWheeler, isSelected = selectedType == "MOTO", onClick = { selectedType = "MOTO"; placaInput = ""; currentStep = 2 })
                            WizardTypeCard(title = "Auto particular", description = "Sedán, Hatchback, SUV para uso privado", icon = Icons.Rounded.DirectionsCar, isSelected = selectedType == "AUTO", onClick = { selectedType = "AUTO"; placaInput = ""; currentStep = 2 })
                            WizardTypeCard(title = "Camioneta", description = "Pick-Up, Furgón, uso comercial o mixto", icon = Icons.Rounded.LocalShipping, isSelected = selectedType == "CAMIONETA", onClick = { selectedType = "CAMIONETA"; placaInput = ""; currentStep = 2 })
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.clickable { currentStep = 1 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Atras", tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Volver a selección ($selectedType)", color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Datos del vehículo", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Placa de Rodaje", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(if (selectedType == "MOTO") "Ej: 1234-AB" else "Ej: ABC-123", color = textSecondary, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = placaInput,
                                            onValueChange = { raw -> placaInput = if (selectedType == "MOTO") WalletValidators.formatMotorcyclePlate(raw) else WalletValidators.formatAutoPlate(raw) },
                                            textStyle = TextStyle(fontSize = 14.sp, color = textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.fillMaxWidth().testTag("wizard_plate_input"),
                                            placeholder = { Text("Número de placa", fontSize = 13.sp, color = textSecondary) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = if (isValidPlaca) StateVigenteDot else StateVencidoDot, unfocusedBorderColor = if (placaInput.isEmpty()) (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)) else if (isValidPlaca) StateVigenteDot else StateVencidoDot),
                                            trailingIcon = {
                                                if (placaInput.isNotEmpty()) {
                                                    Icon(imageVector = if (isValidPlaca) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, tint = if (isValidPlaca) StateVigenteDot else StateVencidoDot)
                                                }
                                            }
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Marca", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(value = marcaInput, onValueChange = { marcaInput = it }, textStyle = TextStyle(fontSize = 13.sp, color = textPrimary), placeholder = { Text("Peugeot", fontSize = 11.sp, color = textSecondary) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Modelo", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(value = modeloInput, onValueChange = { modeloInput = it }, textStyle = TextStyle(fontSize = 13.sp, color = textPrimary), placeholder = { Text("3008", fontSize = 11.sp, color = textSecondary) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary))
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Color", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(value = colorInput, onValueChange = { colorInput = it }, textStyle = TextStyle(fontSize = 13.sp, color = textPrimary), placeholder = { Text("Gris", fontSize = 11.sp, color = textSecondary) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("DNI Propietario", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                if (dniInput.isNotEmpty() && !isValidDni) Text("8 dig.", color = StateVencidoDot, fontSize = 9.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = dniInput,
                                                onValueChange = { raw -> if (raw.length <= 8 && raw.all { it.isDigit() }) dniInput = raw },
                                                textStyle = TextStyle(fontSize = 13.sp, color = textPrimary),
                                                placeholder = { Text("DNI (8 dígitos)", fontSize = 11.sp, color = textSecondary) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = if (isValidDni) StateVigenteDot else StateVencidoDot, unfocusedBorderColor = if (dniInput.isEmpty()) (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)) else if (isValidDni) StateVigenteDot else StateVencidoDot)
                                            )
                                        }
                                    }
                                }
                            }

                            Text("DOCUMENTACION REQUERIDA", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                            val requiredDocs = remember(selectedType) {
                                when (selectedType) {
                                    "MOTO" -> listOf("LICENCIA" to "Licencia Clase B", "TIV" to "Tarjeta Propiedad (TIV)", "SOAT" to "SOAT Certificado")
                                    "AUTO" -> listOf("LICENCIA" to "Licencia Clase A-I", "TIV" to "Tarjeta Propiedad (TIV)", "SOAT" to "SOAT Certificado", "CITV" to "CITV Inspeccion Tecnica")
                                    else -> listOf("LICENCIA" to "Licencia Clase A-I", "TIV" to "Tarjeta Propiedad (TIV)", "SOAT" to "SOAT Certificado", "CITV" to "CITV Inspeccion Tecnica", "PESOS_Y_MEDIDAS" to "Certificado de Pesos y Medidas")
                                }
                            }

                            requiredDocs.forEach { (docType, label) ->
                                val hasPath = docPaths[docType] != null
                                WizardDocSlot(
                                    docType = docType,
                                    label = label,
                                    hasPath = hasPath,
                                    currentPath = docPaths[docType] ?: "Sin capturar",
                                    isDark = isDark,
                                    onCameraClick = {
                                        try {
                                            wizardPendingDocType = docType
                                            val photoFile = FileStorageUtil.createImageFile(context)
                                            val photoUri = FileStorageUtil.getUriForFile(context, photoFile)
                                            wizardPhotoUri = photoUri
                                            wizardPhotoFile = photoFile
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                wizardCameraLauncher.launch(photoUri)
                                            } else {
                                                wizardCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error al preparar la cámara: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onGalleryClick = {
                                        try {
                                            wizardPendingDocType = docType
                                            val permissionToCheck = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                "android.permission.READ_MEDIA_IMAGES"
                                            } else {
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            if (ContextCompat.checkSelfPermission(context, permissionToCheck) == PackageManager.PERMISSION_GRANTED) {
                                                wizardGalleryLauncher.launch("*/*")
                                            } else {
                                                wizardStoragePermissionLauncher.launch(permissionToCheck)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error al abrir la galería: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    onSubmitVehicle(selectedType, placaInput, marcaInput, modeloInput, colorInput, docPaths.toMap())
                                    onDismiss()
                                },
                                enabled = isFormValid,
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("wizard_submit_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Completar registro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WizardDocSlot(
    docType: String,
    label: String,
    hasPath: Boolean,
    currentPath: String,
    isDark: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val textPrimary = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondary = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkSurface else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (hasPath) StateVigenteDot else borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (hasPath) StateVigenteDot else StatePorVencerDot))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = if (hasPath) "Vinculado" else "Requerido",
                    color = if (hasPath) StateVigenteDot else StatePorVencerDot,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = currentPath, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCameraClick,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Capturar Foto", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galeria", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun WizardTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val cardBg = if (isDark) {
        if (isSelected) Color(0xFF1A2240) else DarkSurface
    } else {
        if (isSelected) Color(0xFFF2F1F6) else LightSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) (if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)) else (if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) (if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)).copy(alpha = 0.2f) else (if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) (if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)) else (if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, color = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = description, color = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndManagementScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onDeleteVehicleClick: (Vehicle) -> Unit,
    onAddNewVehicleClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimaryColor = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondaryColor = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkSurface else LightSurface
    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val dbVehicles by viewModel.vehicles.collectAsState()
    val currentUserName by viewModel.userName.collectAsState()
    val currentUserEmail by viewModel.userEmail.collectAsState()
    val isSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()

    var nameInput by remember { mutableStateOf(currentUserName) }
    var emailInput by remember { mutableStateOf(currentUserEmail) }
    var syncState by remember { mutableStateOf(isSyncEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    .testTag("settings_back_button")
            ) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Volver", tint = textPrimaryColor, modifier = Modifier.size(20.dp))
            }
            Text("Configuraciones y gestión", color = textPrimaryColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = null, tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), modifier = Modifier.size(24.dp))
                    Text("Gestión de perfil", color = textPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = cardBorderColor)

                OutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("Nombre del propietario", color = textSecondaryColor) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), unfocusedBorderColor = cardBorderColor, focusedLabelColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), unfocusedLabelColor = textSecondaryColor)
                )

                OutlinedTextField(
                    value = emailInput, onValueChange = { emailInput = it },
                    label = { Text("Correo electrónico", color = textSecondaryColor) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), unfocusedBorderColor = cardBorderColor, focusedLabelColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), unfocusedLabelColor = textSecondaryColor)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9).copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (syncState) Icons.Filled.CloudDone else Icons.Filled.CloudOff, contentDescription = null, tint = if (syncState) StateVigenteDot else StateVencidoDot, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Sincronización Cloud", color = textPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (syncState) "Modo nube activo" else "Modo offline local", color = textSecondaryColor, fontSize = 10.sp)
                        }
                    }
                    val isDarkModeState by viewModel.isDarkMode.collectAsState()
                    Switch(
                        checked = syncState, onCheckedChange = { syncState = it },
                        modifier = Modifier.testTag("sync_cloud_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), uncheckedThumbColor = textSecondaryColor, uncheckedTrackColor = cardBorderColor)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9).copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isDarkModeState by viewModel.isDarkMode.collectAsState()
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (isDarkModeState) Icons.Filled.DarkMode else Icons.Filled.LightMode, contentDescription = null, tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), modifier = Modifier.size(20.dp))
                        Column {
                            Text("Tema de la aplicación", color = textPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (isDarkModeState) "Modo oscuro activo" else "Modo claro activo", color = textSecondaryColor, fontSize = 10.sp)
                        }
                    }
                    val isDarkModeState2 by viewModel.isDarkMode.collectAsState()
                    Switch(
                        checked = isDarkModeState2,
                        onCheckedChange = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("theme_toggle_button_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), uncheckedThumbColor = textSecondaryColor, uncheckedTrackColor = cardBorderColor)
                    )
                }

                Button(
                    onClick = { viewModel.updateUserProfile(nameInput, emailInput, syncState) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar cambios", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Filled.DirectionsCar, contentDescription = null, tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), modifier = Modifier.size(24.dp))
                    Text("Gestión de vehículos", color = textPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = cardBorderColor)

                if (dbVehicles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No hay vehiculos registrados.", color = textSecondaryColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dbVehicles.forEach { veh ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFF1F5F9).copy(alpha = 0.3f))
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (veh.tipoVehiculo) { "MOTO" -> Icons.Filled.TwoWheeler; "CAMIONETA" -> Icons.Filled.LocalShipping; else -> Icons.Filled.DirectionsCar },
                                            contentDescription = null, tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8), modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(veh.placa, color = textPrimaryColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                        Text("${veh.marca} ${veh.modelo} | ${veh.color}", color = textSecondaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteVehicleClick(veh) },
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(StateVencidoDot.copy(alpha = 0.12f)).testTag("manage_delete_vehicle_${veh.placa}")
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Eliminar Registro", tint = StateVencidoDot, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAddNewVehicleClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_vehicle_from_settings"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar vehículo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// ONBOARDING INITIALIZATION WIZARD - Real camera/gallery
// ============================================================
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingInitializationWizard(
    viewModel: WalletViewModel
) {
    val context = LocalContext.current
    val documentsCaptured = remember { mutableStateMapOf<String, String>() }
    val documentsAssetType = remember { mutableStateMapOf<String, String>() }
    var onboardPendingDocType by remember { mutableStateOf<String?>(null) }
    var onboardPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var onboardPhotoFile by remember { mutableStateOf<File?>(null) }

    val onboardCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && onboardPendingDocType != null && onboardPhotoFile != null) {
            documentsCaptured[onboardPendingDocType!!] = onboardPhotoFile!!.absolutePath
            documentsAssetType[onboardPendingDocType!!] = "IMAGE"
        }
        onboardPendingDocType = null
    }

    val onboardCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onboardPhotoUri?.let { onboardCameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permiso de camara requerido para capturar documentos", Toast.LENGTH_SHORT).show()
        }
    }

    val onboardGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        try {
            if (uri != null && onboardPendingDocType != null) {
                val mimeType = context.contentResolver.getType(uri)
                val path = uri.path
                val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true || 
                            path?.endsWith(".pdf", ignoreCase = true) == true
                val ext = if (isPdf) ".pdf" else ".jpg"
                val assetType = if (isPdf) "PDF" else "IMAGE"
                val destFile = FileStorageUtil.saveUriToFile(context, uri, "DOC_${onboardPendingDocType}_${System.currentTimeMillis()}$ext")
                if (destFile != null) {
                    documentsCaptured[onboardPendingDocType!!] = destFile.absolutePath
                    documentsAssetType[onboardPendingDocType!!] = assetType
                    Toast.makeText(context, "Archivo cargado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al procesar archivo", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al importar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            onboardPendingDocType = null
        }
    }

    val onboardStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onboardGalleryLauncher.launch("*/*")
        } else {
            Toast.makeText(context, "Permiso de almacenamiento requerido", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedType by remember { mutableStateOf("") }
    var placaInput by remember { mutableStateOf("") }
    var marcaInput by remember { mutableStateOf("") }
    var modeloInput by remember { mutableStateOf("") }
    var colorInput by remember { mutableStateOf("") }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textPrimaryColor = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondaryColor = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkSurface else LightSurface
    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val royalBlueColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)
    val CarbonAccent = Color(0xFF1E293B)
    val LightCarbonAccent = Color(0xFFF1F5F9)

    val activeDocSlotsForProgress = when (selectedType) {
        "MOTO" -> listOf("DNI", "LICENCIA", "TIV", "SOAT")
        "AUTO" -> listOf("DNI", "LICENCIA", "TIV", "SOAT", "CITV")
        else -> listOf("DNI", "LICENCIA", "TIV", "SOAT", "CITV", "PESOS_Y_MEDIDAS")
    }
    val totalFields = if (selectedType.isEmpty()) 4 else 4 + activeDocSlotsForProgress.size
    var completedFields = 0
    if (placaInput.isNotBlank()) completedFields++
    if (marcaInput.isNotBlank()) completedFields++
    if (modeloInput.isNotBlank()) completedFields++
    if (colorInput.isNotBlank()) completedFields++
    if (selectedType.isNotEmpty()) {
        activeDocSlotsForProgress.forEach { docType ->
            if (documentsCaptured.containsKey(docType)) completedFields++
        }
    }
    val progressFraction = completedFields.toFloat() / totalFields.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 32.dp, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(royalBlueColor).shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.Wallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("Guantera Digital", color = royalBlueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            val isDarkMode by viewModel.isDarkMode.collectAsState()
            IconButton(onClick = { viewModel.toggleTheme() }, modifier = Modifier.testTag("wizard_theme_toggle")) {
                Icon(imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = "Cambiar Tema", tint = royalBlueColor)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Que tipo de vehiculo deseas registrar?", color = textPrimaryColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Selecciona una categoria para iniciar el registro de documentos.", color = textSecondaryColor, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val options = listOf(
                Triple("MOTO", "Motocicleta", Icons.Filled.TwoWheeler),
                Triple("AUTO", "Auto particular", Icons.Filled.DirectionsCar),
                Triple("CAMIONETA", "Camioneta", Icons.Filled.LocalShipping)
            )

            options.forEach { (type, label, icon) ->
                val isSelected = selectedType == type
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "categoryCardScale"
                )

                Card(
                    onClick = {
                        selectedType = type
                        documentsCaptured.clear()
                        documentsAssetType.clear()
                    },
                    modifier = Modifier.weight(1f).height(115.dp).scale(scale).testTag("wizard_type_select_${type}"),
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) royalBlueColor else cardBorderColor),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) royalBlueColor.copy(alpha = 0.08f) else surfaceColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) royalBlueColor else (if (isDark) CarbonAccent else LightCarbonAccent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) Color.White else textSecondaryColor, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(label, color = if (isSelected) royalBlueColor else textPrimaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedType.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorderColor),
                colors = CardDefaults.cardColors(containerColor = surfaceColor)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Filled.BorderColor, contentDescription = null, tint = royalBlueColor, modifier = Modifier.size(20.dp))
                            Text("FICHA VEHICULAR OFICIAL", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().background(color = if (isDark) Color.Black.copy(alpha = 0.15f) else Color(0xFFF1F5F9), shape = RoundedCornerShape(12.dp)).border(1.dp, cardBorderColor, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Progreso del Registro:", color = textSecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("${(progressFraction * 100).toInt()}% ($completedFields de $totalFields)", color = royalBlueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = royalBlueColor,
                                trackColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                            )
                        }
                    }

                    HorizontalDivider(color = cardBorderColor)

                    OutlinedTextField(
                        value = placaInput,
                        onValueChange = { raw -> placaInput = if (selectedType == "MOTO") WalletValidators.formatMotorcyclePlate(raw) else WalletValidators.formatAutoPlate(raw) },
                        label = { Text("Placa de Rodaje", color = textSecondaryColor) },
                        placeholder = { Text(if (selectedType == "MOTO") "Ej. 1234-XYZ" else "Ej. ABC-123", color = textSecondaryColor.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wizard_placa_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = royalBlueColor, unfocusedBorderColor = cardBorderColor, focusedLabelColor = royalBlueColor, unfocusedLabelColor = textSecondaryColor, focusedContainerColor = if (isDark) Color.Transparent else Color.White, unfocusedContainerColor = if (isDark) Color.Transparent else Color.White)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = marcaInput, onValueChange = { marcaInput = it }, label = { Text("Marca", color = textSecondaryColor) }, singleLine = true, modifier = Modifier.weight(1f).testTag("wizard_marca_input"), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = royalBlueColor, unfocusedBorderColor = cardBorderColor, focusedLabelColor = royalBlueColor, unfocusedLabelColor = textSecondaryColor, focusedContainerColor = if (isDark) Color.Transparent else Color.White, unfocusedContainerColor = if (isDark) Color.Transparent else Color.White))
                        OutlinedTextField(value = modeloInput, onValueChange = { modeloInput = it }, label = { Text("Modelo", color = textSecondaryColor) }, singleLine = true, modifier = Modifier.weight(1f).testTag("wizard_modelo_input"), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = royalBlueColor, unfocusedBorderColor = cardBorderColor, focusedLabelColor = royalBlueColor, unfocusedLabelColor = textSecondaryColor, focusedContainerColor = if (isDark) Color.Transparent else Color.White, unfocusedContainerColor = if (isDark) Color.Transparent else Color.White))
                    }

                    OutlinedTextField(value = colorInput, onValueChange = { colorInput = it }, label = { Text("Color Oficial", color = textSecondaryColor) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("wizard_color_input"), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor, focusedBorderColor = royalBlueColor, unfocusedBorderColor = cardBorderColor, focusedLabelColor = royalBlueColor, unfocusedLabelColor = textSecondaryColor, focusedContainerColor = if (isDark) Color.Transparent else Color.White, unfocusedContainerColor = if (isDark) Color.Transparent else Color.White))

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Icon(imageVector = Icons.Filled.Assignment, contentDescription = null, tint = royalBlueColor, modifier = Modifier.size(20.dp))
                        Text("DOCUMENTACION OFICIAL", color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }

                    HorizontalDivider(color = cardBorderColor)

                    val activeDocSlots = when (selectedType) {
                        "MOTO" -> listOf("DNI" to "DNI del Propietario", "LICENCIA" to "Licencia de Conducir Clase B-IIb", "TIV" to "Tarjeta de Identificacion Vehicular", "SOAT" to "SOAT Electronico Vigente")
                        "AUTO" -> listOf("DNI" to "DNI del Propietario", "LICENCIA" to "Licencia de Conducir Clase A-I", "TIV" to "Tarjeta de Identificacion Vehicular", "SOAT" to "SOAT Electronico Particular", "CITV" to "Inspeccion Tecnica Vehicular (CITV)")
                        else -> listOf("DNI" to "DNI del Propietario", "LICENCIA" to "Licencia de Conducir", "TIV" to "Tarjeta de Identificacion Vehicular", "SOAT" to "SOAT Electronico", "CITV" to "Inspeccion Tecnica Vehicular (CITV)", "PESOS_Y_MEDIDAS" to "Certificado de Pesos y Medidas")
                    }

                    activeDocSlots.forEach { (docType, docLabel) ->
                        val isLocked = documentsCaptured.containsKey(docType)
                        val fileDocPath = documentsCaptured[docType] ?: ""

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0D1020) else Color.White),
                            border = BorderStroke(1.dp, if (isLocked) StateVigenteDot else cardBorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(docType, color = textPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(docLabel, color = textSecondaryColor, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    if (isLocked) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.background(StateVigenteDot.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).border(1.dp, StateVigenteDot, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = StateVigenteDot, modifier = Modifier.size(11.dp))
                                            Text("Resguardado", color = StateVigenteDot, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.background(StateVencidoDot.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).border(1.dp, StateVencidoDot, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.Error, contentDescription = null, tint = StateVencidoDot, modifier = Modifier.size(11.dp))
                                            Text("Pendiente", color = StateVencidoDot, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            try {
                                                onboardPendingDocType = docType
                                                val photoFile = FileStorageUtil.createImageFile(context)
                                                val photoUri = FileStorageUtil.getUriForFile(context, photoFile)
                                                onboardPhotoUri = photoUri
                                                onboardPhotoFile = photoFile
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    onboardCameraLauncher.launch(photoUri)
                                                } else {
                                                    onboardCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Error al preparar la cámara: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("onboarding_capture_${docType}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = royalBlueColor, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Camara", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                onboardPendingDocType = docType
                                                val permissionToCheck = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                    "android.permission.READ_MEDIA_IMAGES"
                                                } else {
                                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                                }
                                                if (ContextCompat.checkSelfPermission(context, permissionToCheck) == PackageManager.PERMISSION_GRANTED) {
                                                    onboardGalleryLauncher.launch("*/*")
                                                } else {
                                                    onboardStoragePermissionLauncher.launch(permissionToCheck)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Error al abrir la galería: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("onboarding_gallery_${docType}"),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Galeria", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (placaInput.isBlank() || marcaInput.isBlank() || modeloInput.isBlank() || colorInput.isBlank()) {
                                Toast.makeText(context, "Error: Por favor completa los campos del vehiculo.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val validationError = viewModel.createAndAddVehicle(
                                tipo = selectedType,
                                placa = placaInput,
                                marca = marcaInput,
                                modelo = modeloInput,
                                color = colorInput,
                                customPaths = documentsCaptured.toMap()
                            )
                            if (validationError != null) {
                                Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Registro de ${selectedType} completado exitosamente", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_save_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = royalBlueColor, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FINALIZAR REGISTRO", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
// WELCOME SPLASH SCREEN
// ============================================================
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSplashScreen(
    onStart: () -> Unit,
    viewModel: WalletViewModel
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val royalBlueColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D70B8)
    val textPrimaryColor = if (isDark) com.example.ui.theme.TextPrimary else LightTextPrimary
    val textSecondaryColor = if (isDark) com.example.ui.theme.TextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkSurface else LightSurface

    val entersAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entersAnim.animateTo(targetValue = 1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        delay(1500)
        onStart()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            IconButton(onClick = { viewModel.toggleTheme() }, modifier = Modifier.testTag("splash_theme_toggle")) {
                Icon(imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = "Cambiar Tema", tint = royalBlueColor)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxWidth(0.9f).scale(entersAnim.value).alpha(entersAnim.value)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
                Box(modifier = Modifier.size(170.dp).blur(25.dp).background(Brush.radialGradient(colors = listOf(royalBlueColor.copy(alpha = 0.25f), Color.Transparent))))
                Card(
                    modifier = Modifier.size(130.dp).shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(62.dp)) {
                                Icon(imageVector = Icons.Filled.DirectionsCar, contentDescription = "Car Logo", tint = royalBlueColor, modifier = Modifier.size(52.dp).align(Alignment.Center))
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(StateVigenteDot).border(2.dp, surfaceColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Filled.Sync, contentDescription = "Local Sync", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Guantera Digital", color = royalBlueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("splash_title_brand"))
                Text("¡Bienvenido!", color = textPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("splash_welcome_headline"))
                Text("Tu portadocumentos vehicular digital con custodia offline", color = textSecondaryColor, fontWeight = FontWeight.Normal, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp).testTag("splash_subheadline"))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStart,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(0.7f).height(52.dp).testTag("splash_start_button"),
                colors = ButtonDefaults.buttonColors(containerColor = royalBlueColor, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text("EMPEZAR", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Continuar", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
