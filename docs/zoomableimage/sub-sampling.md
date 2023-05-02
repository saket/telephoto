# Sub-sampling

For displaying large images that can't fit into memory, `ZoomableImage` automatically divides them into tiles so that they can be loaded lazily.

If `ZoomableImage` ^^can't^^ be used or if sub-sampling of images is always desired, you could potentially use `SubSamplingImage()` directly.

```groovy
implementation("me.saket.telephoto:sub-sampling-image:{{ versions.telephoto }}")
```

```kotlin
val zoomableState = rememberZoomableState()
val imageState = rememberSubSamplingImageState(
  zoomableState = zoomableState,
  imageSource = ImageSource.asset("fox.jpg")
)

SubSamplingImage(
  modifier = Modifier
    .fillMaxSize()
    .zoomable(zoomableState),
  state = imageState,
  contentDescription = …,
)
```

`SubSamplingImage()` is an adaptation of the excellent [subsampling-scale-image-view](https://github.com/davemorrissey/subsampling-scale-image-view) by Dave Morrissey.
