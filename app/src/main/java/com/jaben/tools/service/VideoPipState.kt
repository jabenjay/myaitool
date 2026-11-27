package com.jaben.tools.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object VideoPipState {
    var isFloatingPlayerActive by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
}
