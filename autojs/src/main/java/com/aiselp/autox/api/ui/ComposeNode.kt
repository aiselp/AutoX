package com.aiselp.autox.api.ui

import androidx.compose.ui.Modifier

interface ComposeNode {
    var id: Int
    var parentNode: ComposeElement?
    var modifier: Modifier
}