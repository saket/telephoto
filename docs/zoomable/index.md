# `Modifier.zoomable()`

A modifier for handling pan & zoom gestures, designed to be shared across all your media composables so that your users can use the same familiar gestures throughout your app. It supports,

- Pinch to zoom and flings
- Double tap to zoom
- Double tap and hold to quick zoom (work in progress)
- Compatibility with nested scrolling

```groovy
implementation("me.saket.telephoto:zoomable:1.0.0-SNAPSHOT")
```

```kotlin hl_lines="4"
Box(
  Modifier
    .size(200.dp)
    .zoomable(rememberZoomableState())
    .background(
      brush = Brush.linearGradient(listOf(Color.Cyan, Color.Blue)),
      shape = RoundedCornerShape(4.dp)
    )
)
```

While `Modifier.zoomable()` was primarily written with images & videos in mind, it can be used for anything such as text, canvas drawings, etc.

### Click listeners
For detecting double taps, `Modifier.zoomable()` is sadly forced to consume all tap gestures making it incompatible with `Modifier.clickable()` and `Modifier.combinedClickable()`. As an alternative, its `onClick` and `onLongClick` parameters can be used.

```kotlin
Modifier.zoomable(
  state = rememberZoomableState(),
  onClick = { … },
  onLongClick = { … },
)
```

### Preventing over-zoom

For preventing your content from over-zooming or over-panning, `Modifier.zoomable()` will use your content's layout size by default. This is good enough for composables that fill _every_ pixel of their drawing space.

For richer content such as an `Image()` whose _visual_ size may not always match its layout size, `Modifier.zoomable()` will need your assistance.

```kotlin hl_lines="5-7"
val state = rememberZoomableState()
val painter = resourcePainter(R.drawable.example)

LaunchedEffect(painter) {
  state.setContentLocation(
    ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize)
  )
}

Image(
  modifier = Modifier
    .fillMaxSize()
    .zoomable(state),
  painter = painter,
  contentDescription = …,
  contentScale = ContentScale.Inside,
  alignment = Alignment.Center,
)
```

### Applying gesture transformations

When pan & zoom gestures are received, `Modifier.zoomable()` automatically applies their resulting `scale` and `translation` onto your content using `Modifier.graphicsLayer()`. 

This can be disabled if your content prefers applying the transformations in a bespoke manner.

```kotlin hl_lines="2 10-11"
val state = rememberZoomableState(
  autoApplyTransformations = false
)

Text(
  modifier = Modifier
    .fillMaxSize()
    .zoomable(state),
  text = "Nicolas Cage",
  style = state.contentTransformation.let {
    val weightMultiplier = if (it.isUnspecified) 1f else it.scale.scaleX
    TextStyle(
      fontSize = 36.sp,
      fontWeight = FontWeight(400 * weightMultiplier),
    )
  }
)
```
