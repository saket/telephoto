package me.saket.telephoto.sample.crop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import me.saket.telephoto.sample.CropImageScreenKey
import me.saket.telephoto.sample.viewer.CropHandles
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
internal fun CropImageScreen(key: CropImageScreenKey, navigator: Navigator) {
  Scaffold(
    containerColor = Color.Black,
  ) { contentPadding ->
    Column(Modifier.padding(contentPadding)) {
      Box(
        Modifier
          .weight(1f)
          .padding(WindowInsets.safeGestures.asPaddingValues().union(PaddingValues(32.dp)))
      ) {
        val imageState = rememberZoomableImageState()
        ZoomableAsyncImage(
          modifier = Modifier.fillMaxSize(),
          state = imageState,
          model = ImageRequest.Builder(LocalContext.current)
            .data(key.mediaItem.fullSizedUrl)
            .placeholderMemoryCacheKey(key.mediaItem.placeholderImageUrl)
            .build(),
          contentDescription = key.mediaItem.caption,
        )

        CropHandles(
          modifier = Modifier.fillMaxSize(),
          imageState = imageState,
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { navigator.pop() }) {
          Text("Cancel")
        }

        Button(onClick = { }) {
          Text("Save")
        }
      }
    }
  }
}

@Stable
private fun PaddingValues.union(other: PaddingValues): PaddingValues {
  return UnionPaddingValues(this, other)
}

private data class UnionPaddingValues(
  private val first: PaddingValues,
  private val second: PaddingValues,
) : PaddingValues {
  override fun calculateBottomPadding(): Dp {
    return maxOf(first.calculateBottomPadding(), second.calculateBottomPadding())
  }

  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
    return maxOf(first.calculateLeftPadding(layoutDirection), second.calculateRightPadding(layoutDirection))
  }

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
    return maxOf(first.calculateRightPadding(layoutDirection), second.calculateRightPadding(layoutDirection))
  }

  override fun calculateTopPadding(): Dp {
    return maxOf(first.calculateTopPadding(), second.calculateTopPadding())
  }
}
