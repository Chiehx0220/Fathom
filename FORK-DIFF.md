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

- Modified upstream files: **122** (2072 changed lines). New files: **57** (4967 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/local/BackupRepository.kt | 109 | 18 |
| player/datasource/YouTubeHttpDataSource.kt | 97 | 7 |
| ui/screens/home/chips/HomeChipFeeds.kt | 59 | 38 |
| ui/tv/components/TvNavRail.kt | 51 | 45 |
| data/innertube/RssSubscriptionService.kt | 72 | 3 |
| ui/screens/home/HomeViewModel.kt | 60 | 9 |
| ui/screens/home/FlowHeaderLogoIcon.kt | 60 | 7 |
| ui/tv/screens/TvSettingsScreen.kt | 38 | 25 |
| player/EnhancedPlayerManager.kt | 52 | 2 |
| data/video/downloader/resolve/DownloadStreamResolver.kt | 43 | 6 |
| ui/screens/home/HomeFeedSources.kt | 42 | 5 |
| ui/screens/categories/CategoriesScreen.kt | 34 | 6 |
| data/local/PlayerPreferences.kt | 39 | 0 |
| data/local/SubscriptionRecordCodec.kt | 24 | 15 |
| player/resolver/VideoPlaybackResolver.kt | 36 | 3 |
| ui/screens/player/VideoPlayerViewModel.kt | 31 | 7 |
| ui/screens/search/SearchScreen.kt | 38 | 0 |
| ui/screens/onboarding/ImportStep.kt | 25 | 6 |
| data/backup/NewPipeSubscriptionCodec.kt | 24 | 5 |
| data/local/ViewHistory.kt | 28 | 0 |
| data/video/VideoDownloadOptionsLoader.kt | 26 | 1 |
| ui/screens/channel/ChannelViewModel.kt | 23 | 3 |
| data/comments/CommentsPager.kt | 19 | 6 |
| ui/components/shared/card/VideoCardStacked.kt | 15 | 10 |
| ui/screens/player/PlaybackSessionApplier.kt | 25 | 0 |
| ui/screens/search/SearchViewModel.kt | 21 | 4 |
| data/paging/SearchPagingSource.kt | 18 | 6 |
| data/repository/YouTubeRepository.kt | 18 | 6 |
| data/video/VideoDownloadManager.kt | 23 | 1 |
| data/update/UpdateRepository.kt | 14 | 9 |
| data/local/dao/WatchHistoryDao.kt | 22 | 0 |
| data/subscriptions/SubscriptionFeedRepository.kt | 22 | 0 |
| ui/components/videoplayer/settings/PlayerSettingsMainPage.kt | 19 | 3 |
| utils/ShareVideo.kt | 14 | 7 |
| player/error/VideoErrorMapper.kt | 20 | 0 |
| ui/screens/player/content/PlayerErrorPanel.kt | 17 | 3 |
| data/local/entity/VideoEntity.kt | 10 | 9 |
| ui/FlowApp.kt | 8 | 10 |
| ui/components/shared/quickactions/QuickActionsViewModel.kt | 14 | 3 |
| ui/screens/channel/ChannelTabController.kt | 13 | 3 |
| ui/screens/player/effects/PlayerLoadEffects.kt | 14 | 2 |
| data/subscriptions/SubscriptionRefreshPlanner.kt | 15 | 0 |
| ui/screens/player/WatchSessionTracker.kt | 12 | 3 |
| ui/screens/player/dialogs/PlayerDialogsContainer.kt | 14 | 1 |
| ui/screens/player/stage/VideoStage.kt | 15 | 0 |
| data/update/GitHubRelease.kt | 7 | 7 |
| player/GlobalPlayerState.kt | 13 | 0 |
| player/stream/PlaybackLoadResolver.kt | 12 | 1 |
| ui/components/shared/quickactions/VideoQuickActionsSheet.kt | 11 | 2 |
| ui/screens/home/HomeScreen.kt | 11 | 2 |
| ui/FlowNavigation.kt | 7 | 5 |
| ui/screens/player/PlayerSecondaryMetadataLoader.kt | 9 | 3 |
| ui/startup/SplashThemes.kt | 0 | 12 |
| data/local/LikedVideosRepository.kt | 10 | 1 |
| MainActivity.kt | 9 | 1 |
| player/stream/InnerTubeVideoStreamExtractor.kt | 10 | 0 |
| player/stream/ResolvedStreamData.kt | 10 | 0 |
| ui/screens/channel/ChannelCommunityController.kt | 9 | 1 |
| ui/screens/playlists/PlaylistDetailViewModel.kt | 9 | 1 |
| player/stream/VideoCodecUtils.kt | 4 | 5 |
| ... 62 more, each small | | |

## FlowNeuro (Chinese text handling)

- Modified upstream files: **2** (6 changed lines). New files: **2** (747 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| data/recommendation/NeuroText.kt | 2 | 2 |
| data/recommendation/NeuroModels.kt | 2 | 0 |

## Bilibili (native client, mappers, player/paging glue)

- Modified upstream files: **0** (0 changed lines). New files: **63** (6892 lines).

## Local server (fork-only feature)

- Modified upstream files: **0** (0 changed lines). New files: **40** (7038 lines).

## Build

- Modified upstream files: **1** (15 changed lines). New files: **0** (0 lines).

| Modified upstream file | + | - |
|---|---:|---:|
| app/build.gradle.kts | 11 | 4 |

## Resources / strings

- Modified upstream files: **38** (759 changed lines). New files: **4** (76 lines).

| Modified upstream file | + | - |
|---|---:|---:|
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
| app/src/main/res/drawable/ic_launcher_expressive_mint_foreground.xml | 0 | 13 |
| app/src/main/res/drawable/ic_notification_logo.xml | 7 | 6 |
| app/src/main/res/drawable/ic_flow_logo.xml | 0 | 12 |
| app/src/main/res/values/colors.xml | 5 | 7 |
| ... 8 more, each small | | |

## Tests

- Modified upstream files: **17** (129 changed lines). New files: **7** (472 lines).

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
| app/src/test/java/io/github/aedev/flow/player/stream/PlaybackLoadResolverTest.kt | 3 | 0 |
| app/src/test/java/io/github/aedev/flow/data/innertube/RssSubscriptionServiceTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/components/shared/quickactions/QuickActionsMessagesTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/HomeFeedSourcesSeedTest.kt | 1 | 1 |
| app/src/test/java/io/github/aedev/flow/ui/screens/player/VideoPlayerViewModelHarness.kt | 2 | 0 |
| app/src/test/java/io/github/aedev/flow/ui/screens/home/chips/HomeChipFeedsTest.kt | 1 | 0 |

