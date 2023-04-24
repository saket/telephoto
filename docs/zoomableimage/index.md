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

### Crossfade transitions

=== "Coil"
    ```kotlin hl_lines="4"
    ZoomableAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .crossfade(1000)
        .build(),
      contentDescription = …
    )
    ```

=== "Glide"
    ```kotlin hl_lines="5"
    ZoomableGlideImage(
      model = Glide
        .with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .transition(withCrossFade(1000)),
      contentDescription = …
    )
    ```

### Placeholders

Full quality images can take a while to load. If an image is available in multiple resolutions, `telephoto` can use its lower resolution as a placeholder while its full quality equivalent loads in the background.

When combined with a cross-fade transition, `telephoto` will also smoothly swap out the placeholder with its full quality version.

=== "Coil"
    ```kotlin hl_lines="4-5"
    ZoomableAsyncImage(
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
    ```kotlin hl_lines="5-6"
    ZoomableGlideImage(
      model = Glide
        .with(LocalContext.current)
        .load("https://example.com/image.jpg")
        .thumbnail(…)   // or placeholder()
        .transition(withCrossFade(1000)),
      contentDescription = …
    )
    ```
    More details about `placeholder()` can be found on [Glide's website](https://bumptech.github.io/glide/doc/options.html#thumbnail-requests).
