# Desktop touch scrolling coverage

Scope: desktop touch and mouse dragging, with default Compose momentum. Player screens and player-hosted panels retain their previous behavior. No global event interceptor is installed.

## Audited vertical containers

| Container | Status |
| --- | --- |
| `commonMain/kotlin/com/nuvio/app/AppShellComponents.kt:798` | Covered |
| `commonMain/kotlin/com/nuvio/app/core/ui/Components.kt:119` | Covered |
| `commonMain/kotlin/com/nuvio/app/core/ui/DropdownChip.kt:256` | Covered |
| `commonMain/kotlin/com/nuvio/app/core/ui/TokenPreview.kt:28` | Intentionally disabled: design preview |
| `commonMain/kotlin/com/nuvio/app/core/ui/TrackingListPickerDialog.kt:86` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/auth/AuthScreen.kt:440` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/catalog/CatalogScreen.kt:254` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:508` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:2082` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/CollectionFolderBrowserScreen.kt:67` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/CollectionManagementScreen.kt:248` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/FolderDetailScreen.kt:446` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/collection/FolderDetailScreen.kt:578` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/components/CommentDetailSheet.kt:173` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsScreen.kt:2005` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:332` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:450` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:545` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:912` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:1039` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:1127` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/TmdbEntityBrowseScreen.kt:254` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/TmdbEntityBrowseScreen.kt:343` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/TmdbEntityBrowseScreen.kt:382` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/details/TmdbEntityBrowseScreen.kt:660` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/library/LibraryListEditor.kt:59` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/library/LibraryListManagementDialog.kt:127` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/library/LibraryListRows.kt:52` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/onboarding/OnboardingScreen.kt:1132` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/p2p/P2pConsentDialog.kt:38` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/party/PartyChatContent.kt:189` | Covered on standalone pages; disabled by default in player panel |
| `commonMain/kotlin/com/nuvio/app/features/party/PartyPanel.kt:204` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/party/PartyPanel.kt:261` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/party/PartyScreen.kt:223` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/player/AudioTrackModal.kt:79` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt:87` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/PlayerEpisodesPanel.kt:358` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/PlayerStreamList.kt:93` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/skip/SubmitIntroDialog.kt:95` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/StreamInfoOverlay.kt:57` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/SubtitleModal.kt:194` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/SubtitleModal.kt:256` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/player/SubtitleModal.kt:298` | Player-excluded |
| `commonMain/kotlin/com/nuvio/app/features/profiles/ProfilePopupContent.kt:143` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/profiles/ProfileSelectionScreen.kt:179` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AdvancedSettingsPage.kt:483` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AdvancedSettingsPage.kt:573` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AppearanceSettingsPage.kt:416` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AppearanceSettingsPage.kt:489` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AppearanceSettingsPage.kt:556` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AppIconPicker.kt:189` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AuraCustomization.kt:98` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/AutoSkipSegmentSelectionDialog.kt:33` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/CustomThemeEditor.kt:120` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/DebridSettingsPage.kt:1016` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/DebridSettingsPage.kt:1051` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/HomescreenSettingsPage.kt:368` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/MdbListLibraryListsPicker.kt:198` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/MetaScreenSettingsPage.kt:269` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/NavigationBarPreview.kt:64` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/NavigationBarSettingsSheet.kt:52` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/PlaybackSettingsPage.kt:2560` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/PlaybackSettingsPage.kt:2845` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/PlaybackSettingsPage.kt:2902` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/PlaybackSettingsPage.kt:3000` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/PlaybackSettingsPage.kt:3225` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt:1208` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/StreamsSettingsPage.kt:491` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/StreamsSettingsPage.kt:663` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/SupportersContributorsPage.kt:581` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/SupportersContributorsPage.kt:619` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/TrackingAdaptivePicker.kt:201` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/settings/UpdateChannelSettings.kt:68` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/shuffle/EpisodeShuffleSheet.kt:119` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/streams/StreamsScreen.kt:869` | Covered |
| `commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterBanner.kt:377` | Covered |
| `fullCommonMain/kotlin/com/nuvio/app/features/plugins/PluginSettingsDialog.kt:51` | Covered |

## Pages covered through NuvioScreen

- `commonMain/kotlin/com/nuvio/app/features/addons/AddonsScreen.kt:77`
- `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:246`
- `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:650`
- `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:954`
- `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:1060`
- `commonMain/kotlin/com/nuvio/app/features/collection/CollectionEditorScreen.kt:1654`
- `commonMain/kotlin/com/nuvio/app/features/home/HomeScreen.kt:1078`
- `commonMain/kotlin/com/nuvio/app/features/library/LibraryScreen.kt:344`
- `commonMain/kotlin/com/nuvio/app/features/party/PartyScreen.kt:149`
- `commonMain/kotlin/com/nuvio/app/features/profiles/ProfileEditScreen.kt:119`
- `commonMain/kotlin/com/nuvio/app/features/search/SearchScreen.kt:400`
- `commonMain/kotlin/com/nuvio/app/features/settings/LicensesAttributionsPage.kt:79`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:75`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:114`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:146`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:184`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:217`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsFullScreenPages.kt:241`
- `commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt:701`
- `commonMain/kotlin/com/nuvio/app/features/settings/SupportersContributorsPage.kt:273`

## Other scrolling and shared components

- Menu and NuvioDropdownChip dropdowns: covered using their existing native scroll state.
- Existing horizontal nuvioDesktopDragScroll callers: momentum enabled. ProviderFilterRow and StreamCard default to legacy dragging without momentum; only non-player StreamsScreen and StreamsTabletLayout callers opt in.
- PartyChatContent defaults to the existing list behavior; PartyScreen and PartyCompanionScreen explicitly opt in. PartyPanel is unchanged.
- Native scrollables and child controls retain first refusal; disabled user scrolling remains disabled.
- Screen activity, disposal, a new press, wheel input, and competing scroll-state mutations cancel custom momentum.

## Verification

Automated coverage: DesktopDragScrollTest plus the existing desktop suite. Physical Windows touchscreen verification is pending; synthetic touch and mouse tests cannot establish hardware event delivery on every device.
