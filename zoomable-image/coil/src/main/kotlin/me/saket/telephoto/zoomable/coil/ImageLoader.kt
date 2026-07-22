@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NAME_SHADOWING")

package me.saket.telephoto.zoomable.coil

import coil.ImageLoader
import coil.RealImageLoader
import coil.request.ImageRequest
import coil.request.ImageResult
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Executes [request] without dispatching to `Dispatchers.Main.immediate`.
 *
 * [ImageLoader.execute] always dispatches to the main thread, which can deadlock when it is called
 * from `runBlocking` in screenshot tests. Coil 2 does not expose a way to override that dispatcher,
 * so invoke its request pipeline directly instead.
 */
internal suspend fun ImageLoader.executeMain(request: ImageRequest): ImageResult {
  val request = request.newBuilder()
    .interceptorDispatcher(Dispatchers.Unconfined)
    .build()
  return if (this is RealImageLoader) {
    suspendCoroutineUninterceptedOrReturn { continuation ->
      try {
        ExecuteMainMethod.invoke(this, request, /* type = */ 1, continuation)
      } catch (e: InvocationTargetException) {
        throw e.targetException
      }
    }
  } else {
    execute(request)
  }
}

private val ExecuteMainMethod: Method by lazy(LazyThreadSafetyMode.NONE) {
  try {
    RealImageLoader::class.java
      .getDeclaredMethod(
        "executeMain",
        ImageRequest::class.java,
        Int::class.javaPrimitiveType,
        Continuation::class.java,
      )
      .apply { isAccessible = true }
  } catch (e: ReflectiveOperationException) {
    throw IllegalStateException(
      "Telephoto could not find Coil 2's internal RealImageLoader.executeMain() function. " +
        "This is unexpected, and calling this function is the only way Telephoto can resolve Coil 2 " +
        "images synchronously in screenshot tests without deadlocking. Consider migrating to " +
        "Coil 3 for a more stable screenshot testing experience?",
      e,
    )
  }
}
