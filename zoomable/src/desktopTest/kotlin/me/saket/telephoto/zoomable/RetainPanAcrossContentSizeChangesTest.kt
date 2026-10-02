package me.saket.telephoto.zoomable

import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.internal.SavedZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class, ExperimentalTelephotoApi::class)
class RetainPanAcrossContentSizeChangesTest {
  @BeforeTest fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
  @AfterTest fun tearDown() = Dispatchers.resetMain()

  private val fullSize = Size(10_000f, 5_000f)
  private val previewSize = Size(1_080f, 540f)

  private fun newState(saved: SavedZoomableState, viewport: Size, content: Size) =
    RealZoomableState(saved).apply {
      viewportSize = viewport
      density = Density(1f)
      contentScale = ContentScale.Crop
      setContentLocation(ZoomableContentLocation.unscaledAndTopLeftAligned(content))
    }

  private fun RealZoomableState.save(): SavedZoomableState =
    with(RealZoomableState.Saver) { SaverScope { true }.save(this@save) }!!

  private fun pannedState(viewport: Size): RealZoomableState {
    return newState(SavedZoomableState(autoApplyTransformations = true), viewport, fullSize).apply {
      val inputs = currentGestureStateInputs!!
      gestureState = GestureStateCalculator {
        GestureState(
          userOffset = UserOffset(Offset(-1_500f, 0f)),
          userZoom = UserZoomFactor(1f),
          lastCentroid = inputs.viewportSize.center(),
        )
      }
    }
  }

  private fun Size.center() = Offset(width / 2f, height / 2f)

  /** Position of the content point under the viewport's center, as a fraction of the content's size. */
  private fun RealZoomableState.contentFractionAtViewportCenter(): Offset {
    val inputs = currentGestureStateInputs!!
    val point = with(coordinateSystem) {
      SpatialOffset(inputs.viewportSize.center(), CoordinateSpace.Viewport).offsetIn(CoordinateSpace.ZoomableContent)
    }
    val bounds = inputs.unscaledContentBounds
    return Offset((point.x - bounds.left) / bounds.width, (point.y - bounds.top) / bounds.height)
  }

  private fun assertSameFraction(expected: Offset, actual: Offset, label: String) {
    if (abs(expected.x - actual.x) > 0.005f || abs(expected.y - actual.y) > 0.005f) {
      fail("$label: expected content fraction (${expected.x}, ${expected.y}) but was (${actual.x}, ${actual.y})")
    }
  }

  private fun assertUserZoom(expected: Float, state: RealZoomableState, label: String) {
    val actual = state.contentTransformation.scaleMetadata.userZoom
    if (abs(expected - actual) > 0.01f) {
      fail("$label: expected userZoom $expected but was $actual")
    }
  }

  private fun RealZoomableState.pinchBy(zoomChange: Float, centroid: Offset) = runBlocking {
    transformableState.transform(MutatePriority.UserInput) {
      transformBy(zoomChange = zoomChange, panChange = Offset.Zero, centroid = centroid)
    }
  }

  private fun assertSameOffset(expected: Offset, actual: Offset, label: String) {
    if (abs(expected.x - actual.x) > 1f || abs(expected.y - actual.y) > 1f) {
      fail("$label: expected offset $expected but was $actual")
    }
  }

  @Test fun restored_pan_survives_preview_to_full_size_change() = runComposeUiTest {
    val viewport = Size(1_000f, 2_000f)
    val original = pannedState(viewport)
    val expected = original.contentTransformation.offset
    assertTrue(abs(expected.x) > 1f, "test setup should produce a non-zero pan, was $expected")

    val restored = newState(original.save(), viewport, previewSize)
    var contentSize by mutableStateOf(previewSize)
    setContent {
      restored.RetainPanAcrossContentSizeChangesEffect()
      restored.setContentLocation(ZoomableContentLocation.unscaledAndTopLeftAligned(contentSize))
    }
    waitForIdle()
    assertSameOffset(expected, restored.contentTransformation.offset, "while preview is displayed")

    contentSize = fullSize
    waitForIdle()
    assertSameOffset(expected, restored.contentTransformation.offset, "after full image loads")
  }

  @Test fun viewport_round_trip_after_restoration_restores_original_transformation() = runComposeUiTest {
    val portrait = Size(1_000f, 2_000f)
    val landscape = Size(2_000f, 1_000f)
    val original = pannedState(portrait)
    val expected = original.contentTransformation

    val restored = newState(original.save(), portrait, fullSize)
    setContent {
      restored.RetainPanAcrossContentSizeChangesEffect()
    }
    waitForIdle()
    restored.viewportSize = landscape
    waitForIdle()
    restored.viewportSize = portrait
    waitForIdle()

    val actual = restored.contentTransformation
    assertSameOffset(expected.offset, actual.offset, "offset after viewport round trip")
    assertTrue(
      abs(expected.scale.scaleX - actual.scale.scaleX) < 0.001f,
      "scale after viewport round trip: expected ${expected.scale} but was ${actual.scale}",
    )
  }

  @Test fun pan_made_on_preview_survives_full_size_change() = runComposeUiTest {
    val viewport = Size(1_000f, 2_000f)
    val state = newState(SavedZoomableState(autoApplyTransformations = true), viewport, previewSize)
    var contentSize by mutableStateOf(previewSize)
    setContent {
      state.RetainPanAcrossContentSizeChangesEffect()
      state.setContentLocation(ZoomableContentLocation.unscaledAndTopLeftAligned(contentSize))
    }
    waitForIdle()
    runBlocking {
      state.transformableState.transform(MutatePriority.UserInput) {
        transformBy(panChange = Offset(-50f, 0f), centroid = viewport.center())
      }
    }
    waitForIdle()
    val expected = state.contentTransformation.offset
    assertTrue(abs(expected.x) > 1f, "test setup should produce a non-zero pan, was $expected")

    contentSize = fullSize
    waitForIdle()
    assertSameOffset(expected, state.contentTransformation.offset, "after full image loads")
  }

  @Test fun zoom_made_on_upscaled_preview_survives_full_size_change() = runComposeUiTest {
    // The recommended dynamic zoom spec allows more zoom for content displayed larger than
    // its original size, so the preview and the full image have different zoom limits.
    val viewport = Size(1_000f, 2_000f)
    val state = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = viewport
      density = Density(1f)
      contentScale = ContentScale.Fit
    }
    var contentSize by mutableStateOf(Size(250f, 125f))
    setContent {
      state.RetainPanAcrossContentSizeChangesEffect()
      state.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(contentSize))
    }
    waitForIdle()
    state.pinchBy(1.8f, centroid = Offset(700f, 1_000f))
    waitForIdle()
    val expectedFraction = state.contentFractionAtViewportCenter()

    contentSize = fullSize
    waitForIdle()
    assertUserZoom(1.8f, state, "after full image loads")
    assertSameFraction(expectedFraction, state.contentFractionAtViewportCenter(), "after full image loads")
  }

  @Test fun restored_anchor_survives_viewport_and_size_change_for_centered_content() {
    val original = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(500f, 500f)
      density = Density(1f)
      setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(200f, 100f)))
    }
    original.pinchBy(2f, centroid = Offset(150f, 200f))
    val expectedFraction = original.contentFractionAtViewportCenter()

    val restored = RealZoomableState(original.save()).apply {
      viewportSize = Size(600f, 500f)
      density = Density(1f)
      setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(400f, 200f)))
    }
    assertSameFraction(expectedFraction, restored.contentFractionAtViewportCenter(), "after restoration")
  }

  @Test fun restoring_layout_sized_content_into_a_resized_viewport_keeps_user_zoom() {
    // Content that matches the layout's bounds is re-measured on resize rather than
    // resolved at a new resolution, so the viewport adjustment must not rescale it.
    val original = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(1_000f, 2_000f)
      density = Density(1f)
    }
    original.pinchBy(1.5f, centroid = Offset(300f, 300f))

    val restored = RealZoomableState(original.save()).apply {
      viewportSize = Size(2_000f, 4_000f)
      density = Density(1f)
    }
    assertUserZoom(1.5f, restored, "after restoration")
  }

  @Test fun restoring_a_relaid_out_image_after_rotation_keeps_user_zoom() {
    // ContentScale.Inside re-lays out an image larger than the viewport when the viewport
    // changes, which must not be mistaken for a change in the image's resolution.
    val location = ZoomableContentLocation.scaledInsideAndCenterAligned(Size(4_000f, 3_000f))
    val original = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(1_080f, 2_400f)
      density = Density(1f)
      setContentLocation(location)
    }
    original.pinchBy(1.2f, centroid = Offset(540f, 1_200f))

    val restored = RealZoomableState(original.save()).apply {
      viewportSize = Size(2_400f, 1_080f)
      density = Density(1f)
      setContentLocation(location)
    }
    assertUserZoom(1.2f, restored, "after restoration")
  }

  @Test fun relaying_out_the_same_content_does_not_replace_the_gesture_state() = runComposeUiTest {
    val location = ZoomableContentLocation.scaledInsideAndCenterAligned(Size(4_000f, 3_000f))
    val state = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(1_080f, 2_400f)
      density = Density(1f)
      setContentLocation(location)
    }
    setContent {
      state.RetainPanAcrossContentSizeChangesEffect()
    }
    waitForIdle()
    state.pinchBy(1.2f, centroid = Offset(540f, 1_200f))
    val calculator = state.gestureState

    state.viewportSize = Size(2_400f, 1_080f)
    waitForIdle()
    assertTrue(state.gestureState === calculator, "rotation should not replace the gesture state")
  }

  @Test fun resizing_layout_sized_content_does_not_replace_the_gesture_state() = runComposeUiTest {
    val state = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(1_000f, 2_000f)
      density = Density(1f)
    }
    setContent {
      state.RetainPanAcrossContentSizeChangesEffect()
    }
    waitForIdle()
    state.pinchBy(1.5f, centroid = Offset(300f, 300f))
    val calculator = state.gestureState

    state.viewportSize = Size(2_000f, 4_000f)
    waitForIdle()
    assertTrue(state.gestureState === calculator, "resize should not replace the gesture state")
  }

  @Test fun zoom_made_on_a_rounded_thumbnail_survives_full_size_change() = runComposeUiTest {
    // 6000x4000 scaled down to a whole-pixel 320x213 thumbnail.
    val viewport = Size(1_000f, 2_000f)
    val state = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = viewport
      density = Density(1f)
      contentScale = ContentScale.Fit
    }
    var contentSize by mutableStateOf(Size(320f, 213f))
    setContent {
      state.RetainPanAcrossContentSizeChangesEffect()
      state.setContentLocation(ZoomableContentLocation.unscaledAndTopLeftAligned(contentSize))
    }
    waitForIdle()
    state.pinchBy(2f, centroid = Offset(300f, 1_000f))
    // A second event so that the active gesture calculator is not a replay of the pinch from rest.
    runBlocking {
      state.transformableState.transform(MutatePriority.UserInput) {
        transformBy(zoomChange = 1.2f, panChange = Offset(-80f, -40f), centroid = Offset(600f, 900f))
      }
    }
    waitForIdle()
    val expectedZoom = state.contentTransformation.scaleMetadata.userZoom
    val expectedFraction = state.contentFractionAtViewportCenter()

    contentSize = Size(6_000f, 4_000f)
    waitForIdle()
    assertUserZoom(expectedZoom, state, "after full image loads")
    assertSameFraction(expectedFraction, state.contentFractionAtViewportCenter(), "after full image loads")
  }

  @Test fun restored_anchor_survives_rotation_and_resolution_change_together() {
    val original = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(1_080f, 2_400f)
      density = Density(1f)
      setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(400f, 300f)))
    }
    // Zoomed far enough that the content remains pannable horizontally after the rotation.
    original.pinchBy(2.5f, centroid = Offset(400f, 1_100f))
    val expectedFraction = original.contentFractionAtViewportCenter()

    // The full image is re-laid out for the rotated viewport as well as being a higher resolution.
    val restored = RealZoomableState(original.save()).apply {
      viewportSize = Size(2_400f, 1_080f)
      density = Density(1f)
      setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(4_000f, 3_000f)))
    }
    assertSameFraction(expectedFraction, restored.contentFractionAtViewportCenter(), "after restoration")
  }

  @Test fun restored_state_is_kept_when_new_content_looks_unchanged() = runComposeUiTest {
    // The preview is larger than the restored viewport but smaller than the saved one, so the
    // preview and the full image occupy the same bounds only after the rotation.
    val original = RealZoomableState(SavedZoomableState(autoApplyTransformations = true)).apply {
      viewportSize = Size(2_000f, 1_000f)
      density = Density(1f)
      setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(1_500f, 750f)))
    }
    original.pinchBy(2f, centroid = Offset(600f, 400f))

    val restored = RealZoomableState(original.save()).apply {
      viewportSize = Size(1_000f, 2_000f)
      density = Density(1f)
    }
    var contentSize by mutableStateOf(Size(1_500f, 750f))
    setContent {
      restored.RetainPanAcrossContentSizeChangesEffect()
      restored.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(contentSize))
    }
    waitForIdle()
    val expectedZoom = restored.contentTransformation.scaleMetadata.userZoom
    val expectedFraction = restored.contentFractionAtViewportCenter()

    contentSize = Size(6_000f, 3_000f)
    waitForIdle()
    assertUserZoom(expectedZoom, restored, "after full image loads")
    assertSameFraction(expectedFraction, restored.contentFractionAtViewportCenter(), "after full image loads")
  }
}
