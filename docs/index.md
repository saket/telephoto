<style>
/* A table of contents isn't useful on this page. */
.md-nav .md-nav--secondary {
  display: none !important;
}
</style>

![type:video](assets/demo_small.mp4)

Designing a cohesive media experience for Android can be a lot of work. Telephoto aims to make that easier by offering some building blocks:

### [Zoomable Image](zoomableimage/index.md)
_Drop-in_ replacement for `Image()` composables featuring support for pan & zoom gestures and automatic sub&#8209;sampling of large images that'd otherwise not fit into memory.

### [Modifier.zoomable()](zoomable/index.md)
`ZoomableImage`'s gesture detector, packaged as a standalone `Modifier` that can be used with non-image composables.
