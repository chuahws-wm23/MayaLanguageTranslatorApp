package com.chuahws.mayalanguageapp.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphReferenceArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReferenceDrawing(graph: String,description: String,modifier: Modifier=Modifier,fallback: () -> Bitmap) {
    val context=LocalContext.current
    val image by produceState<Bitmap?>(null,context,graph) {
        value=null
        value=withContext(Dispatchers.IO) { GlyphReferenceArtwork.load(context,graph) ?: fallback() }
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        image?.let { Image(it.asImageBitmap(),contentDescription=description,
            modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
            ?: CircularProgressIndicator(Modifier.size(24.dp))
    }
}
