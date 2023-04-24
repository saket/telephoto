# Sub-sampling

For images that are large enough to not fit in memory, `ZoomableImage` automatically divides them into tiles so that they can be lazy loaded. 

If `ZoomableImage` can't be used or if sub-sampling of images is always desired, you could potentially use `SubSamplingImage()` directly:

```groovy
implementation("me.saket.telephoto:sub-sampling-image:1.0.0-SNAPSHOT")
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
  contentDescription = null,
)
```

`SubSamplingImage()` is a Compose implementation of the excellent [subsampling-scale-image-view](https://github.com/davemorrissey/subsampling-scale-image-view) by Dave Morrissey.
