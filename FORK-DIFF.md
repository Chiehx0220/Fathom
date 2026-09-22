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

- Modified upstream files: **160** (8672 changed lines). New files: **74** (10248 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| ui/screens/shorts/ShortsScreen.kt | 319 | 106 |
| ui/components/shorts/ShortsReelPage.kt | 0 | 406 |
| ui/components/shorts/ShortsSettingsSheet.kt | 0 | 406 |
| ui/screens/shorts/ShortsViewModel.kt | 212 | 191 |
| data/shorts/feed/ShortsFeedPager.kt | 0 | 370 |
| data/shorts/ShortsFeedRepository.kt | 0 | 270 |
| data/shorts/ShortsStreamResolver.kt | 0 | 245 |
| ui/components/shorts/ShortsReelEffects.kt | 0 | 217 |
| ui/components/shorts/ShortsMetadataOverlay.kt | 0 | 213 |
| data/model/ShortVideo.kt | 145 | 67 |
| ui/components/shorts/ShortsReelOverlays.kt | 0 | 212 |
| innertube/pages/reel/ReelOverlayPage.kt | 0 | 189 |
| ui/components/shorts/ShortsActionRail.kt | 0 | 189 |
| ui/screens/shorts/ShortsPagerEffects.kt | 0 | 179 |
| data/local/BackupRepository.kt | 122 | 34 |
| player/datasource/YouTubeHttpDataSource.kt | 148 | 7 |
| ui/components/VideoCard.kt | 141 | 11 |
| data/shorts/feed/ShortsFeedSources.kt | 0 | 151 |
| player/EnhancedPlayerManager.kt | 120 | 30 |
| ui/screens/player/PlaybackSessionApplier.kt | 144 | 0 |
| ui/FlowNavigation.kt | 103 | 32 |
| ui/components/FlowSplashScreen.kt | 28 | 97 |
| ui/components/shared/MediaShortCard.kt | 0 | 118 |
| ui/components/shorts/ShortsSettingsSheetState.kt | 0 | 115 |
| innertube/pages/reel/ReelLockupParser.kt | 0 | 112 |
| data/shorts/ShortsFeedOrdering.kt | 34 | 70 |
| player/stream/InnerTubeStreamBridge.kt | 34 | 70 |
| ui/components/shared/MediaShortsShelf.kt | 0 | 102 |
| ui/screens/home/HomeViewModel.kt | 67 | 34 |
| innertube/YouTube.kt | 52 | 42 |
| data/shorts/feed/ShortsFeedMixer.kt | 0 | 92 |
| ui/screens/channel/ChannelViewModel.kt | 83 | 2 |
| notification/SubscriptionCheckWorker.kt | 76 | 0 |
| ui/tv/screens/settings/TvAboutSettingsPane.kt | 42 | 31 |
| data/innertube/RssSubscriptionService.kt | 69 | 3 |
| ui/screens/search/SearchViewModel.kt | 65 | 4 |
| MainActivity.kt | 41 | 27 |
| innertube/pages/reel/ReelSequencePage.kt | 0 | 68 |
| ui/screens/home/HomePagination.kt | 68 | 0 |
| data/feed/FeedPrefetchQueue.kt | 0 | 67 |
| ui/components/shorts/ShortsTopBar.kt | 0 | 66 |
| data/shorts/ShortsStreamSelection.kt | 0 | 65 |
| ui/screens/settings/AboutScreen.kt | 33 | 32 |
| player/stream/PlaybackLoadResolver.kt | 62 | 1 |
| data/shorts/feed/ShortsFeedAssembly.kt | 0 | 60 |
| ui/components/shorts/ShortsOverlayStyle.kt | 0 | 59 |
| data/shorts/ShortsMetadataRepository.kt | 0 | 54 |
| data/shorts/feed/ShortsFeedQuotas.kt | 0 | 54 |
| innertube/InnerTube.kt | 22 | 31 |
| ui/components/channel/ChannelCards.kt | 50 | 0 |
| data/shorts/feed/ShortsFeedLane.kt | 0 | 46 |
| ui/screens/search/SearchScreen.kt | 39 | 4 |
| data/shorts/queue/SubscriptionDeepShortsLoader.kt | 21 | 21 |
| data/shorts/queue/ShortsQueueLoaders.kt | 22 | 18 |
| ui/screens/player/VideoPlayerViewModel.kt | 32 | 7 |
| data/shorts/queue/ShortsQueueController.kt | 9 | 29 |
| player/shorts/ShortsPlayerPool.kt | 8 | 30 |
| data/local/HomeFeedCacheRepository.kt | 2 | 35 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 36 | 0 |
| data/comments/CommentsPager.kt | 28 | 5 |
| ... 100 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **10** (257 changed lines). New files: **2** (172 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/FlowNeuroEngine.kt | 5 | 64 |
| data/recommendation/ShortsSeedSelector.kt | 0 | 65 |
| app/src/test/java/io/github/aedev/flow/data/recommendation/ShortsSeedSelectorTest.kt | 0 | 59 |
| data/recommendation/NeuroModels.kt | 2 | 23 |
| data/recommendation/NeuroTokenizer.kt | 8 | 9 |
| data/recommendation/NeuroScoring.kt | 3 | 6 |
| data/recommendation/NeuroDiscovery.kt | 3 | 3 |
| data/recommendation/NeuroStorage.kt | 0 | 3 |
| data/recommendation/NeuroClusters.kt | 1 | 1 |
| data/recommendation/NeuroVectorMath.kt | 1 | 1 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **45** (4662 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **29** (6914 lines).

## Build

- Modified upstream files: **1** (7 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 6 | 1 |

## Resources / strings

- Modified upstream files: **45** (1017 changed lines). New files: **3** (62 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/main/res/values-zh-rTW/strings.xml | 3 | 161 |
| app/src/main/res/values-in/strings.xml | 26 | 135 |
| app/src/main/res/values/strings.xml | 64 | 12 |
| app/src/main/AndroidManifest.xml | 41 | 1 |
| app/src/main/res/drawable/ic_splash_logo.xml | 32 | 10 |
| app/src/main/res/values-it/strings.xml | 4 | 34 |
| app/src/main/res/values-uk/strings.xml | 4 | 24 |
| app/src/main/res/values-fr/strings.xml | 4 | 23 |
| app/src/main/res/drawable-xhdpi/tv_banner.xml | 8 | 18 |
| app/src/main/res/drawable/ic_fg_ghost.xml | 9 | 15 |
| app/src/main/res/drawable/ic_fg_amoled.xml | 9 | 13 |
| app/src/main/res/drawable/ic_launcher_foreground.xml | 9 | 12 |
| app/src/main/res/drawable/ic_flow_badge_glyph.xml | 9 | 11 |
| app/src/main/res/drawable/ic_fg_monochrome.xml | 9 | 10 |
| app/src/main/res/drawable/ic_fg_flow_play.xml | 7 | 11 |
| app/src/main/res/drawable/ic_flow_badge_shape.xml | 6 | 12 |
| app/src/main/res/drawable/ic_launcher_dynamic_foreground.xml | 8 | 9 |
| app/src/main/res/drawable/splash_icon_ghost.xml | 0 | 17 |
| app/src/main/res/values-ar/strings.xml | 4 | 13 |
| app/src/main/res/values-zh-rCN/strings.xml | 6 | 9 |
| app/src/main/res/drawable/ic_notification_logo.xml | 7 | 6 |
| app/src/main/res/drawable/splash_icon_amoled.xml | 0 | 13 |
| app/src/main/res/drawable/splash_icon_monochrome.xml | 0 | 13 |
| app/src/main/res/drawable/ic_flow_logo.xml | 0 | 12 |
| app/src/main/res/values-az/strings.xml | 4 | 7 |
| app/src/main/res/values-es/strings.xml | 4 | 7 |
| app/src/main/res/values-ko/strings.xml | 4 | 7 |
| app/src/main/res/values-pl/strings.xml | 4 | 7 |
| app/src/main/res/values-pt-rBR/strings.xml | 4 | 7 |
| app/src/main/res/values-ru/strings.xml | 4 | 7 |
| ... 15 more, each small | | |

## Tests

- Modified upstream files: **25** (1764 changed lines). New files: **6** (394 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedBenchmark.kt | 0 | 223 |
| app/src/test/java/io/github/aedev/flow/innertube/pages/reel/ReelOverlayPageTest.kt | 0 | 192 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsStreamResolverTest.kt | 0 | 131 |
| app/src/test/java/io/github/aedev/flow/innertube/pages/reel/ReelSequencePageTest.kt | 0 | 120 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsTestFormats.kt | 0 | 113 |
| app/src/test/java/io/github/aedev/flow/innertube/pages/reel/ReelLockupParserTest.kt | 0 | 109 |
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedAssemblyTest.kt | 0 | 94 |
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedMixerTest.kt | 0 | 92 |
| app/src/test/java/io/github/aedev/flow/player/stream/ShortsItagSynthesisTest.kt | 0 | 85 |
| app/src/test/java/io/github/aedev/flow/ui/components/shorts/ShortsQualitySelectionTest.kt | 0 | 82 |
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedPagerTest.kt | 0 | 79 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsStreamSelectionTest.kt | 0 | 78 |
| app/src/test/java/io/github/aedev/flow/data/shorts/queue/ShortsQueueControllerTest.kt | 9 | 39 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsFeedOrderingTest.kt | 4 | 42 |
| app/src/test/java/io/github/aedev/flow/innertube/pages/reel/ReelFixture.kt | 0 | 42 |
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedBenchmarkTest.kt | 0 | 40 |
| app/src/test/java/io/github/aedev/flow/data/shorts/feed/ShortsFeedQuotasTest.kt | 0 | 40 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ShortsMetadataRepositoryTest.kt | 0 | 38 |
| app/src/test/java/io/github/aedev/flow/data/shorts/queue/SubscriptionDeepShortsLoaderTest.kt | 0 | 30 |
| app/src/test/java/io/github/aedev/flow/ui/components/shorts/ShortsOverlayStyleTest.kt | 0 | 29 |
| app/src/test/java/io/github/aedev/flow/innertube/pages/reel/ReelParamsTest.kt | 0 | 21 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ChannelReelIndexTest.kt | 2 | 2 |

