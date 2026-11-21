package com.chuahws.mayalanguageapp.presentation.research

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Article(val title: String, val body: String, val source: String)
private val articles = listOf(
    Article("Worked example: ka-ka-wa", "The syllabic spelling ka-ka-wa is a well-known example associated with kakaw (cacao). The written sign sequence and a linguistic transcription are different representations: a researcher does not simply pronounce every written vowel. Try arranging ka, ka and wa in the workspace, then consult the handbook's discussion of syllabic spelling. This example does not mean that arbitrary arrangements form attested words.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Transliteration versus transcription", "Transliteration records proposed sign values; transcription represents an interpretation of the linguistic form. A common scholarly convention writes logograms in capitals and syllable values in lower case. The workspace only uses source-labelled syllables, in lower case, with hyphens between signs. It does not reconstruct unwritten sounds or normalize spelling. Preserve this distinction when citing a result in research notes.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Numbers and calendrical context", "Maya numerical notation often uses dots for units and bars for fives, with a separate zero sign. Calendar expressions require identifying the relevant calendar and positional context, not just counting shapes. A date block can contain day names, month names and numerical coefficients. The workspace does not calculate dates or recognize numerical values. Consult the handbook's calendar sections before interpreting dates from an inscription.", "https://www.mesoweb.com/resources/handbook/"),
    Article("A research checklist for an inscription", "Record the object's site or collection, catalogue identifier, photograph source and orientation. Keep the full inscription alongside close-ups so block order is not lost. Separate what is visible from your proposed sign identification, transliteration and linguistic interpretation. Compare several documented drawings and cite the reference used. Mark unreadable or uncertain parts explicitly; a close image match is a research lead, not a confirmed decipherment.", "https://classicmayan.org/"),
    Article("How Maya writing works", "Maya writing combines word signs (logograms) and syllable signs (phonograms). A drawn sign is not necessarily one alphabetic letter. A sign may have more than one reading, and its role depends on context.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Reading a glyph block", "Signs can be grouped into a block. In many inscriptions, paired columns are read left to right, then down. Within a block, placement and the relationship between a main sign and its affixes matter. A simple grid is a study aid, not a rule that deciphers every inscription.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Syllables and transliteration", "Syllable signs commonly represent a consonant and vowel. Researchers use Latin letters to transliterate signs. Hyphens separate sign values; they do not automatically represent a spoken word. Final vowels, spelling conventions and historical language require interpretation.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Word signs and phonetic complements", "A word sign can appear with syllable signs that help indicate its reading. These phonetic complements may repeat part of the word rather than adding another spoken syllable. Concatenating every visible sign can therefore produce a misleading reading.", "https://www.mesoweb.com/resources/handbook/"),
    Article("Variants and uncertain readings", "Different drawings can represent the same sign. Similar-looking signs can also have different readings. Compare catalogue numbers, multiple drawings and inscription context. A visual similarity percentage is not an identification probability.", "https://classicmayan.org/"),
    Article("Research sources and attribution", "This app uses TWKM reference drawings licensed CC BY 4.0 and source-proposed readings. Record the sign number, drawing identifier, source and uncertainty when discussing a result. Consult the original catalogue for updates; this app's study articles are introductory summaries, not expert-reviewed decipherments.", "https://classicmayan.org/")
)

@Composable
fun ResearchScreen(builder: Boolean = false) {
    if (builder) { GlyphAssembly(); return }
    var query by rememberSaveable { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("THE RESEARCH ROOM", style = MaterialTheme.typography.labelLarge) }
        item { Text("Explore Maya writing", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Search short study articles, then follow the sources for deeper research.") }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search topics or terms") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        val filtered = articles.filter { (it.title + it.body).contains(query.trim(), true) }
        if (filtered.isEmpty()) item { Text("No topics found. Try syllables, reading or variants.") }
        items(filtered) { article ->
            Card(onClick = { opened = if (opened == article.title) null else article.title }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(article.title, style = MaterialTheme.typography.titleLarge)
                    Text(if (opened == article.title) article.body else article.body.substringBefore(". ") + ".")
                    if (opened == article.title) Text(if (article.source.contains("mesoweb")) "Further reading: Kettunen & Helmke, Introduction to Maya Hieroglyphs (Mesoweb)." else "Reference: Text Database and Dictionary of Classic Mayan (TWKM).", style = MaterialTheme.typography.labelSmall)
                    if (opened == article.title) TextButton(onClick = { uri.openUri(article.source) }) { Text("Read the source ↗") }
                    else Text("Tap to read", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphAssembly() {
    val context = LocalContext.current
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val catalogue by produceState<GlyphVisualCatalogue?>(null, retry) {
        loadFailed = false
        value = withContext(Dispatchers.IO) { runCatching { GlyphVisualCatalogue.fromAssetsOrNull(context) }.getOrNull() }
        loadFailed = value == null
    }
    var query by rememberSaveable { mutableStateOf("") }
    var slots by rememberSaveable { mutableStateOf(List(6) { "" }) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val bounds = remember { mutableMapOf<Int, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val refs = catalogue?.references.orEmpty().distinctBy { it.syllable }
    fun place(value: String, index: Int) { slots = slots.toMutableList().also { it[index] = value } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
    LazyColumn(modifier = Modifier.testTag("assembly-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("GLYPH WORKSPACE", style = MaterialTheme.typography.labelLarge) }
        item { Text("Build a study block", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Hold and drag a syllable drawing into a numbered slot, or select a slot and tap a drawing. Tap a filled slot to select it.") }
        stickyHeader {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(slots.filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "Your reading appears here" }, style = MaterialTheme.typography.titleLarge)
                    if (typing) Text("Hide the keyboard to place drawings.", style = MaterialTheme.typography.labelSmall)
                    repeat(if (typing) 0 else 3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) { col ->
                                val index = row * 2 + col
                                OutlinedCard(onClick = { selected = index }, modifier = Modifier.weight(1f).height(80.dp).onGloballyPositioned { bounds[index] = it.boundsInRoot() }) {
                                    Column(Modifier.fillMaxSize().background(if (selected == index || (dragging != null && bounds[index]?.contains(dragPosition) == true)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).padding(8.dp)) {
                                        Text("${index + 1} · ${slots[index].ifEmpty { "Empty" }}", style = MaterialTheme.typography.labelMedium)
                                        refs.firstOrNull { it.syllable == slots[index] }?.let { ref ->
                                            val image = remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }
                                            Image(image, "Syllable ${ref.syllable}", Modifier.fillMaxWidth().height(45.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { place("", selected) }, enabled = slots[selected].isNotEmpty()) { Text("Remove selected") }
                        TextButton(onClick = { slots = List(6) { "" } }) { Text("Clear block") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sign-by-sign transliteration", style = MaterialTheme.typography.titleMedium)
                    Text(slots.filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "Add signs to begin" }, style = MaterialTheme.typography.headlineMedium)
                    Text("Order: 1 → 2 → 3 → 4 → 5 → 6. This displays selected syllable values in Latin letters. It does not verify a Maya word, pronunciation or translation. Complements and spelling rules require interpretation.")
                    dragging?.let { Text("Dragging: $it — release over a slot") }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Find a syllable, e.g. ka") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (catalogue == null) item {
            if (loadFailed) { Text("Reference drawings could not be loaded."); TextButton(onClick = { retry++ }) { Text("Retry") } }
            else Text("Loading reference drawings…")
        }
        val filtered = refs.filter { it.syllable.contains(query.trim(), true) }
        if (catalogue != null && filtered.isEmpty()) item { Text("No syllables found. Try another spelling.") }
        items(filtered, key = { it.syllable }) { ref ->
            var origin by remember { mutableStateOf(Offset.Zero) }
            val image = remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }
            Card(onClick = { place(ref.syllable, selected); selected = (selected + 1) % 6 }, modifier = Modifier.fillMaxWidth().onGloballyPositioned { origin = it.boundsInRoot().topLeft }.pointerInput(ref.syllable, slots, selected) {
                detectDragGesturesAfterLongPress(onDragStart = { dragging = ref.syllable; dragPosition = origin + it }, onDrag = { change, amount -> change.consume(); dragPosition += amount }, onDragEnd = {
                    bounds.entries.firstOrNull { it.value.contains(dragPosition) }?.let { place(ref.syllable, it.key); selected = (it.key + 1) % 6 }
                    dragging = null
                }, onDragCancel = { dragging = null })
            }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(image, "Drag syllable ${ref.syllable}", Modifier.size(64.dp))
                    Column { Text(ref.syllable, style = MaterialTheme.typography.titleLarge); Text("TWKM #${ref.twkmSign} · ${ref.graph}"); Text("TWKM · CC BY 4.0", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    dragging?.let { syllable ->
        refs.firstOrNull { it.syllable == syllable }?.let { ref ->
            val bitmap = remember(ref) { catalogue!!.referenceBitmap(ref).asImageBitmap() }
            Image(bitmap, null, Modifier.offset { IntOffset((dragPosition.x-rootOrigin.x-32.dp.toPx()).roundToInt(), (dragPosition.y-rootOrigin.y-32.dp.toPx()).roundToInt()) }.size(64.dp).background(MaterialTheme.colorScheme.surface))
        }
    }
    }

}
