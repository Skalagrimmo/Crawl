package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) // Handle edge-to-edge full bleed
        ) { innerPadding ->
          val viewModel: GameViewModel = viewModel()
          TerminalScreen(viewModel = viewModel)
        }
      }
    }
  }
}
