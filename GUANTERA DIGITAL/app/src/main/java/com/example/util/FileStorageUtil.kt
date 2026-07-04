package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileStorageUtil {

    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "captured_docs")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile("DOC_${timeStamp}_", ".jpg", storageDir)
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveUriToFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val destinationFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyUriToAppStorage(context: Context, sourceUri: Uri, docId: String): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "DOC_${docId}_${timeStamp}.jpg"
        return saveUriToFile(context, sourceUri, fileName)
    }

    fun getDocumentFile(context: Context, path: String): File? {
        if (path.isEmpty()) return null
        val file = File(path)
        return if (file.exists()) file else null
    }
}