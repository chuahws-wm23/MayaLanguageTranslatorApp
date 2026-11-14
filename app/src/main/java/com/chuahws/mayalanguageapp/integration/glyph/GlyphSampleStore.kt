package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Private, opt-in glyph crop collection for research and validation.
 *
 * ONLY the cropped glyph is stored. Never store the full gallery photo, never
 * push anything to Firebase/network, and never treat an app annotation as verified.
 * Stored in noBackupFilesDir so Android's automatic backup does not copy images.
 */
object GlyphSampleStore {
    data class SignOption(val code: String, val description: String)
    val options = listOf(
        SignOption("UNSURE", "Not sure / needs review"),
        SignOption("UNKNOWN", "Known unsupported sign"),
        SignOption("32", "K'UH — God / deity"),
        SignOption("184", "K'INICH — Resplendent / sunny"),
        SignOption("291", "SIIP — Deity name"),
        SignOption("4", "NAAH — House / first"),
        SignOption("44", "TOK — Cloud / mist"),
        SignOption("507", "TZIMAH — Gourd"),
        SignOption("548", "HAAB — Year"),
        SignOption("122", "K'AK' — Fire"),
        SignOption("187", "K'ABA' — Name"),
        SignOption("1030", "K'AWIIL — Deity name"),
    )

    data class Annotation(
        val label: String,
        val drawingGroup: String,
        val sourceReference: String,
        val permissionConfirmed: Boolean,
        val inputSource: String,
        val modelUsed: String,
    )

    private const val MAX_SAMPLES = 200
    private const val ROOT_FOLDER = "maya_glyph_research_samples_v1"

    private fun folder(context: Context): File =
        File(context.noBackupFilesDir, ROOT_FOLDER)

    fun count(context: Context): Int =
        folder(context).listFiles()?.count { it.extension == "json" } ?: 0

    /** Public validation can also be covered by pure JVM tests. */
    fun validate(annotation: Annotation) {
        require(options.any { it.code == annotation.label }) {
            "Choose a recognised label, Unknown, or Not sure."
        }
        require(annotation.drawingGroup.trim().length in 2..80) {
            "Enter a drawing group ID (2–80 characters). Use the same ID for repeat photos."
        }
        require(annotation.sourceReference.length <= 300) {
            "Source reference is too long."
        }
        require(annotation.inputSource in listOf("camera", "gallery")) {
            "Unknown image source."
        }
        require(annotation.modelUsed in listOf("v1", "v2", "v3")) {
            "Unknown model identifier."
        }
    }

    fun save(
        context: Context,
        original: Bitmap,
        crop: GlyphCrop,
        annotation: Annotation,
    ): String {
        validate(annotation)
        require(count(context) < MAX_SAMPLES) {
            "The collection holds 200 samples. Export it and clear it before adding more."
        }
        val (x, y, w, h) = crop.pixels(original.width, original.height)
        require(w >= 25 && h >= 25) { "Crop is too small. Select one complete glyph." }

        val root = folder(context)
        check(root.isDirectory || root.mkdirs()) { "Could not create private sample folder." }

        val id = UUID.randomUUID().toString()
        val imageFile = File(root, "glyph_" + id + ".png")
        val recordFile = File(root, "glyph_" + id + ".json")
        var cut: Bitmap? = null
        var reduced: Bitmap? = null
        try {
            cut = Bitmap.createBitmap(original, x, y, w, h)
            val longest = max(w, h)
            reduced = if (longest > 1200) {
                val factor = 1200.0 / longest
                Bitmap.createScaledBitmap(
                    cut,
                    (w * factor).roundToInt().coerceAtLeast(1),
                    (h * factor).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else cut
            FileOutputStream(imageFile).use { stream ->
                check(reduced.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Could not encode cropped glyph image."
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(imageFile).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            for (existing in root.listFiles().orEmpty().filter { it.extension == "json" }) {
                val previous = runCatching { JSONObject(existing.readText()) }.getOrNull()
                if (previous?.optString("cropped_png_sha256") == sha) {
                    throw IllegalArgumentException(
                        "That exact crop was saved already. Use a different drawing or photo."
                    )
                }
            }
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
            val json = JSONObject()
                .put("schema_version", 1)
                .put("id", id)
                .put("image_filename", imageFile.name)
                .put("timestamp_utc", timestamp)
                .put("annotation_label", annotation.label)
                .put("drawing_group_id", annotation.drawingGroup.trim())
                .put("source_reference", annotation.sourceReference.trim())
                .put("export_reuse_permission_confirmed", annotation.permissionConfirmed)
                .put("image_source", annotation.inputSource)
                .put("model_used_when_collected", annotation.modelUsed)
                .put("cropped_png_sha256", sha)
                .put("review_status", "UNVERIFIED")
                .put("training_eligible", false)
                .put("image_width", reduced.width)
                .put("image_height", reduced.height)
                .put(
                    "crop_fractions",
                    JSONObject().put("left", crop.left).put("top", crop.top)
                        .put("right", crop.right).put("bottom", crop.bottom),
                )
            recordFile.writeText(json.toString(2), Charsets.UTF_8)
            return id
        } catch (e: Exception) {
            imageFile.delete()
            recordFile.delete()
            throw e
        } finally {
            if (reduced !== cut && reduced !== original) reduced?.recycle()
            if (cut !== original) cut?.recycle()
        }
    }

    /**
     * Called only by an explicit user-initiated system document picker.
     * Does not require storage permission and never uploads the ZIP.
     */
    fun exportZip(context: Context, destination: Uri): Int {
        val root = folder(context)
        val metadata = root.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()
        require(metadata.isNotEmpty()) { "No saved samples yet." }
        val valid = metadata.mapNotNull { record ->
            val json = runCatching { JSONObject(record.readText()) }.getOrNull()
                ?: return@mapNotNull null
            val image = File(root, json.optString("image_filename"))
            if (!image.isFile || image.parentFile != root) null else Triple(record, image, json)
        }
        require(valid.size == metadata.size) { "A sample is incomplete. Export cancelled." }

        val stream = context.contentResolver.openOutputStream(destination, "w")
            ?: error("Could not open export destination.")
        ZipOutputStream(stream.buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            val readme = """
                Maya glyph research images — opt-in export

                Includes only glyph crops explicitly saved by the app user, NOT
                original full-size camera or gallery photos.
                All sign labels are USER ANNOTATIONS and UNVERIFIED.
                A model prediction does not establish the correct sign reading.
                Do not put any row in training or final evaluation until its
                sign, source, license and drawing-group identity are reviewed.
                Store photos of the same drawing in ONE data partition.
                Do not redistribute any image without checking reuse rights.
                No data was uploaded automatically to Firebase or GitHub.

                Format: samples/glyph_<UUID>.png and .json; manifest.json.
                Validation script: ml/glyph_v4/ingest_phone_samples.py
            """.trimIndent()
            entry("README.txt", readme.toByteArray(Charsets.UTF_8))
            val records = JSONArray()
            for ((record, image, json) in valid) {
                zip.putNextEntry(ZipEntry("samples/" + image.name))
                FileInputStream(image).use { it.copyTo(zip) }
                zip.closeEntry()
                entry("samples/" + record.name, record.readBytes())
                records.put(json)
            }
            entry("manifest.json", records.toString(2).toByteArray(Charsets.UTF_8))
        }
        return valid.size
    }

    /** Irreversible. Only clears this app's research image collection. */
    fun deleteAll(context: Context): Int {
        val root = folder(context)
        val amount = count(context)
        if (root.exists()) check(root.deleteRecursively()) {
            "Could not delete collected samples."
        }
        return amount
    }
}
