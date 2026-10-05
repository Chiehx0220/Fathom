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
5. `node scripts/fork-diff.js --check` compares the size of that surface with `scripts/fork-diff-budget.json`
   and fails when it grew; `--update-budget` lowers the budget after a cleanup.

## Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)

- Modified upstream files: **178** (3913 changed lines). New files: **56** (4899 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| player/PictureInPictureHelper.kt | 130 | 163 |
| player/EnhancedPlayerManager.kt | 128 | 46 |
| data/local/BackupRepository.kt | 109 | 18 |
| data/innertube/RssSubscriptionService.kt | 83 | 43 |
| ui/screens/channel/ChannelViewModel.kt | 88 | 21 |
| player/datasource/YouTubeHttpDataSource.kt | 97 | 7 |
| data/local/PlayerPreferences.kt | 39 | 63 |
| ui/screens/home/chips/HomeChipFeeds.kt | 59 | 38 |
| ui/tv/components/TvNavRail.kt | 51 | 45 |
| ui/screens/search/SearchScreen.kt | 68 | 20 |
| ui/screens/settings/quality/QualitySettingsScreen.kt | 4 | 67 |
| ui/screens/home/HomeViewModel.kt | 60 | 9 |
| ui/screens/home/FlowHeaderLogoIcon.kt | 60 | 7 |
| ui/tv/screens/TvSettingsScreen.kt | 38 | 25 |
| ui/screens/player/VideoPlayerViewModel.kt | 34 | 26 |
| ui/screens/shorts/ShortsPipEffects.kt | 12 | 48 |
| data/localmedia/LocalEmbeddedTags.kt | 2 | 51 |
| data/video/downloader/resolve/DownloadStreamResolver.kt | 43 | 6 |
| ui/screens/home/HomeFeedSources.kt | 42 | 5 |
| ui/utils/NetworkTransport.kt | 0 | 47 |
| ui/components/shared/quickactions/QuickActionsViewModel.kt | 14 | 31 |
| data/localmedia/MediaStoreThumbnails.kt | 3 | 40 |
| data/localmedia/LocalMediaReindex.kt | 0 | 41 |
| ui/screens/categories/CategoriesScreen.kt | 34 | 6 |
| ui/screens/shorts/ShortsPagerEffects.kt | 40 | 0 |
| data/local/SubscriptionRecordCodec.kt | 24 | 15 |
| player/resolver/VideoPlaybackResolver.kt | 36 | 3 |
| ui/screens/search/SearchViewModel.kt | 19 | 20 |
| data/local/SearchHistoryEntries.kt | 0 | 38 |
| data/subscriptions/SubscriptionFeedRepository.kt | 25 | 10 |
| ui/ChannelNavigation.kt | 22 | 11 |
| data/localmedia/LocalMediaStore.kt | 11 | 21 |
| ui/components/shared/card/VideoCardState.kt | 9 | 23 |
| player/PipActions.kt | 0 | 31 |
| ui/screens/onboarding/ImportStep.kt | 25 | 6 |
| data/local/SearchHistoryRepository.kt | 19 | 11 |
| ui/components/search/SearchFilterSummary.kt | 0 | 30 |
| utils/ThumbnailUrlResolver.kt | 6 | 24 |
| data/backup/NewPipeSubscriptionCodec.kt | 24 | 5 |
| data/local/ViewHistory.kt | 28 | 0 |
| data/video/VideoDownloadOptionsLoader.kt | 26 | 1 |
| service/Media3MusicService.kt | 0 | 27 |
| data/comments/CommentsPager.kt | 19 | 6 |
| ui/components/shared/card/VideoCardStacked.kt | 15 | 10 |
| ui/screens/player/PlaybackSessionApplier.kt | 25 | 0 |
| data/paging/SearchPagingSource.kt | 18 | 6 |
| data/repository/YouTubeRepository.kt | 18 | 6 |
| data/video/VideoDownloadManager.kt | 23 | 1 |
| ui/components/music/sheet/MusicQuickActionsSheet.kt | 3 | 21 |
| ui/screens/settings/quality/QualityOptions.kt | 4 | 20 |
| data/update/UpdateRepository.kt | 14 | 9 |
| ui/NavigationDestinations.kt | 19 | 4 |
| data/local/dao/WatchHistoryDao.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| data/subscriptions/ChannelUploadsClient.kt | 0 | 21 |
| player/MusicMutePause.kt | 0 | 21 |
| ui/FlowApp.kt | 11 | 10 |
| utils/ShareVideo.kt | 14 | 7 |
| player/error/VideoErrorMapper.kt | 20 | 0 |
| ui/screens/player/content/PlayerErrorPanel.kt | 17 | 3 |
| ... 118 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **2** (6 changed lines). New files: **2** (747 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroText.kt | 2 | 2 |
| data/recommendation/NeuroModels.kt | 2 | 0 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **61** (6803 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **40** (7038 lines).

## Build

- Modified upstream files: **1** (15 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 11 | 4 |

## Resources / strings

- Modified upstream files: **44** (2230 changed lines). New files: **4** (76 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/main/res/values-uk/strings.xml | 8 | 674 |
| app/src/main/res/values-in/strings.xml | 19 | 529 |
| app/src/main/res/values-ar/strings.xml | 4 | 147 |
| app/src/main/res/values-pt-rBR/strings.xml | 1 | 59 |
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
| app/src/main/res/values/strings.xml | 0 | 18 |
| app/src/main/res/values/themes.xml | 0 | 18 |
| ... 14 more, each small | | |

## Tests

- Modified upstream files: **36** (866 changed lines). New files: **6** (448 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/src/test/java/io/github/aedev/flow/data/local/SearchHistoryEntriesTest.kt | 0 | 85 |
| app/src/test/java/io/github/aedev/flow/ui/components/shared/quickactions/QuickActionsMessagesTest.kt | 2 | 65 |
| app/src/test/java/io/github/aedev/flow/utils/ThumbnailQualityTiersTest.kt | 0 | 66 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/ChannelUploadsClientTest.kt | 0 | 57 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionReelVerdictsTest.kt | 0 | 55 |
| app/src/test/java/io/github/aedev/flow/data/localmedia/LocalLibraryLoaderTest.kt | 0 | 45 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelEntryPointsTest.kt | 0 | 38 |
| app/src/test/java/io/github/aedev/flow/player/PipActionsTest.kt | 0 | 37 |
| app/src/test/java/io/github/aedev/flow/ui/screens/search/SearchViewModelTest.kt | 1 | 36 |
| app/src/test/java/io/github/aedev/flow/ui/screens/channel/ChannelLoadGuardTest.kt | 0 | 35 |
| app/src/test/java/io/github/aedev/flow/ui/components/search/SearchFilterSummaryTest.kt | 0 | 32 |
| app/src/test/java/io/github/aedev/flow/data/localmedia/LocalMediaReindexTest.kt | 0 | 28 |
| app/src/test/java/io/github/aedev/flow/player/MusicMutePauseTest.kt | 0 | 28 |
| app/src/test/java/io/github/aedev/flow/data/update/GitHubReleaseTest.kt | 13 | 13 |
| app/src/test/java/io/github/aedev/flow/ui/screens/channel/ChannelScreenTabTest.kt | 5 | 21 |
| app/src/test/java/io/github/aedev/flow/data/innertube/RssSubscriptionServiceTest.kt | 4 | 18 |
| app/src/test/java/io/github/aedev/flow/data/localmedia/MediaStoreThumbnailsTest.kt | 0 | 20 |
| app/src/test/java/io/github/aedev/flow/data/localmedia/LocalEmbeddedTextTest.kt | 0 | 16 |
| app/src/test/java/io/github/aedev/flow/platform/LauncherAliasManifestTest.kt | 13 | 2 |
| app/src/test/java/io/github/aedev/flow/ui/components/shorts/ShortsQualitySelectionTest.kt | 0 | 15 |
| app/src/test/java/io/github/aedev/flow/utils/ShareVideoTest.kt | 12 | 3 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionFeedWriteTest.kt | 0 | 14 |
| app/src/test/java/io/github/aedev/flow/data/subscriptions/SubscriptionRefreshPlannerTest.kt | 12 | 0 |
| app/src/test/java/io/github/aedev/flow/data/shorts/ChannelReelIndexTest.kt | 6 | 5 |
| app/src/test/java/io/github/aedev/flow/data/paging/SearchPagingSourceTest.kt | 8 | 1 |
| app/src/test/java/io/github/aedev/flow/data/repository/ChannelMetadataNeedTest.kt | 9 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/crash/CrashSummaryTest.kt | 0 | 9 |
| app/src/test/java/io/github/aedev/flow/ui/screens/settings/QualityAndPlaybackOptionsTest.kt | 9 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/effects/WatchHistoryEntryTest.kt | 7 | 0 |
| app/src/test/java/io/github/aedev/flow/player/stream/VideoCodecUtilsTest.kt | 6 | 0 |
| ... 6 more, each small | | |

