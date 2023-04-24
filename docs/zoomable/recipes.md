### Reacting to zoom

```kotlin
// Hide status and navigation bars when an image is zoomed in.
val systemUiController = rememberSystemUiController()
systemUiController.isSystemBarsVisible = zoomableState.zoomFraction == 0f
```

### Resetting pan & zoom
`Modifier.zoomable()` will automatically retain its pan & zoom across state restorations. This isn't always desired with lazy layouts such as a `Pager()` where each page is restored every time it becomes visible. 

```kotlin hl_lines="16"
val pagerState = rememberPagerState()
val zoomableState = rememberZoomableState()

HorizontalPager(
  state = pagerState,
  pageCount = 3,
) { pageNum ->
  Box(
    Modifier.zoomable(zoomableState)
  )

  if (pagerState.settledPage != pageNum) {
    // Page is now off-screen. Prevent restoration 
    // of zoom when this page becomes visible again.
    LaunchedEffect(Unit) {
      zoomableState.resetZoomImmediately()
    }
  }
}
```
