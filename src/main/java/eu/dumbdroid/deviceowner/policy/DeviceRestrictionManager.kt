package eu.dumbdroid.deviceowner.policy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import eu.dumbdroid.deviceowner.ui.PlayStoreBlockedActivity

class DeviceRestrictionManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    data class ManagedApp(val packageName: String, val label: String)

    fun isDeviceOwner(): Boolean = canModifyHiddenState()

    fun setPlayStoreRestricted(restricted: Boolean): Boolean {
        if (!canModifyHiddenState()) {
            return false
        }
        val success = setApplicationHidden(PLAY_STORE_PACKAGE, restricted)
        if (success) {
            setPlayStoreBlockerEnabled(restricted)
            setUnknownSourcesRestriction(restricted)
        }
        return success
    }

    fun setApplicationBlocked(packageName: String, blocked: Boolean): Boolean {
        if (!canModifyHiddenState()) {
            return false
        }
        if (packageName == context.packageName) {
            return false
        }
        val success = setApplicationHidden(packageName, blocked)
        if (success) {
            updateBlockedApplications(packageName, blocked)
        }
        return success
    }

    fun isApplicationBlocked(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageManager.getApplicationHiddenSettingAsUser(
                    packageName,
                    Process.myUserHandle(),
                )
            } else {
                getStoredBlockedApplications().contains(packageName)
            }
        } catch (securityException: SecurityException) {
            getStoredBlockedApplications().contains(packageName)
        } catch (notFound: PackageManager.NameNotFoundException) {
            false
        } catch (exception: Exception) {
            getStoredBlockedApplications().contains(packageName)
        }
    }

private fun declaresLauncherActivity(pkg: String): Boolean {

val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
               PackageManager.MATCH_DISABLED_COMPONENTS or
               PackageManager.MATCH_DIRECT_BOOT_AWARE or
               PackageManager.MATCH_DIRECT_BOOT_UNAWARE
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
    val results = packageManager.queryIntentActivities(launcher, flags)
    return results.isNotEmpty()
}


    fun getManageableApplications(): List<ManagedApp> {
/*        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = queryLauncherActivities(intent)*/
	val appInfos = packageManager.getInstalledApplications(
	    PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        val seenPackages = mutableSetOf<String>()
        val apps = mutableListOf<ManagedApp>()
        appInfos.forEach { appInfo ->
            val packageName = appInfo.packageName
	    if (!declaresLauncherActivity(packageName))
                return@forEach
            if (
                packageName == context.packageName ||
                packageName == PLAY_STORE_PACKAGE ||
                packageName == SETTINGS_PACKAGE
            ) {
                return@forEach
            }
            if (!seenPackages.add(packageName)) {
                return@forEach
            }
            val label = packageManager.getApplicationLabel(appInfo).toString()
//resolveInfo.loadLabel(packageManager)?.toString() ?: packageName
            apps += ManagedApp(packageName = packageName, label = label)
        }
        return apps
    }

    private fun setUnknownSourcesRestriction(enabled: Boolean) {
        val manager = userManager ?: return
        try {
            manager.setUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, enabled)
        } catch (_: SecurityException) {
            // Ignore if the platform rejects the request; suspension still applies.
        }
    }

    private fun setApplicationHidden(
        packageName: String,
        hidden: Boolean,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        return try {
            packageManager.setApplicationHiddenSettingAsUser(
                packageName,
                hidden,
                Process.myUserHandle(),
            )
        } catch (exception: Exception) {
            false
        }
    }

    private fun updateBlockedApplications(packageName: String, blocked: Boolean) {
        val blockedApps = getStoredBlockedApplications()
        if (blocked) {
            blockedApps.add(packageName)
        } else {
            blockedApps.remove(packageName)
        }
        preferences.edit().putStringSet(KEY_BLOCKED_APPS, blockedApps).apply()
    }

    private fun getStoredBlockedApplications(): MutableSet<String> {
        val stored = preferences.getStringSet(KEY_BLOCKED_APPS, emptySet())
        return stored?.toMutableSet() ?: mutableSetOf()
    }

    private fun setPlayStoreBlockerEnabled(enabled: Boolean) {
        val componentName = ComponentName(context, PlayStoreBlockedActivity::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun canModifyHiddenState(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        return try {
            packageManager.getApplicationHiddenSettingAsUser(
                context.packageName,
                Process.myUserHandle(),
            )
            true
        } catch (exception: Exception) {
	    Log.e("Dumbdroid admin", "Can't modify hidden state", exception)
            false
        }
    }

    private fun queryLauncherActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }

    companion object {
        private const val PREF_NAME = "device_owner_restrictions"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
