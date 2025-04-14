package me.saket.telephoto.zoomable.coil

import coil.ImageLoader
import java.lang.reflect.Field
import kotlin.LazyThreadSafetyMode.NONE

internal object ImageLoader {
  private var canUseReflection = true

  private val optionsField: Field by lazy(NONE) {
    Class.forName("coil.RealImageLoader")
      .getDeclaredField("options")
      .also { it.isAccessible = true }
  }

  private val respectHeadersField: Field by lazy(NONE) {
    Class.forName("coil.util.ImageLoaderOptions")
      .getDeclaredField("respectCacheHeaders")
      .also { it.isAccessible = true }
  }

  internal fun ImageLoader.isRespectingCacheHeaders(): Boolean? {
    if (!canUseReflection) {
      return null
    }
    return try {
      val options = optionsField.get(this)
      respectHeadersField.get(options) as Boolean
    } catch (e: Throwable) {
      canUseReflection = false
      null
    }
  }
}
