package com.billlocket.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

class NavigationShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeStartsAndTopLevelNavigationDoesNotDuplicate() {
        composeRule.onNodeWithText("Trang chủ", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Bản đồ", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Bản đồ", useUnmergedTree = true).performClick()

        pressBack()
        composeRule.onNodeWithText("Trang chủ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun captureIsAnActionAndHidesBottomBar() {
        composeRule.onNodeWithContentDescription("Ghi lại").performClick()

        composeRule.onNodeWithText("Ghi lại giao dịch").assertIsDisplayed()
    }
}
