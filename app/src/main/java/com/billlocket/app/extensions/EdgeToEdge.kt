/**
 * Copyright (c) 2025 lcaohoanq. All rights reserved.
 * This software is the confidential and proprietary information of lcaohoanq.
 * You shall not disclose such confidential information and shall use it only in
 * accordance with the terms of the license agreement you entered into with lcaohoanq.
 */
package com.billlocket.app.extensions

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Enables edge-to-edge once at the activity boundary for the dark BillLocket shell.
 */
fun ComponentActivity.edgeToEdgeWithStyle() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(
            scrim = Color.Black.copy(alpha = 0.25f).toArgb(),
        ),
    )
}
