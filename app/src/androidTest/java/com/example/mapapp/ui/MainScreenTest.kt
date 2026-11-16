package com.example.mapapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_showsTitle() {
        composeTestRule.setContent {
            MainScreen()
        }

        composeTestRule.onNodeWithText("Visual-GNSS Navigation").assertIsDisplayed()
    }

    @Test
    fun mainScreen_showsStartTrackingButton() {
        composeTestRule.setContent {
            MainScreen()
        }

        composeTestRule.onNodeWithText("Start tracking").assertIsDisplayed()
    }
    
    @Test
    fun mainScreen_showsDrowsinessAlertsSwitch() {
        composeTestRule.setContent {
            MainScreen()
        }

        composeTestRule.onNodeWithText("Drowsiness alerts").assertIsDisplayed()
    }

    @Test
    fun mainScreen_searchPlaceholderVisible() {
        composeTestRule.setContent {
            MainScreen()
        }

        composeTestRule.onNodeWithText("Search destination…").assertIsDisplayed()
    }
}
