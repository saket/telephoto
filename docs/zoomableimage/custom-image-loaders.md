# Custom image loaders

If your preferred image loading library isn't supported by `telephoto` out of the box, creating your own is easy by extending `ZoomableImageSource`.

```kotlin
ZoomableImage(
  image = ZoomableImageSource.picasso("https://example.com/image.jpg"),
  contentDescription = …,
)
```

```kotlin title="ZoomablePicassoImage.kt"
@Composable
fun ZoomableImageSource.Companion.picasso(
  model: Any?,
  picasso: Picasso = Picasso.Builder(LocalContext.current).build(),
): ZoomableImageSource {
  TODO("See ZoomableImageSource.coil() or glide() for an example.")
}
```
