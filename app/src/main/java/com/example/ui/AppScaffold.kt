package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Central application scaffold for edge-to-edge screens.
 *
 * System bars and IME are handled at the scaffold boundary so screen-specific
 * layouts do not need ad-hoc status/navigation padding. Scaffold content padding
 * is consumed exactly once by the content container.
 */
@Composable
fun AppScaffold(
  modifier: Modifier = Modifier,
  topBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      Box(
        Modifier.windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        )
      ) { topBar() }
    },
    bottomBar = {
      Box(
        Modifier
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
          .imePadding()
      ) { bottomBar() }
    },
    floatingActionButton = floatingActionButton,
    content = { innerPadding ->
      Box(
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
      ) {
        content(innerPadding)
      }
    }
  )
}
