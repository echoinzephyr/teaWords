package com.teameow.teawords.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.teameow.teawords.R

/** Colour comes from the live Material palette, including wallpaper-derived Monet colours. */
@Composable
internal fun TeaAppIcon(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(80.dp).clip(RoundedCornerShape(24)).background(colors.primaryContainer)
        .semantics { contentDescription = "茶词图标" }) {
        Icon(painterResource(R.drawable.teawords_symbol), null, Modifier.fillMaxSize(), tint = colors.primary)
        Icon(painterResource(R.drawable.teawords_accent), null, Modifier.fillMaxSize(), tint = colors.tertiary)
        Icon(painterResource(R.drawable.teawords_leaf), null, Modifier.fillMaxSize(), tint = colors.primary)
        Icon(painterResource(R.drawable.teawords_letter_a), null, Modifier.fillMaxSize(), tint = colors.onPrimary)
        Icon(painterResource(R.drawable.teawords_letter_wen), null, Modifier.fillMaxSize(), tint = colors.onTertiary)
    }
}
