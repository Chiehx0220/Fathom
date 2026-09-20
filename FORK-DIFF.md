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

- Modified upstream files: **91** (1932 changed lines). New files: **15** (809 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| player/EnhancedPlayerManager.kt | 120 | 30 |
| ui/screens/player/PlaybackSessionApplier.kt | 144 | 0 |
| player/datasource/YouTubeHttpDataSource.kt | 128 | 6 |
| data/local/BackupRepository.kt | 104 | 26 |
| ui/screens/channel/ChannelViewModel.kt | 83 | 2 |
| notification/SubscriptionCheckWorker.kt | 75 | 0 |
| data/innertube/RssSubscriptionService.kt | 66 | 3 |
| ui/screens/search/SearchViewModel.kt | 65 | 4 |
| player/stream/PlaybackLoadResolver.kt | 62 | 1 |
| ui/screens/home/HomeViewModel.kt | 42 | 19 |
| MainActivity.kt | 18 | 26 |
| ui/screens/search/SearchScreen.kt | 39 | 4 |
| ui/FlowNavigation.kt | 26 | 16 |
| ui/screens/player/VideoPlayerViewModel.kt | 32 | 7 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 36 | 0 |
| data/local/SubscriptionRepository.kt | 34 | 1 |
| data/comments/CommentsPager.kt | 28 | 5 |
| player/resolver/VideoPlaybackResolver.kt | 28 | 3 |
| ui/screens/home/HomeFeedSources.kt | 27 | 4 |
| ui/components/VideoCard.kt | 16 | 11 |
| ui/AppHooks.kt | 14 | 11 |
| ui/NavigationDestinations.kt | 20 | 5 |
| data/local/PlayerPreferences.kt | 24 | 0 |
| ui/screens/home/HomeFeedGrid.kt | 23 | 1 |
| utils/ShareVideo.kt | 18 | 6 |
| data/local/ViewHistory.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| data/subscriptions/SubscriptionFeedRepository.kt | 21 | 0 |
| ui/ChannelNavigation.kt | 19 | 2 |
| ui/FlowApp.kt | 13 | 5 |
| ui/screens/player/content/PlayerErrorPanel.kt | 15 | 3 |
| player/stream/ResolvedPlayback.kt | 17 | 0 |
| ui/PlayerNavigation.kt | 12 | 4 |
| ui/screens/player/WatchSessionTracker.kt | 12 | 3 |
| data/local/AppDatabase.kt | 13 | 1 |
| data/local/dao/WatchHistoryDao.kt | 14 | 0 |
| data/paging/SearchPagingSource.kt | 13 | 1 |
| player/GlobalPlayerState.kt | 12 | 0 |
| player/stream/PlaybackPrefetcher.kt | 11 | 1 |
| ui/screens/player/PlayerSecondaryMetadataLoader.kt | 9 | 3 |
| player/stream/InnerTubeVideoStreamExtractor.kt | 10 | 0 |
| player/stream/ResolvedStreamData.kt | 10 | 0 |
| ui/screens/channel/ChannelCommunityController.kt | 9 | 1 |
| data/local/entity/VideoEntity.kt | 6 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 7 | 2 |
| sync/merge/CollectionMergers.kt | 4 | 4 |
| ui/screens/player/stage/VideoStage.kt | 8 | 0 |
| ui/components/shared/VideoShareAction.kt | 5 | 2 |
| ui/screens/player/content/VideoInfoContent.kt | 4 | 3 |
| ui/screens/settings/SettingsScreen.kt | 7 | 0 |
| data/engagement/VideoEngagementUseCase.kt | 6 | 0 |
| data/local/LikedVideosRepository.kt | 5 | 1 |
| sync/canonical/Canonical.kt | 6 | 0 |
| sync/mapping/SimpleMappers.kt | 6 | 0 |
| ui/components/search/SearchResults.kt | 5 | 1 |
| ui/components/videoplayer/settings/PlayerSettingsSheet.kt | 6 | 0 |
| ui/screens/player/dialogs/PlayerSettingsSheetHost.kt | 6 | 0 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 5 | 0 |
| ui/components/VideoQuickActionsSheet.kt | 3 | 2 |
| data/model/MediaItemMappers.kt | 4 | 0 |
| ... 31 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **7** (37 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroTokenizer.kt | 8 | 9 |
| data/recommendation/NeuroDiscovery.kt | 3 | 3 |
| data/recommendation/FlowNeuroEngine.kt | 2 | 2 |
| data/recommendation/NeuroScoring.kt | 2 | 2 |
| data/recommendation/NeuroClusters.kt | 1 | 1 |
| data/recommendation/NeuroModels.kt | 2 | 0 |
| data/recommendation/NeuroVectorMath.kt | 1 | 1 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **31** (3459 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **24** (8405 lines).

## Build

- Modified upstream files: **1** (7 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 6 | 1 |

## Resources / strings

- Modified upstream files: **25** (116 changed lines). New files: **1** (21 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/main/res/values/strings.xml | 16 | 4 |
| app/src/main/AndroidManifest.xml | 9 | 1 |
| app/src/main/res/values-zh-rCN/strings.xml | 4 | 4 |
| app/src/main/res/values-ar/strings.xml | 2 | 2 |
| app/src/main/res/values-az/strings.xml | 2 | 2 |
| app/src/main/res/values-cs/strings.xml | 2 | 2 |
| app/src/main/res/values-de/strings.xml | 2 | 2 |
| app/src/main/res/values-es/strings.xml | 2 | 2 |
| app/src/main/res/values-fr/strings.xml | 2 | 2 |
| app/src/main/res/values-hi/strings.xml | 2 | 2 |
| app/src/main/res/values-in/strings.xml | 2 | 2 |
| app/src/main/res/values-it/strings.xml | 2 | 2 |
| app/src/main/res/values-ko/strings.xml | 2 | 2 |
| app/src/main/res/values-pl/strings.xml | 2 | 2 |
| app/src/main/res/values-pt-rBR/strings.xml | 2 | 2 |
| app/src/main/res/values-ru/strings.xml | 2 | 2 |
| app/src/main/res/values-si/strings.xml | 2 | 2 |
| app/src/main/res/values-tr/strings.xml | 2 | 2 |
| app/src/main/res/values-uk/strings.xml | 2 | 2 |
| app/src/main/res/values-vi/strings.xml | 2 | 2 |
| app/src/main/res/values-bs/strings.xml | 1 | 1 |
| app/src/main/res/values-et/strings.xml | 1 | 1 |
| app/src/main/res/values-pt/strings.xml | 1 | 1 |
| app/src/main/res/values-sr/strings.xml | 1 | 1 |
| app/src/main/res/values-zh-rTW/strings.xml | 1 | 1 |

## Tests

- Modified upstream files: **2** (16 changed lines). New files: **1** (148 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |

