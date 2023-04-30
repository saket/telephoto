Designing a cohesive media experience for Android can be a lot of work. Telephoto aims to make that easier by offering some building blocks:

### [Zoomable Image](zoomableimage/index.md)
Drop-in replacement for `Image()` composables with support for pan & zoom gestures and sub&#8209;sampling of large bitmaps that'd otherwise not fit in memory.

### [Modifier.zoomable()](zoomable/index.md)
`ZoomableImage`'s gesture detector, packaged as a standalone `Modifier` that can be used with non-image composables.
