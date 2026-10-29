package eu.dumbdroid.deviceowner.policy

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

private class AndroidInternetPolicyBackend(context: Context) : InternetPolicyBackend {
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

    override fun baseRestriction(key: String) =
        userManager.hasBaseUserRestriction(key, Process.myUserHandle())
    override fun effectiveRestriction(key: String) = userManager.hasUserRestriction(key)
    override fun setRestriction(key: String, enabled: Boolean) {
        userManager.setUserRestriction(key, enabled)
    }
}
