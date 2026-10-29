package io.github.aedev.flow.sync2

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.RunningProfile
import io.github.aedev.flow.sync.apply.CollectionLedger
import io.github.aedev.flow.sync.apply.LedgerEntry
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore
import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync2.data.SentRecord
import io.github.aedev.flow.sync2.service.LinkedReset
import io.github.aedev.flow.sync2.store.StorePaths
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A restore or import while linked starts both syncs over instead of sending every missing record as a deletion. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LinkedResetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun workManager() {
        if (!WorkManager.isInitialized()) WorkManager.initialize(context, Configuration.Builder().build())
    }

    @Test
    fun data_replaced_in_another_profile_changes_nothing_default_syncs() =
        runTest {
            val g = LinkedGroup(this).apply { create() }
            val db = g.deviceDb()
            mockkObject(ActiveProfile)
            try {
                every { ActiveProfile.get(any()) } returns RunningProfile(id = "b", isDefault = false)
                LinkedReset(context, db, heldStore(db), SyncChangeLedgerStore(context)).dataReplaced()
            } finally {
                unmockkObject(ActiveProfile)
            }
            assertTrue(db.groups().group() != null)
        }

    @Test
    fun replaced_data_leaves_the_group_and_forgets_what_was_sent_and_held() =
        runTest {
            val g = LinkedGroup(this).apply { create() }
            val db = g.deviceDb()
            val local = heldStore(db)
            val path = StorePaths.log(SyncBytes.toHex(g.a.did), 1)
            local.addEntries(g.store.roster)
            local.putObject(path, byteArrayOf(1))
            db.sent().upsert(listOf(SentRecord(SyncCollection.WATCH_HISTORY, "aaaaaaaaaaa", "hash", 0)))
            val ledger = SyncChangeLedgerStore(context)
            ledger.update(SyncCollection.WATCH_HISTORY) { CollectionLedger(true, mapOf("aaaaaaaaaaa" to LedgerEntry(hash = 1, atMs = 1))) }

            LinkedReset(context, db, local, ledger).dataReplaced()

            assertNull(db.groups().group())
            assertTrue(db.sent().forCollection(SyncCollection.WATCH_HISTORY).isEmpty())
            assertTrue(local.entries().isEmpty())
            assertNull(local.getObject(path))
            assertEquals(CollectionLedger(), ledger.load(SyncCollection.WATCH_HISTORY))
        }
}
