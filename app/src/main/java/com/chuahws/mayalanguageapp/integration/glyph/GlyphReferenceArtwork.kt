package com.chuahws.mayalanguageapp.integration.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONObject
import java.security.MessageDigest

/** Source-attributed artwork for display. Inference uses normalized bitplanes. */
internal object GlyphReferenceArtwork {
    private data class Entry(val file: String,val sha: String,val width: Int,val height: Int)
    private val images=object:LruCache<String,Bitmap>(12*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    @Volatile private var entries: Map<String,Entry>?=null
    private fun index(context: Context): Map<String,Entry> = entries ?: synchronized(this) {
        entries ?: run {
            val root=JSONObject(context.assets.open("reference_art/index.json").bufferedReader().use { it.readText() })
            require(root.getInt("format_version")==1)
            val rows=root.getJSONObject("entries")
            val result=rows.keys().asSequence().associateWith { graph ->
                val row=rows.getJSONObject(graph)
                require(row.getString("license") in setOf("CC BY 4.0", "CC BY-SA 4.0"))
                val file=row.getString("file")
                require(file.matches(Regex("[a-f0-9]{64}\\.(jpg|png|webp)")))
                val w=row.getInt("width");val h=row.getInt("height")
                require(w in 1..4096 && h in 1..4096)
                Entry(file,row.getString("sha256"),w,h)
            }
            result.also { entries=it }
        }
    }
    /** Worker dispatcher only; decoded images are bounded by a 12 MiB cache. */
    fun load(context: Context,graph: String): Bitmap? = images.get(graph) ?: runCatching {
        val entry=index(context.applicationContext)[graph] ?: return null
        val raw=context.assets.open("reference_art/${entry.file}").use { it.readBytes() }
        val sha=MessageDigest.getInstance("SHA-256").digest(raw)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(sha==entry.sha)
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(raw,0,raw.size,
            BitmapFactory.Options().apply { inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888 }))
        require(bitmap.width==entry.width && bitmap.height==entry.height)
        images.put(graph,bitmap)
        bitmap
    }.getOrNull()
}
