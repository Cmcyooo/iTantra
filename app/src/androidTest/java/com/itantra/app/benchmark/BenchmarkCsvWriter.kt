package com.itantra.app.benchmark

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Standardized CSV Writer for iTantra on-device benchmarks.
 * Writes structured data to app external files dir (/sdcard/Android/data/com.itantra.app/files/benchmarks/)
 * ensuring 100% write permission on Android 12+ without SELinux denial.
 */
class BenchmarkCsvWriter(val file: File, private val header: List<String>) {

    companion object {
        private const val TAG = "BenchmarkCsvWriter"

        fun escape(value: Any?): String {
            if (value == null) return ""
            val str = value.toString()
            return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                "\"${str.replace("\"", "\"\"")}\""
            } else {
                str
            }
        }

        fun getBenchmarkFile(context: Context, category: String, subCategory: String, fileName: String): File {
            val baseDir = context.getExternalFilesDir(null)?.resolve("benchmarks")
                ?: File(context.filesDir, "benchmarks")
            val targetDir = File(baseDir, "$category/$subCategory")
            targetDir.mkdirs()
            return File(targetDir, fileName)
        }
    }

    init {
        try {
            file.parentFile?.mkdirs()
            if (!file.exists() || file.length() == 0L) {
                file.writeText(header.joinToString(",") + "\n", StandardCharsets.UTF_8)
            }
            Log.i(TAG, "Initialized benchmark CSV writer at: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CSV writer for ${file.absolutePath}", e)
        }
    }

    @Synchronized
    fun writeRow(row: List<Any?>) {
        try {
            val line = row.joinToString(",") { escape(it) } + "\n"
            FileOutputStream(file, true).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(line)
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error appending row to ${file.absolutePath}", e)
        }
    }
}
