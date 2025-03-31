package me.saket.telephoto.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import telephoto.sample.generated.resources.Res

@Composable
fun rememberSvgPainter(svgName: String): Painter {
  return remember(svgName) {
    SvgPainter(svgName)
  }
}

@Stable
private class SvgPainter(
  private val svgName: String,
) : Painter(), RememberObserver {

  private var scope: CoroutineScope? = null
  private var svg: SVGDOM? by mutableStateOf(null)

  override val intrinsicSize: Size
    get() = svg?.root?.let {
      Size(
        width = it.width.value,
        height = it.height.value,
      )
    } ?: Size.Unspecified

  override fun DrawScope.onDraw() {
    svg?.let { svg ->
      drawIntoCanvas {
        svg.render(it.nativeCanvas)
      }
    }
  }

  @OptIn(ExperimentalResourceApi::class)
  override fun onRemembered() {
    scope = CoroutineScope(SupervisorJob())
    scope!!.launch(Dispatchers.IO) {
      svg = SVGDOM(Data.makeFromBytes(Res.readBytes(svgName)))
    }
  }

  override fun onForgotten() {
    scope?.cancel()
  }

  override fun onAbandoned() = Unit
}
