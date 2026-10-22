package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppScreenTab
import com.nuvio.app.DesktopHoverSidebar
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaEpisodeCardStyle
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.components.DetailActionButtons
import com.nuvio.app.features.details.components.DetailSeriesContent
import com.nuvio.app.features.details.components.DesktopDetailHero
import com.nuvio.app.features.details.components.EnhancedDetailPage
import com.nuvio.app.features.search.SearchRecentSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.search.DiscoverCatalogOption
import com.nuvio.app.features.search.DiscoverUiState
import com.nuvio.app.features.search.discoverContent
import com.nuvio.app.features.search.firstEnabledFilterIndex
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class KeyboardFocusPathsTest {
    @get:Rule val compose = createComposeRule()

    private fun focus(tag: String) = compose.onNodeWithTag(tag)
        .performSemanticsAction(SemanticsActions.RequestFocus)

    private fun down(tag: String) = compose.onNodeWithTag(tag)
        .performKeyInput { pressKey(Key.DirectionDown) }

    private fun searchContent(typesEnabled: Boolean = true, hasPosters: Boolean = true) {
        val catalog = DiscoverCatalogOption("catalog", "Test", "", "movie", "catalog", "Test catalog")
        val state = DiscoverUiState(
            typeOptions = if (typesEnabled) listOf("movie") else emptyList(), selectedType = "movie",
            catalogOptions = listOf(catalog), selectedCatalogKey = catalog.key,
            items = if (hasPosters) listOf(MetaPreview("poster", "movie", "First poster")) else emptyList(),
            isLoading = !hasPosters,
        )
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(600.dp, 440.dp).testTag("host")) {
                    val list = rememberLazyListState()
                    val filters = remember { RemoteNavigationFocusDestination() }
                    val poster = remember { RemoteNavigationFocusDestination() }
                    val recent = remember { mutableMapOf<String, RemoteNavigationFocusDestination>() }
                    val keys = listOf("search", "recent", "discover_header", "discover_filters", "discover_catalog_context", "discover_posters_0")
                    BindRemoteNavigationFocusDestination(filters) { list.revealNavigationItem("discover_filters", keys, true) }
                    BindRemoteNavigationFocusDestination(poster) { list.revealNavigationItem("discover_posters_0", keys, true) }
                    LazyColumn(state = list) {
                        stickyHeader(key = "search") {
                            NuvioInputField("", {}, "Search", Modifier.testTag("search")
                                .remoteNavigationDown { recent["Recent one"]?.requestFocus() })
                        }
                        item(key = "recent") {
                            SearchRecentSection(
                                listOf("Recent one", "Recent two"), false, {}, {},
                                onClearSearches = {},
                                focusDestinations = recent,
                                onExitDown = { filters.requestFocus() },
                            )
                        }
                        discoverContent(
                            state, filterFocus = filters, firstEnabledFilter = state.firstEnabledFilterIndex,
                            posterFocus = poster,
                            onFilterDown = { if (state.items.isNotEmpty()) poster.requestFocus() },
                            isSourceLoading = false, columns = 3, networkCondition = NetworkCondition.Online,
                            onTypeSelected = {}, onCatalogSelected = {}, onGenreSelected = {}, onPosterClick = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun searchDownVisitsRecentRowsThenFiltersAndFirstPoster() {
        searchContent()
        focus("search")
        down("search")
        compose.onNodeWithText("Recent one").assertIsFocused()
        compose.onNodeWithText("Recent one").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Recent two").assertIsFocused()
        compose.onNodeWithText("Recent two").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Movies").assertIsFocused()
        compose.onNodeWithText("Recent one").assertExists()
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasAnySibling(hasText("First poster")))
    }

    @Test fun recentDownSkipsDisabledFilterAndLoadingKeepsFilterFocus() {
        searchContent(typesEnabled = false, hasPosters = false)
        focus("search")
        down("search")
        compose.onNodeWithText("Recent one").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Recent two").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Test catalog").assertIsFocused()
        compose.onNodeWithText("Test catalog").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Test catalog").assertIsFocused()
    }

    private fun detailsContent(singleSeason: Boolean = false, empty: Boolean = false, height: Int = 700) {
        val videos = if (empty) emptyList() else (1..if (singleSeason) 1 else 3).flatMap { season ->
            (1..10).map { episode -> MetaVideo("$season:$episode", "Episode $season-$episode", season = season, episode = episode) }
        }
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(1000.dp, height.dp).testTag("host")) {
                    val session = LocalRemoteNavigation.current!!
                    LaunchedEffect(Unit) { session.keyboard = true }
                    val entry = remember { RemoteNavigationFocusDestination() }
                    val primary = rememberRemotePrimaryFocus()
                    val meta = MetaDetails("series", "series", "Test series", videos = videos)
                    LazyColumn(Modifier.remoteNavigationDetailsEntry()) {
                    item {
                    EnhancedDetailPage(height.dp, true) {
                        DesktopDetailHero(
                            meta, false, false, "Play", true, false, false, {},
                            null, false, true, {}, {}, null, null, false, {}, {}, null,
                            compact = true, onPlayDown = { entry.requestFocus() },
                        )
                        DetailSeriesContent(
                            meta,
                            preferredSeasonNumber = if (singleSeason) 1 else 2, preferredEpisodeNumber = 8,
                            episodeCardStyle = MetaEpisodeCardStyle.Enhanced, navigationEntry = entry,
                            onEntryUp = { primary() },
                        )
                    }
                    }
                    }
                    BasicText("Back", Modifier.align(Alignment.TopStart).remoteNavigationTarget(role = RemoteNavigationRole.Back).clickable {})
                    BasicText("See More", Modifier.align(Alignment.BottomCenter).remoteNavigationTarget().clickable {})
                }
            }
        }
        compose.onNodeWithText("Play").assertIsFocused()
    }

    @Test fun playDownEntersSelectedSeasonThenVisibleEpisodeAndTracksSeasonChanges() {
        detailsContent()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Season 2").assertIsFocused()
        compose.onNodeWithText("Season 2").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasText("Episode 2-", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Season 3").performClick()
        compose.onNodeWithText("Play").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Season 3").assertIsFocused()
        compose.onNodeWithText("Season 3").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasText("Episode 3-", substring = true)).assertIsDisplayed()
    }

    @Test fun singleSeasonPlayDownEntersVisibleEpisodeWithoutResettingPreferredPosition() {
        detailsContent(singleSeason = true)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasText("Episode 1-", substring = true)).assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Play").assertIsFocused()
    }

    @Test fun latePlayTakesInitialFocusOnceAndBackDownReturnsToPlay() {
        var loaded by mutableStateOf(false)
        var revision by mutableIntStateOf(0)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(900.dp, 600.dp)) {
                    val session = LocalRemoteNavigation.current!!
                    LaunchedEffect(Unit) { session.keyboard = true }
                    Column(Modifier.remoteNavigationDetailsEntry()) {
                        BasicText("Back", Modifier.remoteNavigationTarget(role = RemoteNavigationRole.Back).clickable {})
                        if (loaded) DetailActionButtons(playLabel = "Play")
                        BasicText("See More $revision", Modifier.remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        compose.onNodeWithText("Back").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { loaded = true }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("See More 0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { revision++ }
        compose.onNodeWithText("See More 1").assertIsFocused()
        compose.onNodeWithText("Back").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").assertIsFocused()
    }

    @Test fun revealingVisibleSearchFilterDoesNotScroll() {
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        val positions = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 700.dp)) {
                    list = rememberLazyListState()
                    val destination = remember { RemoteNavigationFocusDestination() }
                    BindRemoteNavigationFocusDestination(destination) {
                        list.revealNavigationItem("filter", listOf("search", "filter"), true)
                    }
                    LaunchedEffect(list) {
                        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                            .collect { positions += it }
                    }
                    LazyColumn(state = list) {
                        stickyHeader(key = "search") {
                            BasicText("Search", Modifier.height(60.dp).remoteNavigationDown { destination.requestFocus() }
                                .remoteNavigationTarget().clickable {})
                        }
                        item(key = "filter") {
                            BasicText("Filter", Modifier.height(50.dp).remoteNavigationFocusDestination(destination)
                                .remoteNavigationTarget().clickable {})
                        }
                        item { Spacer(Modifier.height(1500.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithText("Search").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Search").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Filter").assertIsFocused()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertTrue(positions.all { it == (0 to 0) }, positions.toString()) }
    }

    @Test fun missingEpisodesDoNotRedirectDownToFloatingButton() {
        detailsContent(empty = true)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").assertIsFocused()
    }

    @Test fun shortEnhancedPageRevealsSeasonsAndEpisodesWithoutResettingToTop() {
        detailsContent(height = 400)
        compose.onNodeWithText("Play").assertIsDisplayed().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Season 2").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Season 2").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasText("Episode 2-", substring = true)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNode(isFocused()).assert(hasText("Episode 2-", substring = true)).assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Season 2").assertIsFocused()
        compose.onNodeWithText("Season 2").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
    }

    @Test fun playbackReturnPreservesFocusInsteadOfReinitializingPlay() {
        var enabled by mutableStateOf(true)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled, modifier = Modifier.size(600.dp, 500.dp)) {
                    val session = LocalRemoteNavigation.current
                    LaunchedEffect(session) { session?.keyboard = true }
                    Column(Modifier.remoteNavigationDetailsEntry()) {
                        DetailActionButtons(playLabel = "Play")
                        BasicText("Episode", Modifier.remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Episode").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { enabled = false }
        compose.waitForIdle()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithText("Episode").assertIsFocused()
    }

    @Test fun initialPlayIsRevealedWhenPrecededByOffscreenSections() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 400.dp)) {
                    val session = LocalRemoteNavigation.current!!
                    LaunchedEffect(Unit) { session.keyboard = true }
                    val list = rememberLazyListState()
                    BindRemotePrimaryFocusReveal(list, true)
                    LazyColumn(Modifier.remoteNavigationDetailsEntry(), state = list) {
                        items(5) { Spacer(Modifier.height(500.dp)) }
                        item { DetailActionButtons(playLabel = "Play") }
                    }
                }
            }
        }
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
    }

    @Test fun removingRecentSearchKeepsAdjacentRowThenReturnsToSearch() {
        var history by mutableStateOf(listOf("First", "Second"))
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 500.dp)) {
                    val search = remember { RemoteNavigationFocusDestination() }
                    val recent = remember { mutableMapOf<String, RemoteNavigationFocusDestination>() }
                    Column {
                        NuvioInputField("", {}, "Search", Modifier.testTag("search")
                            .remoteNavigationFocusDestination(search)
                            .remoteNavigationDown { recent[history.firstOrNull()]?.requestFocus() })
                        if (history.isNotEmpty()) SearchRecentSection(
                            history, false, {}, { removed -> history = history.filterNot { it == removed } },
                            onClearSearches = { history = emptyList() },
                            focusDestinations = recent, onExitUp = { search.requestFocus() },
                        )
                    }
                }
            }
        }
        focus("search")
        down("search")
        compose.onNodeWithText("First").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("First").assertDoesNotExist()
        compose.onNodeWithText("Second").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Second").assertDoesNotExist()
        compose.onNodeWithTag("search").assertIsFocused()
    }

    @Test fun sidebarEntryAlwaysFocusesActiveTabFromEveryHeightWithoutActivation() {
        var selected by mutableStateOf(AppScreenTab.Home)
        var activations = 0
        lateinit var session: RemoteNavigationSession
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(800.dp, 700.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    DesktopHoverSidebar(selected, { activations++ }, {}, {}, sidebarWidth = 68.dp)
                    Column(Modifier.align(Alignment.CenterEnd).width(450.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        repeat(3) { index ->
                            Row {
                                BasicText("Content $index", Modifier.testTag("content$index").size(150.dp, 50.dp)
                                    .remoteNavigationTarget().clickable {})
                                BasicText("Right $index", Modifier.testTag("right$index").size(150.dp, 50.dp)
                                    .remoteNavigationTarget().clickable {})
                            }
                        }
                    }
                }
            }
        }
        AppScreenTab.entries.forEach { tab ->
            compose.runOnIdle { selected = tab }
            repeat(3) { index ->
                focus("content$index")
                compose.onNodeWithTag("content$index").performKeyInput { pressKey(Key.DirectionLeft) }
                compose.runOnIdle {
                    assertEquals(RemoteNavigationRole.Sidebar, session.focusedTarget?.role)
                    assertTrue(session.focusedTarget?.sidebarEntry == true, "Expected active $tab from content $index")
                    assertEquals(0, activations)
                }
                // Leave a different sidebar item focused before the next entry.
                compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
            }
        }
        focus("content0")
        compose.onNodeWithTag("content0").performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals(RemoteNavigationRole.Default, session.focusedTarget?.role) }
        focus("content2")
        down("content2")
        compose.runOnIdle { assertEquals(RemoteNavigationRole.Default, session.focusedTarget?.role) }
        focus("right1")
        compose.onNodeWithTag("right1").performKeyInput { keyDown(Key.DirectionLeft) }
        compose.onNodeWithTag("content1").assertIsFocused()
        compose.runOnIdle {
            val source = session.focusedTarget!!
            session.moveFocus(source.route, androidx.compose.ui.focus.FocusDirection.Left, source.manager)
        }
        compose.onNodeWithTag("content1").assertIsFocused()
        compose.onNodeWithTag("content1").performKeyInput {
            keyUp(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
        }
        compose.runOnIdle { assertTrue(session.focusedTarget?.sidebarEntry == true) }
    }
}
