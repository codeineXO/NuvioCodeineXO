package com.nuvio.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.navigationGlass
import com.nuvio.app.core.ui.navigationGlassFeedback
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import com.nuvio.app.core.ui.LocalRemoteNavigation
import com.nuvio.app.core.ui.LocalRemoteVerticalNavigationGroup
import com.nuvio.app.features.plugins.PluginIdResolution
import kotlinx.datetime.Instant
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Shared by every details background/layout and both stream selection layouts. */
@Composable
internal fun MetadataInfoPanel(
    meta: MetaDetails?,
    requestedId: String,
    modifier: Modifier = Modifier,
    isFromCache: Boolean? = null,
    playbackId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    pluginResolutions: List<PluginIdResolution> = emptyList(),
    showPluginRequest: Boolean = false,
    savedMetadata: MetadataSnapshot? = null,
) {
    val tokens = MaterialTheme.nuvio
    val headerInteraction = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val keyboardNavigation = isRemoteKeyboardNavigationActive
    val navigationSession = LocalRemoteNavigation.current
    val verticalGroup = remember(requestedId, playbackId) { Any() }
    var expanded by rememberSaveable(requestedId, playbackId) { mutableStateOf(false) }
    val unknown = stringResource(Res.string.generic_unknown)
    val currentSnapshot = meta?.metadataSnapshotOrNull()
    val snapshot = currentSnapshot ?: savedMetadata
    val usingSavedMetadata = currentSnapshot == null && savedMetadata != null
    val provenance = snapshot?.provenance ?: meta?.provenance ?: MetadataProvenance()
    val metadataId = snapshot?.id ?: meta?.id ?: requestedId
    val provider = metadataIdProvider(metadataId)
        ?: provenance.knownIds.entries.firstOrNull { it.value == metadataId }?.key
        ?: unknown
    val compact = playbackId != null
    Surface(
        modifier = modifier.fillMaxWidth()
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange()
                }
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange()
                }
            }.focusGroup()
            .navigationGlass(shape = tokens.shapes.compactCard, panel = true, backgroundOnly = true),
        shape = tokens.shapes.compactCard,
        color = Color.Transparent,
        contentColor = tokens.colors.textPrimary,
    ) {
        CompositionLocalProvider(LocalRemoteVerticalNavigationGroup provides verticalGroup) {
            Column(Modifier.padding(NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
                TextButton(
                    onClick = {
                        expanded = !expanded
                        if (navigationSession?.keyboard != true) focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth()
                        .remoteNavigationTarget(shape = tokens.shapes.button, verticalOnly = true,
                            onSelect = { expanded = !expanded })
                        .navigationGlassFeedback(headerInteraction, tokens.shapes.button, showFocus = keyboardNavigation),
                    shape = tokens.shapes.button,
                    interactionSource = headerInteraction,
                    colors = ButtonDefaults.textButtonColors(contentColor = tokens.colors.textPrimary),
                ) {
                    Text(stringResource(if (compact) Res.string.metadata_info_stream_title else Res.string.metadata_info_title))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(if (expanded) Res.string.metadata_info_collapse else Res.string.metadata_info_expand),
                    )
                }
                if (!compact) {
                    MetadataInfoRow(stringResource(Res.string.metadata_info_source), provenance.sourceName ?: unknown)
                    MetadataInfoRow(stringResource(Res.string.metadata_info_provider), provider)
                    MetadataInfoRow(stringResource(Res.string.metadata_info_id), metadataId, copyable = true)
                }
                if (expanded) {
                    Column(
                        modifier = if (compact) Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
                    ) {
                        if (compact) {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_source), provenance.sourceName ?: unknown)
                            MetadataInfoRow(stringResource(Res.string.metadata_info_provider), provider)
                            MetadataInfoRow(stringResource(Res.string.metadata_info_id), metadataId, true)
                        }
                        provenance.sourceId?.let { MetadataInfoRow(stringResource(Res.string.metadata_info_source_id), it, true) }
                        val originalId = provenance.requestedId ?: requestedId
                        if (originalId != metadataId) MetadataInfoRow(stringResource(Res.string.metadata_info_requested_id), originalId, true)
                        provenance.lookupId?.takeIf { it != originalId && it != metadataId }?.let {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_lookup_id), it, true)
                        }
                        val knownIds = provenance.knownIds + listOfNotNull(meta?.imdbId?.let { "IMDb" to it }).toMap()
                        knownIds.forEach { (namespace, id) -> MetadataInfoRow(namespace, id, true) }
                        if (provenance.enrichmentSources.isNotEmpty()) {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_enrichment), provenance.enrichmentSources.joinToString(", "))
                        }
                        if (usingSavedMetadata) {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_loaded), stringResource(Res.string.metadata_info_saved_progress))
                        } else isFromCache?.let {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_loaded), stringResource(
                                if (it) Res.string.metadata_info_cache else Res.string.metadata_info_fetch,
                            ))
                        }
                        provenance.fetchedAtMs?.let {
                            MetadataInfoRow(stringResource(Res.string.metadata_info_fetched_at), Instant.fromEpochMilliseconds(it).toString())
                        }
                        if (provenance.usedFallback) Text(stringResource(Res.string.metadata_info_fallback), style = MaterialTheme.typography.bodySmall)
                        playbackId?.let { MetadataInfoRow(stringResource(Res.string.metadata_info_playback_id), it, true) }
                        if (season != null && episode != null) MetadataInfoRow(stringResource(Res.string.metadata_info_episode), "$season / $episode")
                        if (compact && showPluginRequest) {
                            if (pluginResolutions.isEmpty()) MetadataInfoRow(
                                stringResource(Res.string.metadata_info_plugin_status), stringResource(Res.string.metadata_info_not_requested),
                            )
                            pluginResolutions.forEach { result ->
                                Text(result.scraperName, style = MaterialTheme.typography.labelMedium)
                                MetadataInfoRow(stringResource(Res.string.metadata_info_plugin_status), stringResource(when {
                                    result.passedId == null -> Res.string.metadata_info_resolving
                                    result.resolved -> Res.string.metadata_info_resolved
                                    else -> Res.string.metadata_info_unresolved
                                }))
                                MetadataInfoRow(stringResource(Res.string.metadata_info_plugin_input), result.inputId, true)
                                result.passedId?.let { MetadataInfoRow(stringResource(Res.string.metadata_info_plugin_id), it, true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataInfoRow(label: String, value: String, copyable: Boolean = false) {
    val tokens = MaterialTheme.nuvio
    val copyInteraction = remember { MutableInteractionSource() }
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(Res.string.metadata_info_copied)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textSecondary)
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
        }
        if (copyable) IconButton(onClick = {
            clipboard.setText(AnnotatedString(value))
            NuvioToastController.show(copied)
        }, modifier = Modifier.size(NuvioTokens.Space.s40)
            .remoteNavigationTarget(shape = tokens.shapes.avatar, verticalOnly = true, onSelect = {
                clipboard.setText(AnnotatedString(value))
                NuvioToastController.show(copied)
            })
            .navigationGlassFeedback(copyInteraction, tokens.shapes.avatar),
            interactionSource = copyInteraction,
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(Res.string.metadata_info_copy, label), modifier = Modifier.size(NuvioTokens.Space.s16))
        }
    }
}
