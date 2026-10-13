# Where this fork differs from upstream

Generated from the diff to the last upstream commit this branch contains. Regenerate it after a merge with:

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
5. `node scripts/fork-diff.js --check` compares the size of that surface with `scripts/fork-diff-budget.json`
   and fails when it grew; `--update-budget` lowers the budget after a cleanup. `--check-doc` fails when this
   file is out of date.

## Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)

- Modified upstream files: **125** (1777 changed lines). New files: **238** (6942 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| ui/screens/home/chips/HomeChipFeeds.kt | 59 | 38 |
| ui/tv/components/TvNavRail.kt | 51 | 45 |
| data/local/BackupRepository.kt | 59 | 20 |
| ui/screens/home/HomeViewModel.kt | 60 | 9 |
| ui/tv/screens/TvSettingsScreen.kt | 38 | 25 |
| player/EnhancedPlayerManager.kt | 52 | 2 |
| data/video/downloader/resolve/DownloadStreamResolver.kt | 43 | 6 |
| ui/screens/home/HomeFeedSources.kt | 42 | 5 |
| ui/screens/categories/CategoriesScreen.kt | 34 | 6 |
| data/local/PlayerPreferences.kt | 39 | 0 |
| data/local/SubscriptionRecordCodec.kt | 24 | 15 |
| player/resolver/VideoPlaybackResolver.kt | 36 | 3 |
| data/innertube/RssSubscriptionService.kt | 28 | 3 |
| ui/screens/onboarding/ImportStep.kt | 25 | 6 |
| data/backup/NewPipeSubscriptionCodec.kt | 24 | 5 |
| data/local/ViewHistory.kt | 28 | 0 |
| data/video/VideoDownloadManager.kt | 25 | 3 |
| data/video/VideoDownloadOptionsLoader.kt | 26 | 1 |
| ui/screens/channel/ChannelViewModel.kt | 23 | 3 |
| data/comments/CommentsPager.kt | 19 | 6 |
| ui/components/shared/card/VideoCardStacked.kt | 15 | 10 |
| ui/screens/player/PlaybackSessionApplier.kt | 25 | 0 |
| ui/screens/search/SearchViewModel.kt | 21 | 4 |
| data/paging/SearchPagingSource.kt | 18 | 6 |
| data/repository/YouTubeRepository.kt | 18 | 6 |
| data/update/UpdateRepository.kt | 14 | 9 |
| data/local/dao/WatchHistoryDao.kt | 22 | 0 |
| data/subscriptions/SubscriptionFeedRepository.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| utils/ShareVideo.kt | 14 | 7 |
| player/error/VideoErrorMapper.kt | 20 | 0 |
| data/local/entity/VideoEntity.kt | 10 | 9 |
| ui/screens/player/content/PlayerErrorPanel.kt | 16 | 3 |
| ui/components/shared/quickactions/QuickActionsViewModel.kt | 14 | 3 |
| ui/screens/channel/ChannelTabController.kt | 13 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 14 | 2 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 15 | 0 |
| ui/screens/home/HomeScreen.kt | 12 | 3 |
| ui/screens/player/WatchSessionTracker.kt | 12 | 3 |
| ui/screens/player/dialogs/PlayerDialogsContainer.kt | 14 | 1 |
| ui/screens/player/stage/VideoStage.kt | 15 | 0 |
| data/update/GitHubRelease.kt | 7 | 7 |
| player/GlobalPlayerState.kt | 13 | 0 |
| player/stream/PlaybackLoadResolver.kt | 12 | 1 |
| ui/components/shared/quickactions/VideoQuickActionsSheet.kt | 11 | 2 |
| ui/screens/player/VideoPlayerViewModel.kt | 11 | 2 |
| ui/screens/player/PlayerSecondaryMetadataLoader.kt | 9 | 3 |
| ui/screens/search/SearchScreen.kt | 12 | 0 |
| ui/startup/SplashThemes.kt | 0 | 12 |
| data/local/LikedVideosRepository.kt | 10 | 1 |
| ui/FlowApp.kt | 2 | 9 |
| MainActivity.kt | 9 | 1 |
| data/video/downloader/work/DownloadTransfer.kt | 9 | 1 |
| player/stream/InnerTubeVideoStreamExtractor.kt | 10 | 0 |
| player/stream/ResolvedStreamData.kt | 10 | 0 |
| ui/FlowNavigation.kt | 5 | 5 |
| ui/screens/channel/ChannelCommunityController.kt | 9 | 1 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 9 | 1 |
| player/stream/VideoCodecUtils.kt | 4 | 5 |
| ui/AppHooks.kt | 4 | 5 |
| ... 65 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **2** (6 changed lines). New files: **2** (747 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroText.kt | 2 | 2 |
| data/recommendation/NeuroModels.kt | 2 | 0 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **71** (7649 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **66** (8506 lines).

## Build

- Modified upstream files: **1** (15 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 11 | 4 |

## Resources / strings

- Modified upstream files: **0** (0 changed lines). New files: **3** (62 lines).

## Tests

- Modified upstream files: **18** (132 changed lines). New files: **9** (550 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/update/GitHubReleaseTest.kt | 13 | 13 |
| app/src/test/java/io/github/aedev/flow/platform/LauncherAliasManifestTest.kt | 13 | 2 |
| app/src/test/java/io/github/aedev/flow/utils/ShareVideoTest.kt | 12 | 3 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/data/repository/ChannelMetadataNeedTest.kt | 9 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/NavigationDestinationsTest.kt | 8 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |
| app/src/test/java/io/github/aedev/flow/player/stream/VideoCodecUtilsTest.kt | 6 | 0 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsStreamSelectionTest.kt | 5 | 0 |
| app/src/test/java/io/github/aedev/flow/data/video/DownloadStreamPolicyTest.kt | 5 | 0 |
| app/src/test/java/io/github/aedev/flow/data/innertube/RssSubscriptionServiceTest.kt | 2 | 1 |
| app/src/test/java/io/github/aedev/flow/player/stream/PlaybackLoadResolverTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/components/shared/quickactions/QuickActionsMessagesTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/crash/CrashSummaryTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/HomeFeedSourcesSeedTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelHarness.kt | 2 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/chips/HomeChipFeedsTest.kt | 1 | 0 |

