# Recipes

### Modifier.zoomable()
- [Observing pan & zoom](../zoomable/recipes.md#observing-pan-zoom)
- [Resetting zoom](../zoomable/recipes.md#resetting-zoom)

### Grabbing downloaded images

**Low resolution** drawables can be accessed by using request listeners. These images are down-sampled by your image loading library to fit in memory and are suitable for simple use-cases such as [color extraction](https://developer.android.com/training/material/palette-colors).

=== "Coil"
    ```kotlin
    ZoomableAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .listener(onSuccess = { _, result ->
          // TODO: do something with result.drawable.
        })
        .build(),
      contentDescription = …
    )
    ```
=== "Glide"
    ```kotlin
    ZoomableGlideImage(
      model = "https://example.com/image.jpg",
      contentDescription = …
    ) {
      it.addListener(object : RequestListener<Drawable> {
        override fun onResourceReady(resource: Drawable, …): Boolean {
          // TODO: do something with resource.
        }
      })
    }
    ```

**Full resolutions** must be obtained as files because `ZoomableImage` streams them directly from disk. The easiest way to do this is to load them again from cache.

=== "Coil"
    ```kotlin
    val state = rememberZoomableImageState()
    ZoomableAsyncImage(
      model = imageUrl,
      state = state,
      contentDescription = …,
    )

    if (state.isImageDisplayed) {
      Button(onClick = { downloadImage(context, imageUrl) }) {
        Text("Download image")
      }
    }
    ```
    ```kotlin
    suspend fun downloadImage(context: Context, imageUrl: HttpUrl) {
      val result = context.imageLoader.execute(
        ImageRequest.Builder(context)
          .data(imageUrl)
          .build()
      )
      if (result is SuccessResult) {
        val cacheKey = result.diskCacheKey ?: error("image wasn't saved to disk")
        val diskCache = context.imageLoader.diskCache!!
        diskCache.openSnapshot(cacheKey)!!.use { 
          // TODO: copy to Downloads directory.           
        }
      }
    }
    ```
=== "Glide"
    ```kotlin
    val state = rememberZoomableImageState()
    ZoomableGlideImage(
      model = imageUrl,
      state = state,
      contentDescription = …,
    )

    if (state.isImageDisplayed) {
      Button(onClick = { downloadImage(context, imageUrl) }) {
        Text("Download image")
      }
    }
    ```
    ```kotlin
    fun downloadImage(context: Context, imageUrl: Uri) {
      Glide.with(context)
        .download(imageUrl)
        .into(object : CustomTarget<File>() {
          override fun onResourceReady(resource: File, …) {
            // TODO: copy file to Downloads directory.
          }
          
          override fun onLoadCleared(placeholder: Drawable?) = Unit
        )
    }
    ```
