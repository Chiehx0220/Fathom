# Where this fork differs from upstream

Generated from `git diff upstream/main`. Regenerate before a merge with:

```
node scripts/fork-diff.js > FORK-DIFF.md
```

The rows that matter for a merge are **modified upstream files** (upstream has the file, we changed lines
in it). **New files** never conflict; they are ours alone.

## Merging upstream

1. `git fetch upstream` and merge often - small merges conflict less than one big one.
2. `git config rerere.enabled true` is set in this clone: a conflict resolved once is resolved the same
   way next time.
3. After a merge, compile (`gradlew.bat compileGithubNightlyKotlin`). The likely breakage is in the
   "Hooks in upstream files" group below, not in the Bilibili or local server files.
4. Rules that keep the surface small: new code goes in a new file and upstream files only get a call to it;
   no reformatting or import reordering of upstream files.

## Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)

- Modified upstream files: **121** (2796 changed lines). New files: **49** (4582 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| ui/screens/player/PlaybackSessionApplier.kt | 177 | 0 |
| player/EnhancedPlayerManager.kt | 127 | 30 |
| data/local/BackupRepository.kt | 109 | 18 |
| player/datasource/YouTubeHttpDataSource.kt | 97 | 7 |
| ui/tv/components/TvNavRail.kt | 51 | 45 |
| ui/screens/channel/ChannelViewModel.kt | 81 | 3 |
| notification/SubscriptionCheckWorker.kt | 79 | 0 |
| data/innertube/RssSubscriptionService.kt | 72 | 3 |
| ui/screens/search/SearchViewModel.kt | 71 | 4 |
| MainActivity.kt | 46 | 26 |
| ui/screens/search/SearchScreen.kt | 64 | 4 |
| ui/screens/home/FlowHeaderLogoIcon.kt | 60 | 7 |
| ui/screens/player/PlaybackStreamPreparer.kt | 63 | 0 |
| ui/tv/screens/TvSettingsScreen.kt | 38 | 25 |
| ui/screens/categories/CategoriesScreen.kt | 53 | 7 |
| player/stream/PlaybackLoadResolver.kt | 49 | 1 |
| data/video/downloader/resolve/DownloadStreamResolver.kt | 43 | 6 |
| data/local/PlayerPreferences.kt | 39 | 0 |
| data/local/SubscriptionRecordCodec.kt | 24 | 15 |
| player/resolver/VideoPlaybackResolver.kt | 36 | 3 |
| ui/screens/home/HomeFeedSources.kt | 33 | 5 |
| ui/screens/home/HomeViewModel.kt | 34 | 4 |
| ui/screens/player/VideoPlayerViewModel.kt | 31 | 7 |
| ui/FlowNavigation.kt | 24 | 10 |
| ui/screens/player/PlaybackPreparer.kt | 32 | 2 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 33 | 1 |
| ui/ChannelNavigation.kt | 22 | 11 |
| ui/screens/onboarding/ImportStep.kt | 25 | 6 |
| data/backup/NewPipeSubscriptionCodec.kt | 24 | 5 |
| data/local/ViewHistory.kt | 28 | 0 |
| data/video/VideoDownloadOptionsLoader.kt | 26 | 1 |
| utils/ShareVideo.kt | 19 | 8 |
| data/comments/CommentsPager.kt | 19 | 6 |
| ui/components/shared/card/VideoCardStacked.kt | 15 | 10 |
| data/paging/SearchPagingSource.kt | 18 | 6 |
| data/video/VideoDownloadManager.kt | 23 | 1 |
| data/update/UpdateRepository.kt | 14 | 9 |
| player/stream/ResolvedPlayback.kt | 23 | 0 |
| ui/NavigationDestinations.kt | 19 | 4 |
| data/local/SubscriptionRepository.kt | 22 | 0 |
| data/local/dao/WatchHistoryDao.kt | 22 | 0 |
| data/subscriptions/SubscriptionFeedRepository.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| player/error/VideoErrorMapper.kt | 20 | 0 |
| ui/FlowApp.kt | 13 | 7 |
| ui/screens/player/content/PlayerErrorPanel.kt | 17 | 3 |
| data/local/entity/VideoEntity.kt | 10 | 9 |
| ui/AppHooks.kt | 10 | 9 |
| ui/components/shared/quickactions/QuickActionsViewModel.kt | 14 | 3 |
| ui/screens/home/HomeScreen.kt | 15 | 2 |
| ui/PlayerNavigation.kt | 12 | 4 |
| ui/screens/channel/ChannelTabController.kt | 13 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 14 | 2 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 15 | 0 |
| ui/components/shared/quickactions/VideoQuickActionsSheet.kt | 12 | 3 |
| ui/screens/player/WatchSessionTracker.kt | 12 | 3 |
| ui/screens/player/dialogs/PlayerDialogsContainer.kt | 14 | 1 |
| ui/screens/player/stage/VideoStage.kt | 15 | 0 |
| data/update/GitHubRelease.kt | 7 | 7 |
| player/GlobalPlayerState.kt | 13 | 0 |
| ... 61 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **2** (6 changed lines). New files: **2** (747 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroText.kt | 2 | 2 |
| data/recommendation/NeuroModels.kt | 2 | 0 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **55** (6295 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **40** (7033 lines).

## Build

- Modified upstream files: **1** (11 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 8 | 3 |

## Resources / strings

- Modified upstream files: **65** (1348 changed lines). New files: **4** (76 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/main/res/values/strings.xml | 170 | 89 |
| app/src/main/AndroidManifest.xml | 67 | 35 |
| app/src/main/res/drawable/ic_launcher_expressive_pill_foreground.xml | 34 | 10 |
| app/src/main/res/drawable/ic_splash_logo.xml | 32 | 10 |
| app/src/main/res/drawable/ic_launcher_expressive_pill_monochrome.xml | 34 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_sky_foreground.xml | 24 | 10 |
| app/src/main/res/drawable/splash_icon_expressive_segmented.xml | 0 | 32 |
| app/src/main/res/drawable/ic_launcher_expressive_sky_monochrome.xml | 24 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_oval_foreground.xml | 17 | 10 |
| app/src/main/res/drawable-xhdpi/tv_banner.xml | 8 | 18 |
| app/src/main/res/drawable/ic_fg_ghost.xml | 9 | 15 |
| app/src/main/res/drawable/ic_flow_badge_glyph.xml | 13 | 11 |
| app/src/main/res/drawable/ic_launcher_expressive_cookie_foreground.xml | 17 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_cookie_monochrome.xml | 17 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_oval_monochrome.xml | 17 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_play_foreground.xml | 14 | 10 |
| app/src/main/res/drawable/ic_launcher_expressive_scallop_foreground.xml | 14 | 10 |
| app/src/main/res/drawable/ic_fg_amoled.xml | 9 | 13 |
| app/src/main/res/drawable/ic_launcher_expressive_segmented_foreground.xml | 0 | 22 |
| app/src/main/res/drawable/ic_launcher_expressive_segmented_monochrome.xml | 0 | 22 |
| app/src/main/res/drawable/ic_launcher_expressive_play_monochrome.xml | 14 | 7 |
| app/src/main/res/drawable/ic_launcher_expressive_scallop_monochrome.xml | 14 | 7 |
| app/src/main/res/drawable/ic_launcher_foreground.xml | 9 | 12 |
| app/src/main/res/drawable/ic_fg_monochrome.xml | 9 | 10 |
| app/src/main/res/drawable/ic_fg_flow_play.xml | 7 | 11 |
| app/src/main/res/drawable/ic_flow_badge_shape.xml | 6 | 12 |
| app/src/main/res/values/themes.xml | 0 | 18 |
| app/src/main/res/drawable/ic_launcher_dynamic_foreground.xml | 8 | 9 |
| app/src/main/res/values-zh-rCN/strings.xml | 6 | 9 |
| app/src/main/res/drawable/ic_launcher_expressive_mint_foreground.xml | 0 | 13 |
| ... 35 more, each small | | |

## Tests

- Modified upstream files: **12** (77 changed lines). New files: **2** (168 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/update/GitHubReleaseTest.kt | 13 | 13 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |
| app/src/test/java/io/github/aedev/flow/player/stream/VideoCodecUtilsTest.kt | 6 | 0 |
| app/src/test/java/io/github/aedev/flow/utils/ShareVideoTest.kt | 3 | 3 |
| app/src/test/java/io/github/aedev/flow/player/stream/PlaybackLoadResolverTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/data/innertube/RssSubscriptionServiceTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/components/shared/quickactions/QuickActionsMessagesTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/HomeFeedSourcesSeedTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelHarness.kt | 1 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/search/SearchViewModelTest.kt | 1 | 0 |

