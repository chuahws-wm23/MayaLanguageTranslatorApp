package com.chuahws.mayalanguageapp.presentation.research

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun GlyphWorkspaceScreen() {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val catalogue by produceState<GlyphVisualCatalogue?>(null, retry) {
        failed = false
        value = withContext(Dispatchers.IO) { runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull() }
        failed = value == null
    }
    val refs = catalogue?.references.orEmpty().distinctBy { it.syllable }
    var parts by rememberSaveable { mutableStateOf(listOf("", "")) }
    var xs by rememberSaveable { mutableStateOf(listOf(.28f, .72f)) }
    var ys by rememberSaveable { mutableStateOf(listOf(.5f, .5f)) }
    var result by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var canvas by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(Offset.Zero) }
    fun clear() { parts = listOf("", ""); xs = listOf(.28f,.72f); ys = listOf(.5f,.5f); result = false }
    fun drop(value: String, source: Int?) {
        if (canvas.width <= 0 || !canvas.contains(position)) return
        val index = source ?: parts.indexOfFirst { it.isEmpty() }
        if (index < 0) return
        parts = parts.toMutableList().also { it[index] = value }
        xs = xs.toMutableList().also { it[index] = ((position.x-canvas.left)/canvas.width).coerceIn(.15f,.85f) }
        ys = ys.toMutableList().also { it[index] = ((position.y-canvas.top)/canvas.height).coerceIn(.22f,.78f) }
    }
    fun Modifier.draggablePart(value: String, source: Int?, origin: () -> Offset): Modifier = pointerInput(value, source, parts, canvas) {
        detectDragGesturesAfterLongPress(
            onDragStart = { focus.clearFocus(); dragging = value; position = origin() + it },
            onDrag = { change, amount -> change.consume(); position += amount },
            onDragEnd = { drop(value, source); dragging = null },
            onDragCancel = { dragging = null })
    }
    BackHandler(enabled = result) { result = false }
    if (result) {
        val order = (0..1).sortedWith(compareBy<Int> { xs[it] }.thenBy { ys[it] }.thenBy { it })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Your combination", style = MaterialTheme.typography.headlineLarge)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(parts.indices.sortedWith(compareBy<Int> { xs[it] }.thenBy { ys[it] }.thenBy { it }).joinToString("-") { parts[it] }, style = MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        order.forEach { index -> refs.firstOrNull { it.syllable == parts[index] }?.let { ref ->
                            Image(remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }, "Result syllable ${ref.syllable}", Modifier.weight(1f).height(96.dp))
                        } }
                    }
                    Text("Syllable values in left-to-right order. This is a sign sequence, not a verified Maya word or translation.")
                }
            }
            Button(onClick = { result = false }, modifier = Modifier.fillMaxWidth()) { Text("Edit combination") }
            OutlinedButton(onClick = { clear() }, modifier = Modifier.fillMaxWidth()) { Text("Start again") }
        }
        return
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!typing) {
            Text("Combine two syllables", style = MaterialTheme.typography.headlineSmall)
            Text("Hold and drag two cards onto the canvas. Move them together; reading order is left to right.", style = MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth().height(200.dp).testTag("syllable-canvas").onGloballyPositioned { canvas = it.boundsInRoot() }) {
                BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer)) {
                    if (parts.all { it.isEmpty() }) Text("Drop two syllable cards here", Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
                    parts.forEachIndexed { index, value -> if (value.isNotEmpty()) {
                        var origin by remember(index) { mutableStateOf(Offset.Zero) }
                        val ref = refs.firstOrNull { it.syllable == value }
                        Card(onClick = {}, modifier = Modifier.offset { IntOffset((maxWidth.toPx()*xs[index]-36.dp.toPx()).roundToInt(), (maxHeight.toPx()*ys[index]-40.dp.toPx()).roundToInt()) }
                            .size(72.dp,80.dp).testTag("placed-$index").onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                            .draggablePart(value,index) { origin }) {
                            Column(Modifier.padding(6.dp)) {
                                ref?.let { Image(remember(it) { catalogue!!.referenceBitmap(it).asImageBitmap() }, "Placed syllable $value", Modifier.fillMaxWidth().height(42.dp)) }
                                Text(value, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${parts.count { it.isNotEmpty() }} / 2 syllables")
                TextButton(onClick = { clear() }, enabled = parts.any { it.isNotEmpty() }) { Text("Clear canvas") }
            }
            }
            Button(onClick = { focus.clearFocus(); result = true }, enabled = parts.all { it.isNotEmpty() }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            LazyColumn(Modifier.weight(1f).testTag("syllable-palette"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { OutlinedTextField(query,{ query=it }, label = { Text("Find a syllable") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Text(if (parts.all { it.isNotEmpty() }) "Move the cards, or tap Clear canvas to choose different syllables." else "Hold to drag, or tap a card to add it.", style = MaterialTheme.typography.bodySmall) }
                if (catalogue == null) item { if (failed) TextButton(onClick = { retry++ }) { Text("Retry loading drawings") } else Text("Loading syllables…") }
                val filtered = refs.filter { it.syllable.contains(query.trim(),true) }
                if (catalogue != null && filtered.isEmpty()) item { Text("No syllables found") }
                items(filtered,key={it.syllable}) { ref ->
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    Card(onClick = {
                        focus.clearFocus()
                        val empty = parts.indexOfFirst { it.isEmpty() }
                        if (empty >= 0) parts = parts.toMutableList().also { it[empty] = ref.syllable }
                    }, enabled = parts.any { it.isEmpty() }, modifier = Modifier.fillMaxWidth().onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                        .then(if (parts.any { it.isEmpty() }) Modifier.draggablePart(ref.syllable,null) { origin } else Modifier)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Image(remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }, "Syllable card ${ref.syllable}", Modifier.size(56.dp))
                            Column { Text(ref.syllable,style=MaterialTheme.typography.titleLarge); Text("TWKM #${ref.twkmSign} · ${ref.graph}"); Text("TWKM · CC BY 4.0",style=MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
        dragging?.let { value -> refs.firstOrNull { it.syllable == value }?.let { ref ->
            Image(remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }, null, Modifier.offset { IntOffset((position.x-rootOrigin.x-28.dp.toPx()).roundToInt(), (position.y-rootOrigin.y-28.dp.toPx()).roundToInt()) }.size(56.dp).background(MaterialTheme.colorScheme.surface))
        } }
    }
}
