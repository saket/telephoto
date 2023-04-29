# Recipes

### Modifier.zoomable()
- [Observing pan & zoom](../zoomable/recipes.md#observing-pan-zoom)
- [Resetting zoom](../zoomable/recipes.md#resetting-zoom)

### Grabbing downloaded images
`ZoomableImage` does not offer any API for accessing full-sized images because bitmaps are streamed directly from disk. The safest way to obtain a copy of an image would be to reload it.

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
        val imageFile: Path = diskCache[cacheKey]!!.data
        
        // TODO: copy file to Downloads directory.
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
    suspend fun downloadImage(context: Context, imageUrl: Uri) {
      val target = Glide.with(context)
        .download(imageUrl)
        .submit()
    
      withContext(Dispatchers.IO) {
        try {
          // TODO: copy file to Downloads directory.
          val file: File = target.get()

        } catch (e: Throwable) { … }
      }
    }
    ```
