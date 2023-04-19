package me.saket.telephoto.zoomable.internal

import androidx.annotation.RestrictTo
import androidx.compose.runtime.RememberObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class RememberWorker : RememberObserver {
  private lateinit var job: Job

  abstract suspend fun work()

  override fun onRemembered() {
    check(!::job.isInitialized) // Shouldn't be remembered in multiple locations.
    job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { work() }
  }

  override fun onAbandoned() = job.cancel()

  override fun onForgotten() = job.cancel()
}
