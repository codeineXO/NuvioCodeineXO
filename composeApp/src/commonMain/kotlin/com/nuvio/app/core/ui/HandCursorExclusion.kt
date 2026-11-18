package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/** Marks a clickable that keeps the normal arrow cursor on desktop: posters, cards and menu rows. */
val NoHandCursorKey = SemanticsPropertyKey<Unit>("NuvioNoHandCursor")

fun Modifier.noHandCursor(): Modifier = semantics { this[NoHandCursorKey] = Unit }
