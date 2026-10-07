package com.stoco.qrscanner.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun toCsv(sensors: List<Sensor>): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("PN,DEVEUI,APPEUI,APPKEY,ScannedAt\n")
        for (s in sensors) {
            val fields = listOf(s.pn, s.devEui, s.appEui, s.appKey, ts.format(Date(s.scannedAt)))
            sb.append(fields.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun escape(f: String): String {
        val needsQuote = f.contains(',') || f.contains('"') || f.contains('\n')
        return if (needsQuote) "\"" + f.replace("\"", "\"\"") + "\"" else f
    }

    private fun fileName() =
        "sensors_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"

    fun shareIntent(context: Context, sensors: List<Sensor>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName()).apply { writeText(toCsv(sensors)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Export CSV")
    }

    /** Saves to Downloads. Returns a display location, or null on failure. */
    fun saveToDownloads(context: Context, sensors: List<Sensor>): String? {
        val name = fileName()
        val csv = toCsv(sensors)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                "Downloads/$name"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                val f = File(dir, name).apply { writeText(csv) }
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
