package com.example

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.HumanV1Theme
import com.example.ui.screens.WelcomeScreen
import com.example.ui.viewmodel.StrengthViewModel
import com.example.data.StrengthRepository
import com.example.data.StrengthDao
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow

@RunWith(RobolectricTestRunner::class)

class WelcomeScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setupContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val fakeDao = Proxy.newProxyInstance(
            StrengthDao::class.java.classLoader,
            arrayOf(StrengthDao::class.java)
        ) { _, method, _ ->
            val returnType = method.returnType
            if (returnType == Flow::class.java) {
                MutableStateFlow(null)
            } else if (returnType == List::class.java) {
                emptyList<Any>()
            } else if (returnType == Int::class.java) {
                0
            } else if (returnType == Long::class.java) {
                0L
            } else if (returnType == Boolean::class.java) {
                false
            } else if (returnType == String::class.java) {
                ""
            } else {
                null
            }
        } as StrengthDao

        val repo = StrengthRepository(fakeDao, context)
        val viewModel = StrengthViewModel(repo, context)
        
        composeTestRule.setContent {
            HumanV1Theme {
                WelcomeScreen(viewModel = viewModel, onNavigateToHome = {})
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port")
    fun captureWelcomeScreen_360x640() {
        setupContent()
        composeTestRule.onNode(androidx.compose.ui.test.isRoot()).captureRoboImage(
            filePath = "app/build/outputs/roborazzi/WelcomeScreen_360x640.png"
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port")
    fun captureWelcomeScreen_360x800() {
        setupContent()
        composeTestRule.onNode(androidx.compose.ui.test.isRoot()).captureRoboImage(
            filePath = "app/build/outputs/roborazzi/WelcomeScreen_360x800.png"
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    fun captureWelcomeScreen_412x915() {
        setupContent()
        composeTestRule.onNode(androidx.compose.ui.test.isRoot()).captureRoboImage(
            filePath = "app/build/outputs/roborazzi/WelcomeScreen_412x915.png"
        )
    }


}
