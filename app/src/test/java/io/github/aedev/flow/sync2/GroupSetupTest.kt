package io.github.aedev.flow.sync2

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync2.crypto.recoveryKeys
import io.github.aedev.flow.sync2.crypto.recoverySecret
import io.github.aedev.flow.sync2.engine.Session
import io.github.aedev.flow.sync2.linking.DeviceMeta
import io.github.aedev.flow.sync2.linking.createGroup
import io.github.aedev.flow.sync2.roster.Role
import io.github.aedev.flow.sync2.store.ServerStore
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class GroupSetupTest {
    @Test
    fun creating_a_group_claims_the_server_and_the_engine_opens_it() =
        runTest {
            val instance = "01".repeat(16)
            var claimed: JsonObject? = null
            StandInServer {
                val genesis = claimed?.get("entry")?.jsonObject
                when (val path = request.path()) {
                    "/v1/claim" -> {
                        claimed = Json.parseToJsonElement(receiveText()).jsonObject
                        respondJson(201, "{}")
                    }

                    "/v1/auth/challenge" -> {
                        respondJson(
                            200,
                            """{"challenge":"AAAA","instance":"$instance","group":"${genesis?.get(
                                "group",
                            )?.jsonPrimitive?.content}","time":0}""",
                        )
                    }

                    "/v1/auth/token" -> {
                        respondJson(200, """{"token":"t","expires":${Long.MAX_VALUE / 2}}""")
                    }

                    "/v1/roster" -> {
                        val after = request.queryParameters["after"]?.toLong() ?: -1
                        respondJson(200, if (after < 0) """{"entries":[$genesis]}""" else """{"entries":[]}""")
                    }

                    else -> {
                        val did = path.substringAfterLast('/').removeSuffix(".k")
                        val wrap =
                            checkNotNull(claimed)
                                .getValue("wraps")
                                .jsonObject
                                .getValue(did)
                                .jsonPrimitive.content
                        respondBytes(SyncBytes.b64urlDecode(wrap))
                    }
                }
            }.use { server ->
                val db = emptyDb()
                val device = newMember()
                val words = createGroup(db, server.url, "admin", device, DeviceMeta("app-a", "Pixel", "android"), LINK)

                val session = Session.open(db, ServerStore.connect(server.url, device, instance, null), device)
                val recoveryDid = SyncBytes.toHex(recoveryKeys(checkNotNull(recoverySecret(words))).did)
                assertEquals(
                    Role.RECOVERY,
                    session.state.members
                        .getValue(recoveryDid)
                        .role,
                )
                assertEquals(
                    Role.DEVICE,
                    session.state.members
                        .getValue(SyncBytes.toHex(device.did))
                        .role,
                )
                assertEquals(LINK, db.groups().group()?.link)
                assertFalse("the creator still has to confirm its words", db.groups().group()!!.recoverySaved)
                assertEquals(instance, db.groups().group()?.instance)
            }
        }
}
