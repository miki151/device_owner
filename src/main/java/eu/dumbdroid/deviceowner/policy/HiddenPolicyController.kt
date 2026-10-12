package eu.dumbdroid.deviceowner.policy

/** The policy entry belongs to this app; actualHidden is the package's effective state. */
data class HiddenPolicySnapshot(
    val ownHidden: Boolean?,
    val resolvedHidden: Boolean?,
    val hiddenByOther: Boolean,
    val actualHidden: Boolean,
) {
    // A legacy package flag with no policy is not an enforcement failure.
    val isConsistent: Boolean get() = resolvedHidden == null || resolvedHidden == actualHidden

    fun confirms(requested: Boolean): Boolean =
        ownHidden == requested && resolvedHidden != null && isConsistent
}

enum class PolicyChangeResult {
    APPLIED,
    BLOCKED_BY_OTHER,
    FAILED,
    VERIFICATION_FAILED,
    SIDE_EFFECT_FAILED;

    val applied: Boolean get() = this == APPLIED || this == BLOCKED_BY_OTHER
}

/** All writes in this interface must go through DevicePolicyManager, never PackageManager. */
interface HiddenPolicyBackend {
    fun read(packageName: String): HiddenPolicySnapshot
    fun write(packageName: String, hidden: Boolean)
}

/** Bounded, synchronous verification and recovery. No boot receiver or background enforcement. */
class HiddenPolicyController(
    private val backend: HiddenPolicyBackend,
    private val reportFailure: (String, Boolean, HiddenPolicySnapshot?, Exception) -> Unit,
) {
    @Synchronized
    fun setHidden(packageName: String, hidden: Boolean): PolicyChangeResult {
        val before = try {
            backend.read(packageName)
        } catch (e: Exception) {
            reportFailure(packageName, hidden, null, e)
            return PolicyChangeResult.VERIFICATION_FAILED
        }
        try {
            // The engine skips an unchanged false policy, even if a legacy writer hid the app.
            // Adopt only this edited block first, so the subsequent false request is a transition.
            if (!hidden && before.actualHidden && before.resolvedHidden == false) {
                backend.write(packageName, true)
                check(backend.read(packageName).confirms(true)) { "Legacy block adoption failed" }
            }
            backend.write(packageName, hidden)
            // The setter's Boolean describes the effective state, not whether our policy was saved.
            val after = backend.read(packageName)
            check(after.confirms(hidden)) { "Policy write was not recorded or enforced" }
            return if (!hidden && after.hiddenByOther && after.actualHidden) {
                PolicyChangeResult.BLOCKED_BY_OTHER
            } else {
                PolicyChangeResult.APPLIED
            }
        } catch (e: Exception) {
            var after = readAfterFailure(packageName, hidden)
            reportFailure(packageName, hidden, after, e)
            if (after == null || !after.isConsistent) {
                // Preserve the pre-operation package state. For an adopted legacy block this
                // retains our true policy if the requested unhide could not be verified.
                try {
                    backend.write(packageName, before.actualHidden)
                } catch (recoveryError: Exception) {
                    reportFailure(packageName, hidden, after, recoveryError)
                }
                after = readAfterFailure(packageName, hidden)
            }
            return if (after != null && after.isConsistent) {
                PolicyChangeResult.FAILED
            } else {
                PolicyChangeResult.VERIFICATION_FAILED
            }
        }
    }

    private fun readAfterFailure(packageName: String, hidden: Boolean): HiddenPolicySnapshot? =
        try {
            backend.read(packageName)
        } catch (e: Exception) {
            reportFailure(packageName, hidden, null, e)
            null
        }
}
