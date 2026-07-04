package com.example.dao

import androidx.room.*
import com.example.model.Document
import com.example.model.Vehicle
import kotlinx.coroutines.flow.Flow

data class VehicleWithDocuments(
    @Embedded val vehicle: Vehicle,
    @Relation(
        parentColumn = "vehiculoId",
        entityColumn = "vehicleId"
    )
    val documents: List<Document>
) {
    fun toVehicle(): Vehicle {
        return vehicle.copy(documentos = documents)
    }
}

@Dao
interface VehicleDao {
    @Transaction
    @Query("SELECT * FROM vehicles")
    fun getAllVehiclesWithDocuments(): Flow<List<VehicleWithDocuments>>

    @Query("SELECT * FROM vehicles")
    suspend fun getOnlyVehicles(): List<Vehicle>

    @Query("SELECT * FROM documents WHERE vehicleId = :vehicleId")
    fun getDocumentsForVehicle(vehicleId: String): Flow<List<Document>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleOnly(vehicle: Vehicle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<Document>)

    @Transaction
    suspend fun insertVehicleWithDocuments(vehicle: Vehicle, documents: List<Document>) {
        insertVehicleOnly(vehicle)
        val docsToInsert = documents.map { it.copy(vehicleId = vehicle.vehiculoId) }
        insertDocuments(docsToInsert)
    }

    @Query("DELETE FROM vehicles WHERE vehiculoId = :vehicleId")
    suspend fun deleteVehicleById(vehicleId: String)

    @Query("UPDATE documents SET rutaArchivoLocal = :newPath, assetType = :assetType, sincronizado = 0 WHERE id = :docId")
    suspend fun updateDocumentPath(docId: String, newPath: String, assetType: String)

    @Query("UPDATE documents SET sincronizado = :sincronizado WHERE id = :docId")
    suspend fun updateDocumentSyncStatus(docId: String, sincronizado: Boolean)
}
