package io.github.aedev.flow.data.profile

import android.content.Context
import io.github.aedev.flow.R

/**
 * Sync belongs to Default: its ledgers, CRDTs and device id describe that one profile's data, so another profile
 * syncing under the same device id would corrupt both.
 */
object SyncProfile {
    fun syncsHere(context: Context): Boolean = ActiveProfile.get(context).isDefault

    /** The name to show for the profile that syncs. */
    fun name(
        state: ProfileRegistryState,
        context: Context,
    ): String =
        state.profiles.firstOrNull { it.id == state.defaultId }?.displayName(context) ?: context.getString(R.string.profile_default_name)
}
