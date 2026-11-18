package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Activity
import android.content.Intent
import androidx.activity.result.*
import androidx.activity.result.contract.*
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.chuahws.mayalanguageapp.data.repository.*
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.app.AppDataViewModel
import com.chuahws.mayalanguageapp.presentation.navigation.MayaLanguageApp
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Integrated user interactions, local test account and a simulated system-picker reply.
 * This is not a physical-phone, production-Firebase or field-photo accuracy test.
 */
@RunWith(AndroidJUnit4::class)
class UserJourneyTest {
    @get:Rule val compose = createComposeRule()
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    private fun shot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = requireNotNull(context.getExternalFilesDir("screenshots"))
        folder.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder,"journey-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun registrationLoginScanFavouriteHistoryDictionaryProfileFeedbackLogoutAndRelogin() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val authRepo=DevelopmentAuthRepository()
        val profiles=InMemoryUserProfileRepository()
        val data=InMemoryUserDataRepository()
        val dictionary=InMemoryDictionaryRepository(emptyList())
        val auth=AuthViewModel(authRepo,profiles)
        val app=AppDataViewModel(data,profiles,dictionary)
        val translator=TranslationViewModel(TranslationService(dictionary),data,
            { auth.uiState.value.currentUser?.userId }, { app.refresh() })
        val models=ViewModelStore().apply { put("auth",auth);put("data",app);put("translator",translator) }
        val catalogue=requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context))
        val reference=catalogue.references.first { it.twkmSign==32 }
        val folder=File(context.cacheDir,"images").apply { mkdirs() }
        val file=File(folder,"user-journey-reference.png")
        val image=catalogue.imageFor(reference)
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        var pickerCalls=0
        val owner=object:ActivityResultRegistryOwner {
            override val activityResultRegistry=object:ActivityResultRegistry() {
                override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
                    check(contract is ActivityResultContracts.GetContent)
                    pickerCalls++
                    dispatchResult(requestCode,Activity.RESULT_OK,Intent().setData(uri))
                }
            }
        }
        val email="journey@example.test"
        try {
            compose.setContent { MayaTranslateTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MayaLanguageApp(auth,app,translator,firebaseConfigured=false)
                }
            } }
            button("Sign in").performScrollTo().performClick()
            compose.onNodeWithText("Enter a valid email and password.").assertExists()
            button("Forgot password?").performScrollTo().performClick()
            field("Email address").performTextReplacement(email)
            button("Send reset link").performScrollTo().performClick()
            compose.waitUntil(10_000) { auth.uiState.value.message?.contains("unavailable in development mode")==true }
            assertFalse(auth.uiState.value.passwordResetSent)
            button("Back to sign in").performScrollTo().performClick()
            button("New here? Create an account").performScrollTo().performClick()
            field("Display name").performTextReplacement("Journey explorer")
            field("Email address").performTextReplacement(email)
            field("Password").performTextReplacement("GlyphTest123!")
            field("Confirm password").performTextReplacement("GlyphTest123!")
            button("Create account").performScrollTo().performClick()
            compose.waitUntil(10_000) { auth.uiState.value.registrationCompleted }
            assertNull(auth.uiState.value.currentUser)
            field("Password").performTextReplacement("GlyphTest123!")
            button("Sign in").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.uiState.value.profile?.displayName=="Journey explorer" && !app.uiState.value.loading }
            compose.onNodeWithText("Welcome back").assertExists()
            shot("home")
            button("Scan").performClick()
            button("Gallery").performScrollTo().performClick()
            compose.onNodeWithText("Single glyph").performScrollTo().performClick()
            compose.waitUntil(60_000) { app.uiState.value.history.size==1 }
            assertEquals(1,pickerCalls)
            compose.onNodeWithText("Reference drawing matched").performScrollTo().assertExists()
            shot("scan-result")
            // A short-lived save notification can cover a button near the bottom edge.
            // Wait until the visible notification is gone before tapping through it.
            compose.waitUntil(15_000) { app.uiState.value.message==null && auth.uiState.value.message==null }
            button("Add to favourites").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.uiState.value.savedVocabulary.size==1 }
            compose.waitUntil(15_000) { app.uiState.value.message==null }
            button("View favourites").performScrollTo().performClick()
            compose.onNodeWithText("Glyph · ${reference.reading}").assertExists()
            button("History").performClick()
            compose.onNodeWithText(reference.english).assertExists()
            shot("history")
            button("Dictionary").performClick()
            field("Search meaning, reading or number").performTextReplacement("200")
            compose.onNodeWithText("PIK").assertExists()
            shot("dictionary")
            button("Profile").performClick()
            button("Edit profile").performScrollTo().performClick()
            field("Display name").performTextReplacement("Updated explorer")
            button("Save changes").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.uiState.value.profile?.displayName=="Updated explorer" }
            shot("profile")
            compose.onNodeWithContentDescription("Open menu").performClick()
            button("My activity").performScrollTo().performClick()
            compose.onNodeWithText("Your activity").assertExists()
            compose.onNodeWithText("Saved items").assertExists()
            compose.onNodeWithText("Most used").assertExists()
            assertEquals(1L,app.uiState.value.analytics.totalTranslations)
            assertEquals(1L,app.uiState.value.analytics.savedWords)
            shot("activity")
            compose.onNodeWithContentDescription("Open menu").performClick()
            button("Feedback").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Rate 4 out of 5 stars").performScrollTo().performClick()
            button("Send feedback").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.uiState.value.feedbackSubmissionVersion==1L && !app.uiState.value.feedbackSubmitting }
            button("Profile").performClick()
            compose.waitUntil(15_000) { app.uiState.value.message==null }
            button("Sign out").performScrollTo().performClick()
            compose.waitUntil(10_000) { auth.uiState.value.currentUser==null && app.uiState.value.userId==null }
            field("Email address").performTextReplacement(email)
            field("Password").performTextReplacement("GlyphTest123!")
            button("Sign in").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.uiState.value.profile?.displayName=="Updated explorer" && !app.uiState.value.loading }
            assertEquals(1,app.uiState.value.history.size)
            assertEquals(1,app.uiState.value.savedVocabulary.size)
            assertEquals(1L,app.uiState.value.analytics.totalTranslations)
        } catch (error: Throwable) {
            runCatching { shot("failure") }
            compose.onRoot().printToLog("UserJourneyFailure")
            println("Journey state: history=${app.uiState.value.history.size}, favourites=${app.uiState.value.savedVocabulary.size}, loading=${app.uiState.value.loading}, message=${app.uiState.value.message}")
            throw error
        } finally {
            compose.runOnIdle { app.clearUser();auth.signOut();models.clear() }
            file.delete()
        }
    }
}
