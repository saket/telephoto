package me.saket.telephoto.zoomable

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.flow.Flow
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import kotlin.time.Duration

/**
 * An image that can be displayed using [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Keep in mind that this shouldn't be used directly. It is designed to provide an
 * abstraction over your favorite image loading library.
 *
 * TODO: mention glide and picasso.
 *
 * If you're using Coil for loading images, Telephoto provides a default implementation
 * through [ZoomableAsyncImage()][me.saket.telephoto.zoomable.coil.ZoomableAsyncImage]
 * (`me.saket.telephoto:zoomable-image-coil`).
 *
 * ```kotlin
 * ZoomableAsyncImage(
 *  model = "https://example.com/image.jpg",
 *  contentDescription = …
 *)
 * ```
 */
interface ZoomableImageSource {
  companion object; // For extensions.

  // todo: doc.
  @Composable
  fun resolve(canvasSize: Flow<Size>): ResolveResult

  sealed interface ResolveResult {
    val placeholder: Painter?
    val crossfadeDuration: Duration
  }

  /** Images that aren't bitmaps (for e.g., GIFs) and should be rendered without sub-sampling. */
  // todo: doc
  data class Generic(
    val image: Painter?,
    override val placeholder: Painter? = null,
    override val crossfadeDuration: Duration = Duration.ZERO,
  ) : ResolveResult

  // todo: doc
  data class RequiresSubSampling(
    val source: SubSamplingImageSource,
    override val placeholder: Painter?,
    override val crossfadeDuration: Duration = Duration.ZERO,
    val imageOptions: ImageBitmapOptions = ImageBitmapOptions.Default,
    val expectedSize: Size,
  ) : ResolveResult
}
