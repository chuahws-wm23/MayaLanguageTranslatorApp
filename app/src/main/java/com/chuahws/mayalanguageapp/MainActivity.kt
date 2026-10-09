package com.chuahws.mayalanguageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import com.chuahws.mayalanguageapp.presentation.translation.TranslationScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase is intentionally not required for the very first build.
        // Replace this with FirestoreDictionaryRepository after Firebase setup
        // and after the validated Yucatec Maya dataset is imported.
        val repository = InMemoryDictionaryRepository(emptyList())
        val viewModel = TranslationViewModel(
            translationService = TranslationService(repository)
        )

        setContent {
            MaterialTheme {
                Surface {
                    TranslationScreen(viewModel = viewModel)
                }
            }
        }
    }
}
