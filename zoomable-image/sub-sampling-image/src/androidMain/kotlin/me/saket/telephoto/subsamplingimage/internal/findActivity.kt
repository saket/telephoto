package me.saket.telephoto.subsamplingimage.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

// todo: delete this when LocalActivity is available in androidx.activity.
internal tailrec fun Context.findActivity(): Activity {
  return when (this) {
    is Activity -> this
    is ContextWrapper -> this.baseContext.findActivity()
    else -> throw IllegalArgumentException("Could not find activity!")
  }
}
