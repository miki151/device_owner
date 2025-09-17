package eu.dumbdroid.deviceowner.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import eu.dumbdroid.deviceowner.admin.DeviceOwnerReceiver
import eu.dumbdroid.deviceowner.ui.PlayStoreBlockedActivity

/**
 * Applies Play Store blocking policies when the device is managed by this device owner.
 */
class PlayStoreRestrictionManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        DeviceOwnerReceiver.getDevicePolicyManager(context)
    private val adminComponent: ComponentName = DeviceOwnerReceiver.getComponentName(context)
    private val packageManager: PackageManager = context.packageManager
    private val blockerComponent: ComponentName = ComponentName(context, PlayStoreBlockedActivity::class.java)

    fun isDeviceOwner(): Boolean =
        devicePolicyManager.isDeviceOwnerApp(context.packageName)

    fun setPlayStoreRestricted(restricted: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Attempted to change Play Store restriction without device owner privileges")
            return false
        }
        if (restricted) {
            enableBlockingComponent()
            registerPersistentHandlers()
        } else {
            clearPersistentHandlers()
            disableBlockingComponent()
        }
        return true
    }

    private fun enableBlockingComponent() {
        packageManager.setComponentEnabledSetting(
            blockerComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun disableBlockingComponent() {
        packageManager.setComponentEnabledSetting(
            blockerComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun registerPersistentHandlers() {
        clearPersistentHandlers()

        val mainFilter = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MARKET)
            addCategory(Intent.CATEGORY_DEFAULT)
        }.toIntentFilter()

        val marketSchemeFilter = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = android.net.Uri.parse("market://details")
        }.toIntentFilter()

        val httpsFilter = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = android.net.Uri.parse("https://play.google.com/store/apps")
        }.toIntentFilter()

        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent,
            mainFilter,
            blockerComponent
        )

        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent,
            marketSchemeFilter,
            blockerComponent
        )

        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent,
            httpsFilter,
            blockerComponent
        )
    }

    private fun clearPersistentHandlers() {
        devicePolicyManager.clearPackagePersistentPreferredActivities(
            adminComponent,
            PLAY_STORE_PACKAGE
        )
    }

    fun isPlayStoreRestricted(): Boolean {
        return try {
            val enabledSetting = packageManager.getComponentEnabledSetting(blockerComponent)
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    companion object {
        private const val TAG = "PlayStoreRestriction"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        private fun Intent.toIntentFilter(): android.content.IntentFilter {
            val filter = android.content.IntentFilter(action)
            categories?.forEach { category -> filter.addCategory(category) }
            data?.let { uri ->
                uri.scheme?.let { filter.addDataScheme(it) }
                uri.host?.let { filter.addDataAuthority(it, null) }
                if (uri.scheme == "https" || uri.scheme == "http") {
                    filter.addDataPath(uri.path ?: "", android.content.IntentFilter.MATCH_PREFIX)
                }
            }
            return filter
        }
    }
}
