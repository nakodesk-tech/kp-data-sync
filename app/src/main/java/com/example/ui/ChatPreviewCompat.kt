package com.example.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Local compatibility wrapper used by the targeted chat preview without changing existing imports. */
internal fun Modifier.clip(shape: Shape): Modifier = androidx.compose.ui.draw.clip(this, shape)
