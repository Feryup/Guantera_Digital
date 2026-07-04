package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import androidx.room.ForeignKey
import androidx.room.Index

enum class DocumentStatus {
    VALID,     // Glowing Neon Green
    EXPIRING,  // Soft Amber/Orange
    EXPIRED    // Solid Crimson Red
}

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["vehiculoId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId"])]
)
data class Document(
    @PrimaryKey val id: String,
    val tipo: String, // DNI, LICENCIA, SOAT, TIV, CITV, PESOS_Y_MEDIDAS
    val friendlyName: String,
    val identificadorVisual: String,
    val rutaArchivoLocal: String,
    val urlServidor: String? = null,
    val fechaVencimiento: String, // yyyy-MM-dd
    val requiereAlerta: Boolean = true,
    var sincronizado: Boolean = false,
    var assetType: String = "IMAGE", // "IMAGE", "PDF", or "CAMERA"
    val vehicleId: String = ""
) {
    val status: DocumentStatus
        get() = calculateStatus(fechaVencimiento)

    private fun calculateStatus(dateStr: String): DocumentStatus {
        try {
            val parts = dateStr.split("-")
            if (parts.size != 3) return DocumentStatus.VALID
            
            val year = parts[0].toIntOrNull() ?: 2026
            val month = parts[1].toIntOrNull() ?: 6
            val day = parts[2].toIntOrNull() ?: 14
            
            // Reference current time is 2026-06-14 (from prompt metadata)
            val currYear = 2026
            val currMonth = 6
            val currDay = 14
            
            if (year < currYear) return DocumentStatus.EXPIRED
            if (year == currYear && month < currMonth) return DocumentStatus.EXPIRED
            if (year == currYear && month == currMonth && day < currDay) return DocumentStatus.EXPIRED
            
            // Calculate approximate days difference (365 days/year, 30.4 days/month)
            val dateDays = (year * 365) + (month * 30.4) + day
            val currDays = (currYear * 365) + (currMonth * 30.4) + currDay
            val diff = dateDays - currDays
            
            return when {
                diff <= 0 -> DocumentStatus.EXPIRED
                diff <= 30 -> DocumentStatus.EXPIRING
                else -> DocumentStatus.VALID
            }
        } catch (e: Exception) {
            return DocumentStatus.VALID
        }
    }
}

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val vehiculoId: String,
    val tipoVehiculo: String, // MOTO, AUTO, CAMIONETA
    val placa: String, // format e.g. "ABC-123" or "1234-AB"
    val marca: String,
    val modelo: String,
    val color: String,
    @Ignore val documentos: List<Document> = emptyList()
) {
    // Secondary constructor for Room to instantiate without the ignored documentos list
    constructor(
        vehiculoId: String,
        tipoVehiculo: String,
        placa: String,
        marca: String,
        modelo: String,
        color: String
    ) : this(vehiculoId, tipoVehiculo, placa, marca, modelo, color, emptyList())
}

object WalletValidators {
    // strict 8-digit limits for DNI
    fun validateDni(dni: String): Boolean {
        return dni.matches(Regex("^[0-9]{8}$"))
    }

    // Auto plate code: 3 letters, hyphen, 3 numbers (Peruvian standard) e.g., "ABC-123"
    fun validateAutoPlate(plate: String): Boolean {
        return plate.uppercase().matches(Regex("^[A-Z]{3}-[0-9]{3}$"))
    }

    // Motorcycle plate code: 4 numbers, hyphen, 2 or 3 letters/numbers e.g., "1234-AB" or "1234-ABC"
    fun validateMotorcyclePlate(plate: String): Boolean {
        return plate.uppercase().matches(Regex("^[0-9]{4}-[A-Z0-9]{2,3}$"))
    }
    
    // Auto Plate mask builder
    fun formatAutoPlate(input: String): String {
        val clean = input.replace("-", "").uppercase().take(6)
        return if (clean.length > 3) {
            "${clean.substring(0, 3)}-${clean.substring(3)}"
        } else {
            clean
        }
    }

    // Motorcycle Plate mask builder
    fun formatMotorcyclePlate(input: String): String {
        val clean = input.replace("-", "").uppercase().take(7)
        return if (clean.length > 4) {
            "${clean.substring(0, 4)}-${clean.substring(4)}"
        } else {
            clean
        }
    }
}
