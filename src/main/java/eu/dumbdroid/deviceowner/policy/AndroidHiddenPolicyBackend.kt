package eu.dumbdroid.deviceowner.policy

import android.app.admin.DevicePolicyIdentifiers
import android.app.admin.DevicePolicyManager
import android.app.admin.EnforcingAdmin
import android.app.admin.PackagePolicyKey
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

internal class AndroidHiddenPolicyBackend(private val context: Context) : HiddenPolicyBackend {
    private val manager = checkNotNull(context.getSystemService(DevicePolicyManager::class.java))
    private val user = Process.myUserHandle()

    private data class PolicyValues(
        val admins: Map<EnforcingAdmin, Boolean>,
        val resolved: Boolean?,
    )

    private fun readPolicy(packageName: String): PolicyValues {
        val state = checkNotNull(manager.devicePolicyState) { "Device policy state unavailable" }
        val key = PackagePolicyKey(DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY, packageName)
        val policy = state.getPoliciesForUser(user)[key] ?: return PolicyValues(emptyMap(), null)
        val admins = policy.policiesSetByAdmins.mapValues { (_, value) ->
            check(value is Boolean) { "Unexpected hidden policy type" }
            value
        }
        val resolved = policy.currentResolvedPolicy
        check(resolved == null || resolved is Boolean) { "Unexpected resolved hidden policy type" }
        return PolicyValues(admins, resolved as Boolean?)
    }

    override fun read(packageName: String): HiddenPolicySnapshot {
        val info = context.packageManager.getApplicationInfo(
            packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES,
        )
        check(info.flags and ApplicationInfo.FLAG_INSTALLED != 0) { "Package is not installed" }
        // Another controller can update its policy between binder calls. Retry reads once;
        // a changing snapshot must never be mistaken for verified enforcement.
        repeat(2) {
            val before = readPolicy(packageName)
            val actual = manager.isApplicationHidden(null, packageName)
            val after = readPolicy(packageName)
            if (before == after) {
                val own = after.admins.filterKeys {
                    it.packageName == context.packageName && it.userHandle == user
                }
                check(own.size <= 1) { "Ambiguous App Blocker policy identity" }
                return HiddenPolicySnapshot(
                    ownHidden = own.values.singleOrNull(),
                    resolvedHidden = after.resolved,
                    hiddenByOther = after.admins.any { (admin, hidden) ->
                        admin !in own && hidden
                    },
                    actualHidden = actual,
                )
            }
        }
        error("Hidden policy changed during verification")
    }

    override fun write(packageName: String, hidden: Boolean) {
        // No DeviceAdminReceiver, delegation, or role assignment: use this system app's identity.
        manager.setApplicationHidden(null, packageName, hidden)
    }
}
