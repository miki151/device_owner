package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager.BlockState
import eu.dumbdroid.deviceowner.policy.PolicyChangeResult

internal fun BlockState.statusText(context: Context): String? {
    val state = snapshot
    val message = when {
        state == null -> R.string.app_policy_state_unknown
        !state.isConsistent -> R.string.app_policy_state_inconsistent
        requested && !state.actualHidden -> R.string.app_saved_block_inactive
        state.hiddenByOther -> R.string.app_blocked_by_other
        !requested && state.actualHidden -> R.string.app_hidden_elsewhere
        else -> return null
    }
    return context.getString(message)
}

internal fun PolicyChangeResult.showFailure(context: Context) {
    when (this) {
        PolicyChangeResult.VERIFICATION_FAILED -> AlertDialog.Builder(context)
            .setTitle(R.string.app_policy_verification_title)
            .setMessage(R.string.app_policy_verification_failed)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        PolicyChangeResult.SIDE_EFFECT_FAILED -> Toast.makeText(
            context, R.string.app_installation_controls_failed, Toast.LENGTH_LONG,
        ).show()
        else -> Toast.makeText(context, R.string.app_changes_failed, Toast.LENGTH_LONG).show()
    }
}
