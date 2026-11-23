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
   no reformatting or import reordering of upstream files. The formatter only checks files changed since its
   ratchet commit, so an upstream file it has never seen would be reformatted by our edit: list it in
   `scripts/spotless-keep-format.txt` and keep upstream's text.
5. `node scripts/fork-diff.js --check` compares the size of that surface with `scripts/fork-diff-budget.json`
   and fails when it grew; `--update-budget` lowers the budget after a cleanup. `--check-doc` fails when this
   file is out of date.
6. After a merge, compare each row of "Hand-written copies of upstream behavior" below with its upstream
   symbol: compilation does not catch drift there.

## Hand-written copies of upstream behavior

Fork code that reproduces an upstream flow instead of calling it, tagged `@mirrors <upstream symbol>: note` in the source.
An upstream change to the symbol leaves this code compiling and silently different.

| Fork code | Mirrors | Note |
|---|---|---|
| localserver/LocalServerHomeFeed.kt | `HomeFeedLoader.loadFlowFeed` | concurrent FlowNeuro discovery round; [resetDepth] fresh session vs load-more; wave-1 query count. |
| localserver/LocalServerHomeFeed.kt | `HomeFeedLoader.loadFlowFeed` | wave-1 lanes, YouTube-only, no wave-2 / persistent cache / neighbours; HomeLanesMirrorTest guards the arguments. |
| localserver/LocalServerHomeFeed.kt | `HomeLoadMorePages` | deeper discovery round, ranked, uncached; discovery depth shared. |
| localserver/LocalServerLibrary.kt | `VideoFeedbackUseCase.toggleWatchLater` | also reports SAVED to FlowNeuroEngine, best-effort. |
| localserver/LocalServerLibrary.kt | `VideoPlayerViewModel.likeVideo, VideoPlayerViewModel.dislikeVideo` | via `LikedVideosRepository` (DataStore). `-1`/empty as [addWatchLater]. |
| localserver/LocalServerSubscriptions.kt | `ChannelViewModel.setChannelNotifications` | channel bell, same switch. |

## Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)

- Modified upstream files: **114** (1144 changed lines). New files: **287**.

| Modified upstream file | + | - |
|---|---:|---:|
| ui/screens/home/chips/HomeChipFeeds.kt | 32 | 34 |
| ui/screens/categories/CategoriesScreen.kt | 36 | 6 |
| data/local/BackupRepository.kt | 20 | 17 |
| data/local/SubscriptionRecordCodec.kt | 22 | 15 |
| ui/screens/onboarding/ImportStep.kt | 25 | 6 |
| data/paging/SearchPagingSource.kt | 17 | 12 |
| ui/screens/channel/ChannelViewModel.kt | 22 | 7 |
| ui/screens/search/SearchViewModel.kt | 22 | 7 |
| data/backup/NewPipeSubscriptionCodec.kt | 23 | 5 |
| ui/screens/home/HomeViewModel.kt | 26 | 2 |
| data/video/VideoDownloadOptionsLoader.kt | 24 | 1 |
| data/repository/YouTubeRepository.kt | 16 | 7 |
| data/update/UpdateRepository.kt | 14 | 9 |
| player/resolver/VideoPlaybackResolver.kt | 21 | 2 |
| data/innertube/RssSubscriptionService.kt | 19 | 3 |
| data/video/VideoDownloadManager.kt | 20 | 2 |
| data/local/ViewHistory.kt | 21 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 18 | 3 |
| ui/screens/player/PlaybackSessionApplier.kt | 21 | 0 |
| data/video/downloader/resolve/DownloadStreamResolver.kt | 16 | 4 |
| ui/screens/home/HomeFeedSources.kt | 17 | 3 |
| ui/screens/home/HomeScreen.kt | 16 | 4 |
| utils/ShareVideo.kt | 13 | 7 |
| data/local/dao/WatchHistoryDao.kt | 17 | 0 |
| data/subscriptions/SubscriptionFeedRepository.kt | 17 | 0 |
| player/EnhancedPlayerManager.kt | 16 | 1 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 15 | 0 |
| ui/screens/channel/ChannelTabController.kt | 12 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 13 | 2 |
| data/update/GitHubRelease.kt | 7 | 7 |
| ui/FlowNavigation.kt | 7 | 7 |
| ui/screens/home/HomeFeedLoader.kt | 9 | 5 |
| ui/screens/player/PlayerSecondaryMetadataLoader.kt | 10 | 4 |
| player/stream/PlaybackLoadResolver.kt | 11 | 1 |
| ui/FlowApp.kt | 2 | 9 |
| ui/screens/player/WatchSessionTracker.kt | 8 | 3 |
| data/comments/CommentsPager.kt | 7 | 3 |
| data/local/LikedVideosRepository.kt | 9 | 1 |
| data/local/entity/VideoEntity.kt | 7 | 3 |
| ui/screens/player/content/PlayerErrorPanel.kt | 7 | 3 |
| ui/screens/player/dialogs/PlayerDialogsContainer.kt | 7 | 3 |
| utils/ThumbnailUrlResolver.kt | 6 | 4 |
| ui/screens/player/content/RelatedVideosList.kt | 6 | 3 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 8 | 1 |
| sync/merge/CollectionMergers.kt | 4 | 4 |
| ui/PlayerNavigation.kt | 5 | 3 |
| ui/screens/player/PlaybackPreparer.kt | 6 | 2 |
| utils/YouTubeLink.kt | 8 | 0 |
| data/video/downloader/work/DownloadTransfer.kt | 7 | 0 |
| player/stream/InnerTubeVideoStreamExtractor.kt | 7 | 0 |
| ui/screens/player/PlaybackLoadController.kt | 6 | 1 |
| sync/mapping/SimpleMappers.kt | 6 | 0 |
| ui/AppHooks.kt | 2 | 4 |
| ui/components/videoplayer/settings/PlayerSettingsSheet.kt | 6 | 0 |
| ui/screens/channel/ChannelCommunityController.kt | 5 | 1 |
| ui/screens/player/dialogs/PlayerSettingsSheetHost.kt | 6 | 0 |
| ui/screens/subscriptions/SubscriptionsFeedContent.kt | 4 | 2 |
| data/local/migrations/Migrations.kt | 5 | 0 |
| data/model/Models.kt | 5 | 0 |
| player/datasource/YouTubeHttpDataSource.kt | 3 | 2 |
| ... 54 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **1** (4 changed lines). New files: **5**.

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroText.kt | 2 | 2 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **144**.

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **154**.

## Build

- Modified upstream files: **2** (14 changed lines). New files: **0**.

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 10 | 3 |
| build.gradle.kts | 1 | 0 |

## Resources / strings

- Modified upstream files: **0** (0 changed lines). New files: **3**.

## Tests

- Modified upstream files: **18** (140 changed lines). New files: **92**.

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/update/GitHubReleaseTest.kt | 16 | 16 |
| app/src/test/java/io/github/aedev/flow/platform/LauncherAliasManifestTest.kt | 14 | 3 |
| app/src/test/java/io/github/aedev/flow/utils/ShareVideoTest.kt | 12 | 3 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 9 | 1 |
| app/src/test/java/io/github/aedev/flow/data/repository/ChannelMetadataNeedTest.kt | 9 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/NavigationDestinationsTest.kt | 8 | 0 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsStreamSelectionTest.kt | 5 | 0 |
| app/src/test/java/io/github/aedev/flow/data/video/DownloadStreamPolicyTest.kt | 5 | 0 |
| app/src/test/resources/profiles/store_inventory.tsv | 5 | 0 |
| app/src/test/java/io/github/aedev/flow/data/repository/ChatReplayPageTest.kt | 2 | 2 |
| app/src/test/java/io/github/aedev/flow/ui/screens/crash/CrashSummaryTest.kt | 3 | 1 |
| app/src/test/java/io/github/aedev/flow/data/backup/BackupRulesTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/data/innertube/RssSubscriptionServiceTest.kt | 2 | 1 |
| app/src/test/java/io/github/aedev/flow/player/stream/PlaybackLoadResolverTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/HomeFeedSourcesSeedTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelHarness.kt | 2 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/chips/HomeChipFeedsTest.kt | 1 | 0 |

