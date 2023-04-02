package me.saket.telephoto.zoomable.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import app.cash.molecule.RecompositionClock.Immediate
import app.cash.molecule.launchMolecule
import app.cash.paparazzi.Paparazzi
import app.cash.turbine.test
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.test.FakeImageLoaderEngine
import com.google.accompanist.drawablepainter.DrawablePainter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.GenericImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.RequiresSubSampling
import okio.fakefilesystem.FakeFileSystem
import org.junit.Rule
import org.junit.Test
import coil.size.Size.Companion as CoilSize

@OptIn(ExperimentalCoilApi::class)
class CoilImageResolverTest {
  // Only used to create a fake context. I could use robolectric but it's
  // not super reliable and I'm already using paparazzi in other tests.
  @get:Rule val paparazzi = Paparazzi()
  private val context: Context get() = paparazzi.context

  @Test fun `verify image request specs`() = runTest {
    val requests = Channel<ImageRequest>(capacity = Channel.CONFLATED)

    Coil.setImageLoader(buildImageLoader {
      components {
        add(buildFakeImageEngine {
          addInterceptor {
            requests.send(it.request)
            null
          }
        })
      }
    })

    val images = backgroundScope.launchMolecule(clock = Immediate) {
      ZoomableImage.coil(
        ImageRequest.Builder(context)
          .data("foo")
          .build()
      ).resolve()
    }
    images.test {
      skipItems(1)
      cancelAndIgnoreRemainingEvents()
    }

    val request = requests.receive()
    assertThat(request.diskCachePolicy.writeEnabled).isTrue()
    assertThat(request.sizeResolver.size()).isEqualTo(CoilSize.ORIGINAL)
  }

  @Test fun `start with the placeholder image`() = runTest {
    Coil.setImageLoader(buildImageLoader {
      components {
        add(buildFakeImageEngine {
          intercept("fake_image", BitmapDrawable(context.resources, fakeBitmap()))
        })
      }
    })

    val placeholderKey = MemoryCache.Key("placeholder_image")
    context.imageLoader.memoryCache!![placeholderKey] = MemoryCache.Value(fakeBitmap())

    backgroundScope.launchMolecule(clock = Immediate) {
      ZoomableImage.coil(
        buildImageRequest {
          data("fake_image").placeholderMemoryCacheKey(placeholderKey)
        }
      ).resolve()
    }.test {
      // Default value.
      assertThat(awaitItem()).isEqualTo(GenericImage(EmptyPainter))

      with(awaitItem()) {
        check(this is GenericImage)
        (painter as DrawablePainter).let { painter ->
          (painter.drawable as BitmapDrawable).let { drawable ->
            assertThat(drawable.bitmap.width).isEqualTo(fakeBitmap().width)
            assertThat(drawable.bitmap.height).isEqualTo(fakeBitmap().height)
          }
        }
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `bitmaps should be sub-sampled`() = runTest {
    val diskCacheFileSystem = FakeFileSystem()
    val diskCache = DiskCache.Builder()
      .fileSystem(diskCacheFileSystem)
      .directory(diskCacheFileSystem.workingDirectory)
      .build()

    val imageDiskCacheKey = "bitmap_image_disk_cache_key"
    diskCache.edit(imageDiskCacheKey)!!
      .apply { diskCache.fileSystem.write(data) { writeUtf8("foo") } }
      .commit()

    Coil.setImageLoader(buildImageLoader {
      diskCache(diskCache).components {
        add(buildFakeImageEngine {
          addInterceptor {
            SuccessResult(
              drawable = BitmapDrawable(context.resources, fakeBitmap()),
              request = it.request,
              dataSource = DataSource.DISK,
              diskCacheKey = imageDiskCacheKey,
            )
          }
        })
      }
    })

    val images = backgroundScope.launchMolecule(clock = Immediate) {
      ZoomableImage.coil(
        ImageRequest.Builder(context)
          .data("ignored")
          .build()
      ).resolve()
    }

    images.test {
      skipItems(1)
      assertThat(awaitItem()).isEqualTo(
        RequiresSubSampling(
          ImageSource.file(context.imageLoader.diskCache!![imageDiskCacheKey]!!.data)
        )
      )
    }
  }

  private fun buildImageRequest(build: ImageRequest.Builder.() -> ImageRequest.Builder): ImageRequest =
    ImageRequest.Builder(context)
      .let(build)
      .build()

  private fun buildImageLoader(build: ImageLoader.Builder.() -> ImageLoader.Builder): ImageLoader =
    ImageLoader.Builder(context)
      .let(build)
      .build()

  private fun buildFakeImageEngine(
    build: FakeImageLoaderEngine.Builder.() -> FakeImageLoaderEngine.Builder
  ) = FakeImageLoaderEngine.Builder()
    .let(build)
    .build()

  private fun fakeBitmap(): Bitmap {
    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
  }
}
