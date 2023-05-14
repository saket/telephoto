### Observing pan & zoom

```kotlin
val state = rememberZoomableState()
Box(
  Modifier.zoomable(state)
)

LaunchedEffect(state.contentTransformation) {
  println("Pan = ${state.contentTransformation.offset}")
  println("Zoom = ${state.contentTransformation.scale}")
  println("Zoom percent = ${state.zoomFraction * 100}%")
}

// Example use case: Hide system bars when image is zoomed in.
val systemUiController = rememberSystemUiController()
systemUiController.isSystemBarsVisible = state.zoomFraction == 0f
```

### Resetting zoom
`Modifier.zoomable()` will automatically retain its pan & zoom across state restorations. You may want to prevent this in lazy layouts such as a `Pager()`, where each page is restored every time it becomes visible. 

```kotlin hl_lines="15"
val pagerState = rememberPagerState()
HorizontalPager(
  state = pagerState,
  pageCount = 3,
) { pageNum ->
  val zoomableState = rememberZoomableState()
  ZoomableContent(
    state = zoomableState
  )

  if (pagerState.settledPage != pageNum) {
    // Page is now off-screen. Prevent restoration of 
    // current zoom when this page becomes visible again.
    LaunchedEffect(Unit) {
      zoomableState.resetZoom(withAnimation = false)
    }
  }
}
```

!!! warning
    A bug in `Pager()` previously caused `settledPage` to reset to `0` upon state restoration. This issue has been resolved in version `1.5.0-alpha02`.
