package eu.dumbdroid.deviceowner.policy

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.util.Log

class InternetRestrictionManager(context: Context) : InternetPolicyController(
    AndroidInternetPolicyBackend(context),
    // Private DNS is device-global; configure it only from the system user.
    isSupportedUser = Process.myUserHandle().identifier == 0,
    onFailure = { Log.e("AppBlockerInternet", "Cannot read or apply internet controls", it) },
)

// Android U accepts null admins for permission-based callers despite its API annotations.
@Suppress("NULL_FOR_NONNULL_TYPE")
private class AndroidInternetPolicyBackend(context: Context) : InternetPolicyBackend {
    private val manager = checkNotNull(context.getSystemService(DevicePolicyManager::class.java))
    private val resolver = context.contentResolver
    private val userManager = checkNotNull(context.getSystemService(UserManager::class.java))
    private val preferences = context.getSharedPreferences("internet_restrictions", Context.MODE_PRIVATE)

    override var dnsSession: DnsSession?
        get() {
            val hostname = preferences.getString("hostname", null) ?: return null
            return DnsSession(PrivateDnsConfig(preferences.getString("previous_mode", null),
                preferences.getString("previous_hostname", null)), hostname)
        }
        set(session) {
            val edit = preferences.edit()
            if (session == null) edit.clear()
            else edit.putString("hostname", session.hostname)
                .putString("previous_mode", session.previous.mode)
                .putString("previous_hostname", session.previous.hostname)
            check(edit.commit()) { "Could not save DNS restoration state" }
        }

    override fun readDns() = PrivateDnsConfig(
        Settings.Global.getString(resolver, Settings.Global.PRIVATE_DNS_MODE),
        Settings.Global.getString(resolver, Settings.Global.PRIVATE_DNS_SPECIFIER),
    )

    override fun writeDns(config: PrivateDnsConfig) {
        check(Settings.Global.putString(resolver, Settings.Global.PRIVATE_DNS_SPECIFIER, config.hostname))
        check(Settings.Global.putString(resolver, Settings.Global.PRIVATE_DNS_MODE, config.mode))
    }

    override fun effectiveRestriction(key: String) = userManager.hasUserRestriction(key)
    override fun ownRestriction(key: String): Boolean =
        (if (key == InternetPolicyController.DNS_RESTRICTION) manager.userRestrictionsGlobally
        else manager.getUserRestrictions(null)).getBoolean(key)

    override fun setRestriction(key: String, enabled: Boolean) {
        // Android U permission-based policies use this package's identity, without enrollment.
        // DNS is GLOBAL_ONLY. Tethering permits local policy, preserving its old user scope.
        if (!enabled) manager.clearUserRestriction(null, key)
        else if (key == InternetPolicyController.DNS_RESTRICTION) {
            manager.addUserRestrictionGlobally(key)
        } else manager.addUserRestriction(null, key)
    }
}
