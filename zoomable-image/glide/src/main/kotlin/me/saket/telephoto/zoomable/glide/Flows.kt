package me.saket.telephoto.zoomable.glide

import android.graphics.drawable.Drawable
import androidx.annotation.GuardedBy
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.glide.Status.RUNNING

// TODO: delete this file when https://github.com/bumptech/glide/issues/5122 is resolved.

/**
 * The current status of a flow
 *
 * There is no well established graph that defines the valid Status transitions. Depending on
 * various factors like the parameters of the request, whether or not the resource is in the memory
 * cache, or even various calls to Glide's APIs, these may be emitted in different orders. As an
 * example, [RUNNING] is skipped if a request can be immediately completed from the memory cache.
 *
 * See [flow] for more details.
 */
internal enum class Status {
  /** The load is not started or has been cleared. */
  CLEARED,

  /** At least the primary load is still in progress. */
  RUNNING,

  /**
   * The primary load or the error load ([RequestBuilder.error]) associated with the primary have
   * finished successfully.
   */
  SUCCEEDED,

  /** The primary load has failed. One or more thumbnails may have succeeded. */
  FAILED,
}

/**
 * Identical to [flow] with dimensions, except that the size is resolved asynchronously using
 * [waitForSize].
 *
 * If an override size has been set using [RequestBuilder.override], that size will be used instead
 * and [waitForSize] may never be called.
 *
 * [Placeholder] values may be emitted prior to [waitForSize] returning. Similarly if
 * [RequestBuilder.thumbnail] requests are present and have overridden sizes, [Resource] values for
 * those thumbnails may also be emitted. [waitForSize] will only be used for requests where no
 * [RequestBuilder.override] size is available.
 *
 * If [waitForSize] does not return, this flow may never return values other than placeholders.
 *
 * This function is internal only, intended primarily for Compose. The Target API provides similar
 * functionality for traditional Views. We could consider expanding the visibility if there are use
 * cases for asynchronous size resolution outside of Glide's Compose integration.
 */
internal fun <ResourceT : Any> RequestBuilder<ResourceT>.flow(
  waitForSize: suspend () -> Size,
  requestManager: RequestManager,
): Flow<GlideFlowInstant<ResourceT>> {
  val requestBuilder = this
  return callbackFlow {
    val target = FlowTarget(this, AsyncGlideSize(waitForSize))
    requestBuilder
      .addListener(target)
      .into(target)
    awaitClose { requestManager.clear(target) }
  }
}

/**
 * A [Status] and value pair, where the value is either a [Placeholder] or a [Resource] depending on
 * how far the Glide load has progressed and/or how successful it's been.
 */
internal sealed class GlideFlowInstant<ResourceT> {
  abstract val status: Status
}

/**
 * Wraps a [Status] and a placeholder [Drawable] (from [RequestBuilder.placeholder],
 * [RequestBuilder.fallback], [RequestBuilder.error] etc).
 */
internal data class Placeholder<ResourceT>(
  public override val status: Status,
  public val placeholder: Drawable?,
) : GlideFlowInstant<ResourceT>()

/**
 * Wraps a [Status] and a resource loaded from the primary request, a [RequestBuilder.thumbnail]
 * request, or a [RequestBuilder.error] request.
 *
 * **Status.FAILED** is a perfectly valid status with this class. If the primary request fails, but
 * at least one thumbnail succeeds, the flow will emit `Resource(FAILED, resource)` to indicate both
 * that we have some value but also that the primary request has failed.
 */
internal data class Resource<ResourceT>(
  public override val status: Status,
  public val resource: ResourceT,
  public val transition: Transition<in ResourceT>?,
) : GlideFlowInstant<ResourceT>()

/**
 * Observes a glide request using [Target] and [RequestListener] and tries to emit something
 * resembling a coherent set of placeholders and resources for it.
 *
 * Threading in this class is a bit complicated. As a general rule, the callback methods are ordered
 * by callers. So we have to handle being called from multiple threads, but we don't need to try to
 * handle callbacks being called in parallel.
 *
 * The primary area of concern around thread is that [resolvedSize] and [sizeReadyCallbacks] must be
 * updated atomically, but can be modified on different threads.
 *
 * [currentRequest] would normally be a concern because [Target]s can be cancelled on threads other
 * than where they were started. However in our case, [currentRequest] is set once when our request
 * is started (by us) and is only cancelled when the request finishes. So we just have to avoid NPEs
 * and make sure the state is reasonably up to date.
 *
 * [lastResource] is an unfortunate hack that tries to make sure that we emit [Status.FAILED] if a
 * thumbnail request succeeds, but then the primary request fails. In that case, we'd normally
 * already have emitted [Resource] with [Status.RUNNING] and the thumbnail value and then we'd emit
 * nothing else. That's not very satisfying for callers who expect some resolution. So instead we
 * track the last resource produced by thumbnails and emit that along with [Status.FAILED] when we
 * see that the primary request has failed. As a result we're not concerned with ordering with
 * regards to [lastResource], but it is possible the callbacks will be called on different threads,
 * so the value may be updated from different threads even if it's not concurrent.
 */
private class FlowTarget<ResourceT : Any>(
  private val scope: ProducerScope<GlideFlowInstant<ResourceT>>,
  private val size: ResolvableGlideSize,
) : Target<ResourceT>, RequestListener<ResourceT> {
  @Volatile private var resolvedSize: Size? = null
  @Volatile private var currentRequest: Request? = null
  @Volatile private var lastResource: ResourceT? = null

  @GuardedBy("this") private val sizeReadyCallbacks = mutableListOf<SizeReadyCallback>()

  init {
    when (size) {
      // Otherwise, we do not want to block the flow while waiting on a size because one or more
      // requests in the chain may have a fixed size, even if the primary request does not.
      // Starting the Glide request right away allows any subrequest that has a fixed size to
      // begin immediately, shaving off some small amount of time.
      is AsyncGlideSize ->
        scope.launch {
          val localResolvedSize = size.asyncSize()
          val callbacksToNotify: List<SizeReadyCallback>
          synchronized(this) {
            resolvedSize = localResolvedSize
            callbacksToNotify = ArrayList(sizeReadyCallbacks)
            sizeReadyCallbacks.clear()
          }
          callbacksToNotify.forEach {
            it.onSizeReady(localResolvedSize.width, localResolvedSize.height)
          }
        }
    }
  }

  override fun onStart() {}
  override fun onStop() {}
  override fun onDestroy() {}

  override fun onLoadStarted(placeholder: Drawable?) {
    lastResource = null
    scope.trySend(Placeholder(Status.RUNNING, placeholder))
  }

  override fun onLoadFailed(errorDrawable: Drawable?) {
    scope.trySend(Placeholder(Status.FAILED, errorDrawable))
  }

  override fun onResourceReady(resource: ResourceT, transition: Transition<in ResourceT>?) {
    lastResource = resource
    scope.trySend(
      Resource(
        // currentRequest is the entire request state, so we can use it to figure out if this
        // resource is from a thumbnail request (isComplete is false) or the primary request.
        if (currentRequest?.isComplete == true) Status.SUCCEEDED else Status.RUNNING,
        resource,
        transition,
      )
    )
  }

  override fun onLoadCleared(placeholder: Drawable?) {
    lastResource = null
    scope.trySend(Placeholder(Status.CLEARED, placeholder))
  }

  override fun getSize(cb: SizeReadyCallback) {
    val localResolvedSize = resolvedSize
    if (localResolvedSize != null) {
      cb.onSizeReady(localResolvedSize.width, localResolvedSize.height)
      return
    }

    synchronized(this@FlowTarget) {
      val lockedResolvedSize = resolvedSize
      if (lockedResolvedSize != null) {
        cb.onSizeReady(lockedResolvedSize.width, lockedResolvedSize.height)
      } else {
        sizeReadyCallbacks.add(cb)
      }
    }
  }

  override fun removeCallback(cb: SizeReadyCallback) {
    synchronized(this) { sizeReadyCallbacks.remove(cb) }
  }

  override fun setRequest(request: Request?) {
    currentRequest = request
  }

  override fun getRequest(): Request? {
    return currentRequest
  }

  override fun onLoadFailed(
    e: GlideException?,
    model: Any?,
    target: Target<ResourceT>?,
    isFirstResource: Boolean,
  ): Boolean {
    val localLastResource = lastResource
    val localRequest = currentRequest
    if (localLastResource != null && localRequest?.isComplete == false && !localRequest.isRunning) {
      scope.channel.trySend(Resource(Status.FAILED, localLastResource, transition = null))
    }
    return false
  }

  override fun onResourceReady(
    resource: ResourceT,
    model: Any?,
    target: Target<ResourceT>?,
    dataSource: DataSource?,
    isFirstResource: Boolean,
  ): Boolean {
    return false
  }
}

internal data class Size(val width: Int, val height: Int)

private sealed class ResolvableGlideSize

private data class AsyncGlideSize(val asyncSize: suspend () -> Size) : ResolvableGlideSize()
