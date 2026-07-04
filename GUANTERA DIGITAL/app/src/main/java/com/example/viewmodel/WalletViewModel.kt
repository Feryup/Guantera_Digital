package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Document
import com.example.model.Vehicle
import com.example.model.WalletValidators
import com.example.db.AppDatabase
import com.example.util.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val vehicleDao = db.vehicleDao()
    private val preferencesRepository = UserPreferencesRepository(application)

    private val _hasAcceptedTerms = MutableStateFlow<Boolean?>(null)
    val hasAcceptedTerms: StateFlow<Boolean?> = _hasAcceptedTerms.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _selectedVehicleIndex = MutableStateFlow(0)
    val selectedVehicleIndex: StateFlow<Int> = _selectedVehicleIndex.asStateFlow()

    // Interactive active selected document
    private val _selectedDocumentId = MutableStateFlow<String?>(null)
    val selectedDocumentId: StateFlow<String?> = _selectedDocumentId.asStateFlow()

    // Modo intervención (fullscreen visual, maximum brightness, maximum contrast)
    private val _isModoIntervencionActive = MutableStateFlow(false)
    val isModoIntervencionActive: StateFlow<Boolean> = _isModoIntervencionActive.asStateFlow()

    private val _isContrastInverted = MutableStateFlow(false)
    val isContrastInverted: StateFlow<Boolean> = _isContrastInverted.asStateFlow()

    // Simulated local logs/notifications of mock system brightness commands
    private val _systemStatusMessage = MutableStateFlow<String?>(null)
    val systemStatusMessage: StateFlow<String?> = _systemStatusMessage.asStateFlow()

    // Light & Dark theme state controller
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Interactive custom camera capture simulation
    private val _isCameraViewfinderActive = MutableStateFlow(false)
    val isCameraViewfinderActive: StateFlow<Boolean> = _isCameraViewfinderActive.asStateFlow()

    private val _cameraTargetDocId = MutableStateFlow<String?>(null)
    val cameraTargetDocId: StateFlow<String?> = _cameraTargetDocId.asStateFlow()

    init {
        // Collect real-time database flow of vehicles with documents
        viewModelScope.launch {
            vehicleDao.getAllVehiclesWithDocuments().collect { list ->
                val mappedVehicles = list.map { it.toVehicle() }
                _vehicles.value = mappedVehicles
                
                // Automatically assign default selected document when first populated
                if (_selectedDocumentId.value == null && mappedVehicles.isNotEmpty()) {
                    _selectedDocumentId.value = mappedVehicles.first().documentos.firstOrNull()?.id
                }
            }
        }
        
        // Collect DataStore terms acceptance state
        viewModelScope.launch {
            preferencesRepository.hasAcceptedTermsFlow.collect { accepted ->
                _hasAcceptedTerms.value = accepted
            }
        }

        // Collect DataStore user name state
        viewModelScope.launch {
            preferencesRepository.userNameFlow.collect { name ->
                _userName.value = name
            }
        }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            preferencesRepository.setHasAcceptedTerms(true)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            preferencesRepository.setUserName(name)
        }
    }

    fun selectVehicle(index: Int) {
        if (index in 0 until _vehicles.value.size) {
            _selectedVehicleIndex.value = index
            // Select first document of this vehicle automatically
            val docs = _vehicles.value[index].documentos
            if (docs.isNotEmpty()) {
                _selectedDocumentId.value = docs.first().id
            } else {
                _selectedDocumentId.value = null
            }
        }
    }

    fun selectDocument(docId: String) {
        _selectedDocumentId.value = docId
    }

    fun toggleModoIntervencion() {
        val nextState = !_isModoIntervencionActive.value
        _isModoIntervencionActive.value = nextState
        if (nextState) {
            _isContrastInverted.value = true
            _systemStatusMessage.value = "SYS: Force Screen Brightness 100% | High-Contrast Scan Overlay Activated."
        } else {
            _isContrastInverted.value = false
            _systemStatusMessage.value = "SYS: Screen Brightness Restored to Normal."
        }
    }

    fun dismissSystemStatusMessage() {
        _systemStatusMessage.value = null
    }

    fun closeModoIntervencion() {
        _isModoIntervencionActive.value = false
        _isContrastInverted.value = false
        _systemStatusMessage.value = "SYS: Screen Brightness Restored."
    }

    // Capture Simulation
    fun openCameraCapture(docId: String) {
        _cameraTargetDocId.value = docId
        _isCameraViewfinderActive.value = true
    }

    fun captureMockImage() {
        val docId = _cameraTargetDocId.value ?: return
        val randSuffix = UUID.randomUUID().toString().take(6)
        val mockPath = "/app/sandbox/files/docs/captured_IMG_$randSuffix.jpg"
        
        // Update document's local path and state in database
        updateDocumentPath(docId, mockPath, "CAMERA")
        
        _isCameraViewfinderActive.value = false
        _cameraTargetDocId.value = null
        _systemStatusMessage.value = "FOTO CAPTURADA: Guardada en $mockPath"
    }

    fun closeCameraViewfinder() {
        _isCameraViewfinderActive.value = false
        _cameraTargetDocId.value = null
    }

    // Attach File and update database path reactively
    fun updateDocumentPath(docId: String, newPath: String, assetType: String) {
        viewModelScope.launch {
            vehicleDao.updateDocumentPath(docId, newPath, assetType)
            
            // Ensure UI refreshes selected document properties
            if (_selectedDocumentId.value == docId) {
                _selectedDocumentId.value = null
                _selectedDocumentId.value = docId
            }

            if (_isCloudSyncEnabled.value) {
                triggerBackgroundSync()
            }
        }
    }

    // Add Vehicle into local SQLite Database asynchronously
    fun createAndAddVehicle(
        tipo: String, // "MOTO", "AUTO", "CAMIONETA"
        placa: String,
        marca: String,
        modelo: String,
        color: String,
        customPaths: Map<String, String> = emptyMap()
    ): String? {
        // Validate plate
        val isValidPlate = when (tipo) {
            "MOTO" -> WalletValidators.validateMotorcyclePlate(placa)
            else -> WalletValidators.validateAutoPlate(placa)
        }
        if (!isValidPlate) {
            return if (tipo == "MOTO") "Error: Placa de Moto inválida. Debe ser ####-XX o ####-XXX (ej. 1234-XYZ)"
            else "Error: Placa de Vehículo inválida. Debe ser XXX-### (ej. ABC-123)"
        }

        // Generate documents for the vehicle type
        val vehicleId = "veh_" + tipo.lowercase() + "_" + UUID.randomUUID().toString().take(6)
        
        val defaultDni = Document(
            id = "doc_${vehicleId}_dni",
            tipo = "DNI",
            friendlyName = "DNI Propietario",
            identificadorVisual = "DNI Propietario del Vehículo",
            rutaArchivoLocal = customPaths["DNI"] ?: "/app/data/files/docs/dni_front.jpg",
            urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_DNI_ID/view?usp=sharing",
            fechaVencimiento = "2032-10-18",
            sincronizado = true,
            assetType = if (customPaths["DNI"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else "IMAGE",
            vehicleId = vehicleId
        )

        val defaultLicencia = Document(
            id = "doc_${vehicleId}_licencia",
            tipo = "LICENCIA",
            friendlyName = if (tipo == "MOTO") "Licencia Clase B" else "Licencia Clase A-I",
            identificadorVisual = if (tipo == "MOTO") "Licencia Moto B-IIb" else "Licencia Particular A-I",
            rutaArchivoLocal = customPaths["LICENCIA"] ?: "/app/data/files/docs/licencia.jpg",
            urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_LICENCIA_ID/view?usp=sharing",
            fechaVencimiento = "2029-04-12",
            sincronizado = true,
            assetType = if (customPaths["LICENCIA"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else "IMAGE",
            vehicleId = vehicleId
        )

        val defaultTiv = Document(
            id = "doc_${vehicleId}_tiv",
            tipo = "TIV",
            friendlyName = "Tarjeta Propiedad (TIV)",
            identificadorVisual = "TIV - Reg. Placa $placa",
            rutaArchivoLocal = customPaths["TIV"] ?: "/app/data/files/docs/tiv.jpg",
            urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_TIV_ID/view?usp=sharing",
            fechaVencimiento = "2036-12-31",
            sincronizado = true,
            assetType = if (customPaths["TIV"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else "IMAGE",
            vehicleId = vehicleId
        )

        val defaultSoat = Document(
            id = "doc_${vehicleId}_soat",
            tipo = "SOAT",
            friendlyName = "SOAT Electrónico",
            identificadorVisual = "SOAT Pacífico Placa $placa",
            rutaArchivoLocal = customPaths["SOAT"] ?: "/app/data/files/docs/soat_moto.pdf",
            urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_SOAT_ID/view?usp=sharing",
            fechaVencimiento = "2026-11-20", // Valid
            sincronizado = false,
            assetType = if (customPaths["SOAT"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else if (customPaths.containsKey("SOAT")) "IMAGE" else "PDF",
            vehicleId = vehicleId
        )

        val newDocs = mutableListOf(defaultDni, defaultLicencia, defaultTiv, defaultSoat)

        if (tipo == "AUTO" || tipo == "CAMIONETA") {
            newDocs.add(
                Document(
                    id = "doc_${vehicleId}_citv",
                    tipo = "CITV",
                    friendlyName = "CITV Técnica",
                    identificadorVisual = "Inspección Oficial CITV",
                    rutaArchivoLocal = customPaths["CITV"] ?: "/app/data/files/docs/citv.jpg",
                    urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_CITV_ID/view?usp=sharing",
                    fechaVencimiento = "2026-07-15", // Expiring soon
                    sincronizado = true,
                    assetType = if (customPaths["CITV"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else "IMAGE",
                    vehicleId = vehicleId
                )
            )
        }

        if (tipo == "CAMIONETA") {
            newDocs.add(
                Document(
                    id = "doc_${vehicleId}_pesos",
                    tipo = "PESOS_Y_MEDIDAS",
                    friendlyName = "Certif. Pesos y Medidas",
                    identificadorVisual = "MTC Pesos Furgón/Pick-Up",
                    rutaArchivoLocal = customPaths["PESOS_Y_MEDIDAS"] ?: "/app/data/files/docs/pesos.pdf",
                    urlServidor = "https://drive.google.com/file/d/1v6_rLdD_XN9Z8Z3Z4D1_PESOS_ID/view?usp=sharing",
                    fechaVencimiento = "2026-05-02", // Expired
                    sincronizado = false,
                    assetType = if (customPaths["PESOS_Y_MEDIDAS"]?.endsWith(".pdf", ignoreCase = true) == true) "PDF" else if (customPaths.containsKey("PESOS_Y_MEDIDAS")) "IMAGE" else "PDF",
                    vehicleId = vehicleId
                )
            )
        }

        val newVehicle = Vehicle(
            vehiculoId = vehicleId,
            tipoVehiculo = tipo,
            placa = placa.uppercase(),
            marca = marca,
            modelo = modelo,
            color = color,
            documentos = newDocs
        )

        viewModelScope.launch {
            vehicleDao.insertVehicleWithDocuments(newVehicle, newDocs)
            
            // Adjust selected index dynamically after insertion
            val updatedList = vehicleDao.getOnlyVehicles()
            val newIndex = updatedList.indexOfFirst { it.vehiculoId == vehicleId }
            if (newIndex != -1) {
                _selectedVehicleIndex.value = newIndex
            } else {
                _selectedVehicleIndex.value = _vehicles.value.size
            }
            _selectedDocumentId.value = newDocs.first().id
            _systemStatusMessage.value = "VEHÍCULO AGREGADO: Placa ${placa.uppercase()} registrado."
        }
        return null
    }

    // Delete vehicle asynchronously using coroutines
    fun deleteVehicle(vehiculoId: String) {
        viewModelScope.launch {
            val currentList = _vehicles.value
            val indexToDelete = currentList.indexOfFirst { it.vehiculoId == vehiculoId }
            if (indexToDelete != -1) {
                val placaDeleted = currentList[indexToDelete].placa
                vehicleDao.deleteVehicleById(vehiculoId)
                
                // Recalculate selections
                val remainingList = _vehicles.value.filter { it.vehiculoId != vehiculoId }
                val currentSelected = _selectedVehicleIndex.value
                val nextSelected = when {
                    remainingList.isEmpty() -> 0
                    currentSelected >= remainingList.size -> remainingList.size - 1
                    else -> currentSelected
                }
                _selectedVehicleIndex.value = nextSelected
                
                val nextVehicle = remainingList.getOrNull(nextSelected)
                if (nextVehicle != null) {
                    _selectedDocumentId.value = nextVehicle.documentos.firstOrNull()?.id
                } else {
                    _selectedDocumentId.value = null
                }
                _systemStatusMessage.value = "VEHÍCULO ELIMINADO: Registro de placa $placaDeleted removido exitosamente."
            }
        }
    }

    // User Profile Information for "Gestión de Perfil"
    private val _userEmail = MutableStateFlow("john.doe@automotive-wallet.pe")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(true)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    fun updateUserProfile(name: String, email: String, syncEnabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUserName(name)
        }
        _userEmail.value = email
        _isCloudSyncEnabled.value = syncEnabled
        _systemStatusMessage.value = "PERFIL ACTUALIZADO: Cambios guardados correctamente."
        if (syncEnabled) {
            triggerBackgroundSync()
        }
    }

    fun triggerBackgroundSync() {
        viewModelScope.launch {
            if (!_isCloudSyncEnabled.value) {
                _systemStatusMessage.value = "ERROR DE SINC: Habilita la Sincronización Cloud para subir los datos."
                return@launch
            }
            
            _systemStatusMessage.value = "SINC EN CURSO: Conectando con servidor en segundo plano..."
            
            val unsyncedDocs = _vehicles.value.flatMap { it.documentos }.filter { !it.sincronizado }

            if (unsyncedDocs.isEmpty()) {
                delay(1000)
                _systemStatusMessage.value = "SINC COMPLETA: Todos los documentos locales están sincronizados."
                return@launch
            }

            // Sync each in the background with simulated delays
            launch {
                unsyncedDocs.forEach { doc ->
                    delay(1500) // simulated network upload delay
                    vehicleDao.updateDocumentSyncStatus(doc.id, true)
                    _systemStatusMessage.value = "SINCRONIZADO: ${doc.friendlyName} subido al servidor."
                }
                delay(1000)
                _systemStatusMessage.value = "SINC FINALIZADA: Todos los documentos cargados correctamente."
            }
        }
    }

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun addCustomDocument(vehicleId: String, name: String) {
        viewModelScope.launch {
            val docId = "doc_custom_" + UUID.randomUUID().toString().take(6)
            val newDoc = Document(
                id = docId,
                tipo = name.uppercase().replace(" ", "_"),
                friendlyName = name,
                identificadorVisual = "📄",
                rutaArchivoLocal = "",
                urlServidor = null,
                fechaVencimiento = "2030-12-31",
                requiereAlerta = false,
                sincronizado = false,
                assetType = "CAMERA",
                vehicleId = vehicleId
            )
            vehicleDao.insertDocuments(listOf(newDoc))
            _selectedDocumentId.value = docId
        }
    }

    fun testFormatDniInput(dni: String): Boolean {
        return WalletValidators.validateDni(dni)
    }
}
