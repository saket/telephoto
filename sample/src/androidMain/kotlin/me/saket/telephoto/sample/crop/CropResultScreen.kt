package me.saket.telephoto.sample.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import me.saket.telephoto.sample.CropResultScreenKey
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CropResultScreen(
  key: CropResultScreenKey,
  navigator: Navigator,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    sheetState = sheetState,
    onDismissRequest = { navigator.pop() },
  ) {
    AsyncImage(
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
        .fillMaxWidth()
        .height(240.dp),
      model = ImageRequest.Builder(LocalContext.current)
        .data(key.filePath)
        .crossfade(300)
        .build(),
      contentDescription = null,
      contentScale = ContentScale.Fit,
    )

    ListItem(
      leadingContent = { Icon(Icons.Rounded.Image, contentDescription = null) },
      headlineContent = { Text("Original image") },
      supportingContent = { Text(key.originalSize) },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )

    ListItem(
      leadingContent = { Icon(Icons.Rounded.Crop, contentDescription = null) },
      headlineContent = { Text("Cropped image") },
      supportingContent = {
        Column {
          Text(key.croppedSize)
          Text(key.croppedBounds)
        }
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )

    Spacer(Modifier.height(40.dp))
  }

  DisposableEffect(Unit) {
    onDispose {
      val fs = FileSystem.SYSTEM
      fs.delete(key.filePath.toPath())
    }
  }
}
