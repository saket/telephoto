# Zoomable Image

A _drop-in_ replacement for async `Image()` composables with support for pan & zoom gestures. For images that are large enough to not fit in memory, sub&#8209;sampling is automatically enabled so that they're displayed without any loss of detail when fully zoomed in.

=== "Coil"
    ```groovy
    implementation("me.saket.telephoto:zoomable-image-coil:1.0.0-SNAPSHOT")
    ```
=== "Glide"
    ```groovy
    implementation("me.saket.telephoto:zoomable-image-glide:1.0.0-SNAPSHOT")
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

### Image options

For complex scenarios, the `model` parameter can take full image requests. 

=== "Coil"
    ```kotlin
    val imageLoader = LocalContext.current.imageLoader
      .newBuilder()
      .components { add(GifDecoder.Factory()) }
      .build()

    ZoomableAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .listener(
          onStart = { … },
          onSuccess = { … },
        )
        .crossfade(1_000)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .allowHardware(true)
        .build(),
      imageLoader = imageLoader,
      contentDescription = …
    )
    ```

=== "Glide"
    ```kotlin
    ZoomableGlideImage(
      model = Glide
        .with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .addListener(object : RequestListener<Drawable> {
          override fun onLoadFailed(…): Boolean = TODO()
          override fun onResourceReady(…): Boolean = TODO()
        }),
        .transition(withCrossFade(1_000))
        .skipMemoryCache(true)
        .timeout(30_000)
        .addListener(…),
      contentDescription = …
    )
    ```

### Placeholders

For images that are available in multiple resolutions, `telephoto` highly recommends using their lower resolutions as placeholders while their full quality equivalents are loaded in the background. 

When combined with a cross-fade transition, `ZoomableImage` will smoothly swap out placeholders when their full quality versions are ready to be displayed.

=== "Coil"
    ```kotlin hl_lines="5-6"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .placeholderMemoryCacheKey(…)
        .crossfade(1000)
        .build(),
      contentDescription = …
    )
    ```
    More details about `placeholderMemoryCacheKey()` can be found on [Coil's website](https://coil-kt.github.io/coil/recipes/#using-a-memory-cache-key-as-a-placeholder).

=== "Glide"
    ```kotlin hl_lines="6-7"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = Glide
        .with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .thumbnail(…)   // or placeholder()
        .transition(withCrossFade(1000)),
      contentDescription = …
    )
    ```
    More details about `thumbnail()` can be found on [Glide's website](https://bumptech.github.io/glide/doc/options.html#thumbnail-requests).

### Click listeners
For detecting double taps, `Modifier.zoomable()` consumes all tap gestures making it incompatible with `Modifier.clickable()` and `Modifier.combinedClickable()`. As an alternative, its `onClick` and `onLongClick` parameters can be used.

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
