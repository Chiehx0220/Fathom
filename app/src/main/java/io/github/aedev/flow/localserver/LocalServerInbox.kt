package io.github.aedev.flow.localserver

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.content.InfoItem
import io.github.aedev.flow.data.local.NotificationRepository
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.data.model.toUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalServerInboxEntryPoint {
    fun notificationRepository(): NotificationRepository

    fun subscriptionGroupDao(): SubscriptionGroupDao
}

private fun Context.inbox() = EntryPointAccessors.fromApplication(this, LocalServerInboxEntryPoint::class.java)

@Serializable
internal class InboxItem(
    val id: Int,
    val isNew: Boolean,
    val timestamp: Long,
    val video: VideoCard,
)

@Serializable
internal class InboxDto(
    val unread: Int,
    val items: List<InboxItem>,
)

/** `count`: unread only; else list (unread flagged new) and mark read; `delete`, `clear` first. */
internal fun RequestContext.handleApiNotifications(params: Query): InboxDto {
    val repository = dbHelper.appContext.inbox().notificationRepository()
    params["delete"]?.toIntOrNull()?.let { id ->
        runBlocking {
            repository.allNotifications
                .first()
                .firstOrNull { it.id == id }
                ?.let { repository.deleteNotification(it) }
        }
    }
    if (params.bool("clear") == true) runBlocking { repository.clearAll() }
    val rows = runBlocking { repository.allNotifications.first() }
    val unread = rows.count { !it.isRead }
    if (params.bool("count") == true) return InboxDto(unread, emptyList())
    val items = rows.filter { it.videoId.isNotBlank() }.map { it.toItem() }
    if (unread > 0) runBlocking { repository.markAllAsRead() }
    return InboxDto(unread, items)
}

private fun NotificationEntity.toItem(): InboxItem {
    val url = videoIdToUrl(videoId)
    return InboxItem(
        id = id,
        isNew = !isRead,
        timestamp = timestamp,
        video =
            VideoCard(
                id = LocalServerMedia.getVideoId(videoId),
                url = url,
                serviceId = LocalServerMedia.serviceIdOfUrl(url),
                title = title,
                channelName = channelName,
                channelId = "",
                thumbnailUrl = WebFormat.getThumbnailUrl(thumbnailUrl),
                channelThumbnailUrl = "",
                duration = 0,
                viewCount = -1,
                uploadDate = "",
                isLive = false,
                isShort = false,
            ),
    )
}

@Serializable
internal class GroupDto(
    val name: String,
    val channels: List<String>,
)

@Serializable
internal class GroupsDto(
    val groups: List<GroupDto>,
)

/** Groups with their still-subscribed channel URLs. */
internal fun RequestContext.handleApiGroups(): GroupsDto {
    val groups =
        runBlocking {
            dbHelper.appContext
                .inbox()
                .subscriptionGroupDao()
                .getAllGroupsOnce()
        }
    val urlById = dbHelper.subscriptions().associateBy({ channelUrlToId(it.url) }, { it.url })
    return GroupsDto(
        groups.map { entity ->
            GroupDto(entity.name, entity.toUiModel().channelIds.mapNotNull { urlById[it] })
        },
    )
}

/** Subscriptions of group [name]; all if null/unknown. */
internal fun RequestContext.groupSubscriptions(name: String?): List<InfoItem> {
    val all = dbHelper.subscriptions()
    if (name.isNullOrEmpty()) return all
    val urls =
        handleApiGroups()
            .groups
            .firstOrNull { it.name == name }
            ?.channels
            ?.toSet() ?: return all
    return all.filter { it.url in urls }
}
