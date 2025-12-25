package me.saket.telephoto.util

import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp

fun TouchInjectionScope.quickZoomIn(byDistance: Float = height / 2f) {
  val doubleTapMinTimeMillis = 40L // From LocalViewConfiguration.current.doubleTapMinTimeMillis.
  val start = center
  val endY = start.y + byDistance

  click(start)
  advanceEventTime(eventPeriodMillis + doubleTapMinTimeMillis)
  swipeDown(startY = start.y, endY = endY, durationMillis = 1_000)
}

fun TouchInjectionScope.quickZoomOut(byDistance: Float = height / 2f) {
  val doubleTapMinTimeMillis = 40L // From LocalViewConfiguration.current.doubleTapMinTimeMillis.

  val start = bottomCenter
  val endY = start.y - byDistance

  click(start)
  advanceEventTime(doubleTapMinTimeMillis + 2)
  swipeUp(startY = start.y, endY = endY, durationMillis = 1_000)
}
