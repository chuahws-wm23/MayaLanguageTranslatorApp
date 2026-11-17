package com.chuahws.mayalanguageapp.presentation.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LearnScreen(userId: String) {
    val context = LocalContext.current
    val preferences = remember(userId) { context.getSharedPreferences("maya_learning_$userId", 0) }
    var completed by remember(userId) { mutableStateOf(preferences.getStringSet("completed", emptySet())!!.toSet()) }
    var best by remember(userId) { mutableIntStateOf(preferences.getInt("quiz_best", 0)) }
    var destination by rememberSaveable(userId) { mutableStateOf("home") }
    var questionIndex by rememberSaveable(userId) { mutableIntStateOf(0) }
    var answer by rememberSaveable(userId) { mutableIntStateOf(-1) }
    var score by rememberSaveable(userId) { mutableIntStateOf(0) }
    var number by rememberSaveable(userId) { mutableIntStateOf(23) }
    var challenge by rememberSaveable(userId) { mutableStateOf(false) }
    var target by rememberSaveable(userId) { mutableIntStateOf(37) }
    var checked by rememberSaveable(userId) { mutableStateOf(false) }
    BackHandler(destination != "home") { destination = "home" }
    val lesson = LearningContent.lessons.find { it.id == destination }
    val listState = key(destination, questionIndex) { rememberLazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("learning-content"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (destination != "home") item { TextButton(onClick = { destination = "home" }) { Text("← Learning hub") } }
        when {
            lesson != null -> {
                item { LearnHeading(lesson.title, lesson.introduction) }
                items(lesson.sections) { (title, body) -> LearningCard(title, body) }
                if (lesson.id == "order") item {
                    LearningCard("Typical sequence", "A1 → B1\n       ↓\nA2 → B2\n       ↓\nA3 → B3\n\nAn illustrative layout, not an analysis of your photograph.")
                }
                item {
                    Button(onClick = {
                        completed = completed + lesson.id
                        preferences.edit().putStringSet("completed", completed).apply()
                        destination = "home"
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (lesson.id in completed) "Return to learning hub" else "Mark lesson complete") }
                }
                item { ContentSources() }
            }
            destination == "quiz" -> {
                if (questionIndex >= LearningContent.questions.size) {
                    item { LearnHeading("Practice complete", "You answered $score of ${LearningContent.questions.size} correctly. Your best result is $best.") }
                    item { LearningCard("Keep exploring", "Review any lessons you found difficult, then try again. This score reflects these introductory questions, not proficiency in reading inscriptions.") }
                    item { Button(onClick = { questionIndex = 0; score = 0; answer = -1 }) { Text("Practise again") } }
                } else {
                    val q = LearningContent.questions[questionIndex]
                    item { LearnHeading("Test your understanding", "Question ${questionIndex + 1} of ${LearningContent.questions.size}") }
                    item { LinearProgressIndicator(progress = { questionIndex.toFloat() / LearningContent.questions.size }, modifier = Modifier.fillMaxWidth()) }
                    item { Text(q.prompt, style = MaterialTheme.typography.headlineSmall) }
                    items(q.choices.indices.toList()) { index ->
                        OutlinedButton(onClick = { answer = index }, enabled = answer == -1, modifier = Modifier.fillMaxWidth()) {
                            Text(q.choices[index], modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    if (answer >= 0) {
                        item { LearningCard(if (answer == q.answer) "Correct" else "Review this answer", "You chose: ${q.choices[answer]}\n\n${q.explanation}") }
                        item { Button(onClick = {
                            val nextScore = score + if (answer == q.answer) 1 else 0
                            score = nextScore
                            questionIndex++
                            answer = -1
                            if (questionIndex == LearningContent.questions.size && nextScore > best) {
                                best = nextScore
                                preferences.edit().putInt("quiz_best", best).apply()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (questionIndex == LearningContent.questions.lastIndex) "See results" else "Next question") } }
                    }
                }
            }
            destination == "numbers-play" -> {
                item { LearnHeading("Maya numbers playground", "Build ordinary base-twenty numbers from 0 to 399. Each dot is one; each bar is five.") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !challenge, onClick = { challenge = false; checked = false }, label = { Text("Explore") })
                    FilterChip(selected = challenge, onClick = { challenge = true; checked = false }, label = { Text("Challenge") })
                } }
                if (challenge) item { LearningCard("Build $target", "Adjust the twenties and ones levels, then check your answer.") }
                item { Text(if (challenge && !checked) "Your number" else "Your number: $number", style = MaterialTheme.typography.headlineMedium) }
                items(listOf(20, 1)) { place ->
                    val digit = if (place == 20) number / 20 else number % 20
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (place == 20) "Twenties · upper level" else "Ones · lower level", style = MaterialTheme.typography.titleMedium)
                            NumberDrawing(digit)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(enabled = digit > 0, onClick = { number -= place; checked = false }) { Text("− ${if (place == 20) "twenty" else "one"}") }
                                OutlinedButton(enabled = digit < 19, onClick = { number += place; checked = false }) { Text("+ ${if (place == 20) "twenty" else "one"}") }
                            }
                        }
                    }
                }
                if (!challenge || checked) item { LearningCard("Place value", "${number / 20} × 20 + ${number % 20} × 1 = $number. A zero upper level can be omitted.") }
                if (challenge) {
                    item { Button(onClick = { checked = true }, modifier = Modifier.fillMaxWidth()) { Text("Check number") } }
                    if (checked) item {
                        LearningCard(if (number == target) "Correct — you built $target" else "Keep trying", if (number == target) "Try another number to practise place value." else "Your levels total $number. The target is $target.")
                        TextButton(onClick = { target = (target + 53) % 400; number = 0; checked = false }) { Text("New challenge") }
                    }
                }
                item { LearningCard("Zero and calendar notation", "The outlined oval is a simplified placeholder for the shell-like zero sign, not a historical tracing. This activity uses ordinary base twenty; Long Count calendar units follow a different sequence after twenties.") }
                item { ContentSources() }
            }
            else -> {
                item { LearnHeading("Discover Maya writing", "Read a little. Try it yourself. Build your understanding of Classic Maya glyphs.") }
                item { LearningCard("Your learning journey", "${completed.size} of ${LearningContent.lessons.size} lessons completed · Best quiz: $best/${LearningContent.questions.size}\nProgress is saved on this device for your account.") }
                item { Text("START WITH THE FOUNDATIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(LearningContent.lessons) { entry ->
                    Card(onClick = { destination = entry.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (entry.id in completed) "COMPLETED · REVIEW" else "SHORT LESSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(entry.title, style = MaterialTheme.typography.titleLarge)
                            Text(entry.introduction)
                            Text("Open lesson →", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item { Button(onClick = { destination = "quiz" }, modifier = Modifier.fillMaxWidth()) { Text("Practise with a quiz") } }
                item { OutlinedButton(onClick = { destination = "numbers-play" }, modifier = Modifier.fillMaxWidth()) { Text("Open numbers playground") } }
                item { ContentSources() }
            }
        }
    }
}

@Composable
private fun LearnHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LearningCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NumberDrawing(digit: Int) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics {
        contentDescription = if (digit == 0) "Zero, simplified shell placeholder" else "${digit % 5} dots and ${digit / 5} bars: digit $digit"
    }) {
        val cx = size.width / 2
        val unit = 12.dp.toPx()
        if (digit == 0) {
            drawOval(ink, Offset(cx - unit * 2, unit * 2), Size(unit * 4, unit * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        } else {
            repeat(digit % 5) { i -> drawCircle(ink, unit / 3, Offset(cx + (i - (digit % 5 - 1) / 2f) * unit * 1.5f, unit)) }
            repeat(digit / 5) { i -> drawRoundRect(ink, Offset(cx - unit * 3, unit * (2 + i * 1.3f)), Size(unit * 6, unit / 2), androidx.compose.ui.geometry.CornerRadius(unit / 4)) }
        }
    }
}

@Composable
private fun ContentSources() {
    Text("Further reading: Harri Kettunen & Christophe Helmke, Introduction to Maya Hieroglyphs (Wayeb); Michael D. Coe & Mark Van Stone, Reading the Maya Glyphs (Thames & Hudson). These introductory lessons summarise general conventions; individual readings require specialist evidence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
