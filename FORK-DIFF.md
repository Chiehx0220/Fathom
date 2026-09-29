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

- Modified upstream files: **110** (2386 changed lines). New files: **47** (4410 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| player/EnhancedPlayerManager.kt | 121 | 30 |
| data/local/BackupRepository.kt | 109 | 18 |
| ui/screens/player/PlaybackSessionApplier.kt | 116 | 0 |
| player/datasource/YouTubeHttpDataSource.kt | 97 | 7 |
| ui/tv/components/TvNavRail.kt | 51 | 45 |
| ui/screens/channel/ChannelViewModel.kt | 81 | 3 |
| notification/SubscriptionCheckWorker.kt | 79 | 0 |
| data/innertube/RssSubscriptionService.kt | 72 | 3 |
| MainActivity.kt | 46 | 26 |
| ui/screens/search/SearchViewModel.kt | 68 | 4 |
| ui/screens/home/HomeViewModel.kt | 44 | 19 |
| ui/tv/screens/TvSettingsScreen.kt | 38 | 25 |
| ui/screens/search/SearchScreen.kt | 48 | 0 |
| player/stream/PlaybackLoadResolver.kt | 44 | 1 |
| player/resolver/VideoPlaybackResolver.kt | 36 | 3 |
| ui/screens/player/VideoPlayerViewModel.kt | 31 | 7 |
| ui/screens/player/PlaybackStreamPreparer.kt | 35 | 0 |
| ui/FlowNavigation.kt | 24 | 10 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 33 | 1 |
| ui/ChannelNavigation.kt | 22 | 11 |
| ui/screens/home/FlowHeaderLogoIcon.kt | 25 | 7 |
| ui/screens/home/HomeFeedSources.kt | 28 | 4 |
| ui/screens/player/PlaybackPreparer.kt | 30 | 0 |
| data/backup/NewPipeSubscriptionCodec.kt | 24 | 5 |
| data/local/ViewHistory.kt | 28 | 0 |
| utils/ShareVideo.kt | 19 | 8 |
| data/comments/CommentsPager.kt | 19 | 6 |
| ui/components/shared/card/VideoCardStacked.kt | 15 | 10 |
| data/local/PlayerPreferences.kt | 24 | 0 |
| data/paging/SearchPagingSource.kt | 18 | 6 |
| data/video/VideoDownloadManager.kt | 23 | 1 |
| data/video/VideoDownloadOptionsLoader.kt | 23 | 1 |
| ui/NavigationDestinations.kt | 19 | 4 |
| data/local/SubscriptionRepository.kt | 22 | 0 |
| data/local/dao/WatchHistoryDao.kt | 22 | 0 |
| data/subscriptions/SubscriptionFeedRepository.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| ui/screens/home/HomeFeedGrid.kt | 22 | 0 |
| ui/FlowApp.kt | 13 | 7 |
| ui/screens/player/content/PlayerErrorPanel.kt | 17 | 3 |
| data/local/entity/VideoEntity.kt | 10 | 9 |
| ui/AppHooks.kt | 10 | 9 |
| player/stream/ResolvedPlayback.kt | 17 | 0 |
| ui/components/shared/quickactions/QuickActionsViewModel.kt | 14 | 3 |
| ui/PlayerNavigation.kt | 12 | 4 |
| ui/screens/channel/ChannelTabController.kt | 13 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 14 | 2 |
| data/local/SubscriptionRecordCodec.kt | 11 | 4 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 15 | 0 |
| ui/components/shared/quickactions/VideoQuickActionsSheet.kt | 12 | 3 |
| ui/screens/player/WatchSessionTracker.kt | 12 | 3 |
| ui/screens/player/dialogs/PlayerDialogsContainer.kt | 14 | 1 |
| player/GlobalPlayerState.kt | 13 | 0 |
| player/stream/PlaybackPrefetcher.kt | 11 | 1 |
| ui/components/layout/navigation/MediaNavigator.kt | 10 | 2 |
| ui/screens/player/PlayerSecondaryMetadataLoader.kt | 9 | 3 |
| data/local/LikedVideosRepository.kt | 10 | 1 |
| player/stream/InnerTubeVideoStreamExtractor.kt | 10 | 0 |
| player/stream/ResolvedStreamData.kt | 10 | 0 |
| ui/screens/channel/ChannelCommunityController.kt | 9 | 1 |
| ... 50 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **6** (35 changed lines). New files: **2** (173 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroTokenizer.kt | 8 | 9 |
| data/recommendation/NeuroDiscovery.kt | 3 | 3 |
| data/recommendation/FlowNeuroEngine.kt | 2 | 2 |
| data/recommendation/NeuroScoring.kt | 2 | 2 |
| data/recommendation/NeuroClusters.kt | 1 | 1 |
| data/recommendation/NeuroModels.kt | 2 | 0 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **47** (4910 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **35** (6830 lines).

## Build

- Modified upstream files: **1** (7 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 6 | 1 |

## Resources / strings

- Modified upstream files: **42** (633 changed lines). New files: **3** (62 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/main/res/values/strings.xml | 90 | 12 |
| app/src/main/AndroidManifest.xml | 41 | 1 |
| app/src/main/res/drawable/ic_splash_logo.xml | 32 | 10 |
| app/src/main/res/drawable-xhdpi/tv_banner.xml | 8 | 18 |
| app/src/main/res/drawable/ic_fg_ghost.xml | 9 | 15 |
| app/src/main/res/drawable/ic_fg_amoled.xml | 9 | 13 |
| app/src/main/res/drawable/ic_launcher_foreground.xml | 9 | 12 |
| app/src/main/res/drawable/ic_flow_badge_glyph.xml | 9 | 11 |
| app/src/main/res/drawable/ic_fg_monochrome.xml | 9 | 10 |
| app/src/main/res/drawable/ic_fg_flow_play.xml | 7 | 11 |
| app/src/main/res/drawable/ic_flow_badge_shape.xml | 6 | 12 |
| app/src/main/res/drawable/ic_launcher_dynamic_foreground.xml | 8 | 9 |
| app/src/main/res/values-zh-rCN/strings.xml | 6 | 9 |
| app/src/main/res/drawable/ic_notification_logo.xml | 7 | 6 |
| app/src/main/res/values-it/strings.xml | 5 | 8 |
| app/src/main/res/drawable/ic_flow_logo.xml | 0 | 12 |
| app/src/main/res/values-ar/strings.xml | 5 | 7 |
| app/src/main/res/values-uk/strings.xml | 5 | 7 |
| app/src/main/res/values-az/strings.xml | 4 | 7 |
| app/src/main/res/values-es/strings.xml | 4 | 7 |
| app/src/main/res/values-fr/strings.xml | 4 | 7 |
| app/src/main/res/values-in/strings.xml | 4 | 7 |
| app/src/main/res/values-ko/strings.xml | 4 | 7 |
| app/src/main/res/values-pl/strings.xml | 4 | 7 |
| app/src/main/res/values-pt-rBR/strings.xml | 4 | 7 |
| app/src/main/res/values-ru/strings.xml | 4 | 7 |
| app/src/main/res/values-tr/strings.xml | 4 | 7 |
| app/src/main/res/values-vi/strings.xml | 4 | 7 |
| app/src/main/res/values-de/strings.xml | 4 | 6 |
| app/src/main/res/values-hi/strings.xml | 4 | 5 |
| ... 12 more, each small | | |

## Tests

- Modified upstream files: **9** (45 changed lines). New files: **2** (168 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |
| app/src/test/java/io/github/aedev/flow/player/stream/VideoCodecUtilsTest.kt | 6 | 0 |
| app/src/test/java/io/github/aedev/flow/utils/ShareVideoTest.kt | 2 | 2 |
| app/src/test/java/io/github/aedev/flow/player/stream/PlaybackLoadResolverTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/components/shared/quickactions/QuickActionsMessagesTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelHarness.kt | 1 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/search/SearchViewModelTest.kt | 1 | 0 |

