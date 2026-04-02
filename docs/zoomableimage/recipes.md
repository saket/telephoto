# Recipes

### Modifier.zoomable()
- [Observing pan & zoom](../zoomable/recipes.md#observing-pan-zoom)
- [Controlling pan & zoom](../zoomable/recipes.md#controlling-pan-zoom)
- [Resetting zoom](../zoomable/recipes.md#resetting-zoom)
- [Observing press gestures](../zoomable/recipes.md#observing-press-gestures)

### Setting zoom limits

=== "Coil"
    ```kotlin hl_lines="2-5"
    val zoomableState = rememberZoomableState(
      zoomSpec = ZoomSpec(
        maxZoomFactor = 4f,
        overzoomEffect = OverzoomEffect.RubberBanding,
      )
    )
    
    ZoomableAsyncImage(
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
    )
    ```
=== "Glide"
    ```kotlin hl_lines="2-5"
    val zoomableState = rememberZoomableState(
      zoomSpec = ZoomSpec(
        maxZoomFactor = 4f,
        overzoomEffect = OverzoomEffect.RubberBanding,
      )
    )
    
    ZoomableGlideImage(
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
    )
    ```

### Observing image loads

```kotlin
val imageState = rememberZoomableImageState()

// Whether the full quality image is loaded. This will be false for placeholders
// or thumbnails, in which case isPlaceholderDisplayed can be used instead.
val showLoadingIndicator = imageState.isImageDisplayed

AnimatedVisibility(visible = showLoadingIndicator) {
  CircularProgressIndicator()    
}
```

### Converting between viewport and image coordinates

=== "Coil"
    ```kotlin
    val zoomableState = rememberZoomableState()
    
    ZoomableAsyncImage(
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
      onClick = { clickedAt: Offset ->
        val clickedAt = SpatialOffset(clickedAt, CoordinateSpace.Viewport)
        val offsetInImage = with(zoomableState.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.ZoomableContent)
        }
      }
    )
    ```
=== "Glide"
    ```kotlin
    val zoomableState = rememberZoomableState()
    
    ZoomableGlideImage(
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
      onClick = { clickedAt: Offset ->
        val clickedAt = SpatialOffset(clickedAt, CoordinateSpace.Viewport)
        val offsetInImage = with(zoomableState.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.ZoomableContent)
        }
      }
    )
    ```

### Drawing drop shadows

`Modifier.shadow()` doesn't work with `ZoomableImage`, as it applies shadows to the whole viewport. Instead, use `graphicsLayer` with a custom shape to apply shadows only to the visible bounds of the image:  

=== "Coil"
    ```kotlin hl_lines="4-7"
    val zoomableState = rememberZoomableState() 
    
    ZoomableAsyncImage(
      modifier = Modifier.graphicsLayer {
        this.shadowElevation = 40.dp.toPx()
        this.shape = VisibleImageShape(zoomableState)
      },
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
    )
    ```
=== "Glide"
    ```kotlin
    val zoomableState = rememberZoomableState() 
    
    ZoomableGlideImage(
      modifier = Modifier.graphicsLayer {
        this.shadowElevation = 40.dp.toPx()
        this.shape = VisibleImageShape(zoomableState)
      },
      state = rememberZoomableImageState(zoomableState),
      model = "https://example.com/image.jpg",
      contentDescription = "…",
    )
    ```

```kotlin
/** A shape that clips a composable to match the visible image area.*/
@OptIn(ExperimentalTelephotoApi::class)
data class VisibleImageShape(
  val state: ZoomableState
): Shape {

  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val visibleBounds: Rect = with(state.coordinateSystem) {
      contentBounds.rectIn(CoordinateSpace.Viewport)
    }
    return Outline.Rectangle(visibleBounds)
  }
}
```

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
      contentDescription = "…",
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
      contentDescription = "…",
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
