package me.saket.telephoto.util

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import kotlin.time.Duration

fun AndroidComposeTestRule<*, *>.waitUntil(timeout: Duration, condition: () -> Boolean) {
  this.waitUntil(timeoutMillis = timeout.inWholeMilliseconds, condition)
}
