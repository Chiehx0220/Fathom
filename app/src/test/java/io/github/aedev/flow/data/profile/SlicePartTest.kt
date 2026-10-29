package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import io.github.aedev.flow.data.local.BackupData
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.ContentPreferencesBackup
import io.github.aedev.flow.data.local.SettingsBackup
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import org.junit.Test

class SlicePartTest {
    private val full =
        BackupData(
            viewHistory =
                listOf(
                    VideoHistoryEntry(
                        videoId = "v",
                        position = 1,
                        duration = 2,
                        timestamp = 3,
                        title = "t",
                        thumbnailUrl = "",
                        channelName = "c",
                        channelId = "UCc",
                    ),
                ),
            subscriptions = listOf(ChannelSubscription(channelId = "UCa", channelName = "A", channelThumbnail = "")),
            subscriptionGroups = listOf(SubscriptionGroupEntity(name = "Group", channelIds = "UCa")),
            contentPreferences = ContentPreferencesBackup(blockedChannels = setOf("UCx")),
            settings =
                SettingsBackup(
                    strings = mapOf("app_icon_suffix" to "Red", "theme_mode" to "DARK", "upcoming_video_reminders" to "[]"),
                    booleans = mapOf("onboarding_done" to true, "deep_flow_active" to true, "autoplay_enabled" to false),
                    longs = mapOf("deep_flow_activated_at" to 5L),
                    stringSets =
                        mapOf(
                            "hidden_library_sections" to setOf("notes"),
                            "upcoming_video_reminder_ids" to setOf("v1"),
                        ),
                ),
        )

    @Test
    fun `a seed carries exactly the ticked parts, for every combination`() {
        val all = SlicePart.entries
        (0 until (1 shl all.size)).forEach { mask ->
            val parts = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            val seed = full.seedFor(parts)

            assertThat(seed.viewHistory).isNull()
            assertThat(seed.playlists).isNull()
            assertThat(seed.likedVideos).isNull()
            assertThat(seed.notes).isNull()
            assertThat(seed.searchHistory).isNull()
            assertThat(seed.subscriptions != null).isEqualTo(SlicePart.SUBSCRIPTIONS in parts)
            assertThat(seed.subscriptionGroups != null).isEqualTo(SlicePart.SUBSCRIPTIONS in parts)
            assertThat(seed.contentPreferences != null).isEqualTo(SlicePart.RECOMMENDATIONS in parts)
            assertThat(seed.settings!!.strings.containsKey("theme_mode")).isEqualTo(SlicePart.SETTINGS in parts)
            assertThat(seed.settings!!.stringSets.containsKey("hidden_library_sections")).isEqualTo(SlicePart.SETTINGS in parts)
            assertThat(seed.settings!!.booleans["onboarding_done"]).isEqualTo(SlicePart.RECOMMENDATIONS in parts)
        }
    }

    @Test
    fun `copied settings leave behind what the profile was in the middle of`() {
        val settings = full.seedFor(setOf(SlicePart.SETTINGS)).settings!!

        assertThat(settings.strings).containsExactly("theme_mode", "DARK")
        assertThat(settings.booleans).containsExactly("autoplay_enabled", false, "onboarding_done", false)
        assertThat(settings.longs).isEmpty()
        assertThat(settings.stringSets).containsExactly("hidden_library_sections", setOf("notes"))
    }

    @Test
    fun `string sets survive the backup file, and an older file without them reads as none`() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        val restored = gson.fromJson(gson.toJson(full.settings), SettingsBackup::class.java)
        val older = gson.fromJson("""{"strings":{"theme_mode":"DARK"}}""", SettingsBackup::class.java)

        assertThat(restored.stringSets).isEqualTo(full.settings!!.stringSets)
        assertThat(older.stringSets).isEmpty()
    }
}
