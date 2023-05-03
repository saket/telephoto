# Zoomable Image

![type:video](../assets/demo_small.mp4)

A _drop-in_ replacement for async `Image()` composables featuring support for pan & zoom gestures and automatic sub-sampling of large images. This ensures that images maintain their intricate details even when fully zoomed in, without causing any `OutOfMemory` exceptions.

=== "Coil"
    ```groovy
    implementation("me.saket.telephoto:zoomable-image-coil:{{ versions.telephoto }}")
    ```
=== "Glide"
    ```groovy
    implementation("me.saket.telephoto:zoomable-image-glide:{{ versions.telephoto }}")
    ```
<!-- Invisible separator for tabbed code blocks -->
=== "Coil"
    ```diff
    - AsyncImage(
    + ZoomableAsyncImage(
        model = "https://example.com/image.jpg",
        contentDescription = …
      )
    ```
=== "Glide"
    ```diff
    - GlideImage(
    + ZoomableGlideImage(
        model = "https://example.com/image.jpg",
        contentDescription = …
      )
    ```

### Image requests

For complex scenarios, the `model` parameter can take full image requests. 

=== "Coil"
    ```kotlin
    ZoomableAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .listener(
          onSuccess = { … },
          onError = { … }
        )
        .crossfade(1_000)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build(),
      imageLoader = LocalContext.current.imageLoader, // Optional.
      contentDescription = …
    )
    ```

=== "Glide"
    ```kotlin
    ZoomableGlideImage(
      model = Glide.with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .addListener(object : RequestListener<Drawable> {
          override fun onResourceReady(…): Boolean = TODO()
          override fun onLoadFailed(…): Boolean = TODO()
        })
        .transition(withCrossFade(1_000))
        .skipMemoryCache(true)
        .disallowHardwareConfig()
        .timeout(30_000),
      contentDescription = …
    )
    ```

### Placeholders

![type:video](../assets/placeholders_small.mp4)

If your images are available in multiple resolutions, `telephoto` highly recommends using their lower resolutions as placeholders while their full quality equivalents are loaded in the background.

When combined with a cross-fade transition, `ZoomableImage` will smoothly swap out placeholders when their full quality versions are ready to be displayed.

=== "Coil"
    ```kotlin hl_lines="5-6"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .placeholderMemoryCacheKey(…)
        .crossfade(1_000)
        .build(),
      contentDescription = …
    )
    ```
    More details about `placeholderMemoryCacheKey()` can be found on [Coil's website](https://coil-kt.github.io/coil/recipes/#using-a-memory-cache-key-as-a-placeholder).

=== "Glide"
    ```kotlin hl_lines="5-6"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = Glide.with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .thumbnail(…)   // or placeholder()
        .transition(withCrossFade(1_000)),
      contentDescription = …
    )
    ```
    More details about `thumbnail()` can be found on [Glide's website](https://bumptech.github.io/glide/doc/options.html#thumbnail-requests).

### Content alignment

When images are zoomed, they're scaled with respect to their `alignment` until they're large enough to fill all available space. After that, they're scaled uniformly. The default `alignment` is `Alignment.Center`.

|              `Alignment.TopCenter`               |              `Alignment.BottomCenter`               |
|:------------------------------------------------:|:---------------------------------------------------:|
| ![type:video](../assets/alignment_top_small.mp4) | ![type:video](../assets/alignment_bottom_small.mp4) |

=== "Coil"
    ```kotlin hl_lines="4"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      alignment = Alignment.TopCenter
    )
    ```
=== "Glide"
    ```kotlin hl_lines="4"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      alignment = Alignment.TopCenter
    )
    ```

### Content scale

Unlike `Image()`, cropped images can be panned in `ZoomableImage`.

Images are scaled using `ContentScale.Fit` by default. This is good enough for media viewers, but can be changed to any other `ContentScale`. For example, a wallpaper app may want to use `ContentScale.Crop` to stretch images to fill all available space. 

When images are cropped, you'll still be able to move them around.

=== "Coil"
    ```kotlin hl_lines="4"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      contentScale = ContentScale.Crop
    )
    ```
=== "Glide"
    ```kotlin hl_lines="4"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      contentScale = ContentScale.Crop
    )
    ```

### Click listeners
For detecting double taps, `ZoomableImage` consumes all tap gestures making it incompatible with `Modifier.clickable()` and `Modifier.combinedClickable()`. As an alternative, its `onClick` and `onLongClick` parameters can be used.

=== "Coil"
    ```kotlin
    ZoomableAsyncImage(
      modifier = Modifier.clickable { error("This will not work") },
      model = "https://example.com/image.jpg",
      onClick = { … },
      onLongClick = { … },
    )
    ```
=== "Glide"
    ```kotlin
    ZoomableGlideImage(
      modifier = Modifier.clickable { error("This will not work") },
      model = "https://example.com/image.jpg",
      onClick = { … },
      onLongClick = { … },
    )
    ```


### Sharing hoisted state

For handling zoom gestures, `Zoomablemage` uses [`Modifier.zoomable()`](../zoomable/index.md) underneath. If your app displays different kinds of media, it is recommended to hoist the `ZoomableState` outside so that it can be shared with all zoomable composables:

=== "Coil"
    ```kotlin
    val zoomableState = rememberZoomableState()

    when (media) {
     is Image -> {
        ZoomableAsyncImage(
         model = media.imageUrl,
         state = rememberZoomableImageState(zoomableState),
        )
      }
      is Video -> {
        ZoomableVideoPlayer(
          model = media.videoUrl,
          state = rememberZoomableExoState(zoomableState),
        )
      }
    }
    ```
=== "Glide"
    ```kotlin
    val zoomableState = rememberZoomableState()

    when (media) {
     is Image -> {
        ZoomableGlideImage(
         model = media.imageUrl,
         state = rememberZoomableImageState(zoomableState),
        )
      }
      is Video -> {
        ZoomableVideoPlayer(
          model = media.videoUrl,
          state = rememberZoomableExoState(zoomableState),
        )
      }
    }
    ```
