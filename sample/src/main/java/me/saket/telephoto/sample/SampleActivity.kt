package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class SampleActivity : AppCompatActivity() {

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    setContent {
      TelephotoTheme {
        Scaffold { contentPadding ->
          Text(
            modifier = Modifier
              .padding(contentPadding)
              .padding(16.dp),
            text = "Hello world!"
          )
        }
      }
    }
  }
}
