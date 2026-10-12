package eu.dumbdroid.deviceowner.policy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.util.Log
import eu.dumbdroid.deviceowner.storage.PinStorage
import eu.dumbdroid.deviceowner.ui.PlayStoreBlockedActivity

class DeviceRestrictionManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val backend by lazy { AndroidHiddenPolicyBackend(context) }
    private val controller by lazy {
        HiddenPolicyController(backend) { pkg, requested, state, error ->
            Log.e(TAG, "Hidden policy failed: package=$pkg requested=$requested snapshot=$state", error)
        }
    }

    data class BlockState(
        val requested: Boolean,
        val snapshot: HiddenPolicySnapshot?,
    )

    fun getBlockState(packageName: String): BlockState {
        val snapshot = try {
            backend.read(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read hidden policy for $packageName", e)
            null
        }
        val stored = if (packageName == PLAY_STORE_PACKAGE) {
            PinStorage(context).isRestrictionEnabled()
        } else {
            getStoredBlockedApplications().contains(packageName)
        }
        return BlockState(
            isApplicationPermanentlyBlocked(packageName) || (snapshot?.ownHidden ?: stored),
            snapshot,
        )
    }

    data class ManagedApp(val packageName: String, val label: String)

    fun canManagePolicies(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            false
        } else {
            backend.read(context.packageName)
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Device policy access unavailable", e)
        false
    }

    @Synchronized
    fun setPlayStoreRestricted(restricted: Boolean): PolicyChangeResult {
        val result = setApplicationHidden(PLAY_STORE_PACKAGE, restricted)
        if (result.applied) {
            PinStorage(context).setRestrictionEnabled(restricted)
            try {
                setPlayStoreBlockerEnabled(restricted)
                setUnknownSourcesRestriction(restricted)
            } catch (e: Exception) {
                Log.e(TAG, "Play Store policy applied, but installation controls failed", e)
                return PolicyChangeResult.SIDE_EFFECT_FAILED
            }
        }
        return result
    }

    @Synchronized
    fun setApplicationBlocked(packageName: String, blocked: Boolean): PolicyChangeResult {
        if (packageName == context.packageName || packageName == PLAY_STORE_PACKAGE ||
            (!blocked && isApplicationPermanentlyBlocked(packageName))) {
            return PolicyChangeResult.FAILED
        }
        val result = setApplicationHidden(packageName, blocked)
        if (result.applied && !isApplicationPermanentlyBlocked(packageName)) {
            updateBlockedApplications(packageName, blocked)
        }
        return result
    }

    @Synchronized
    fun setApplicationPermanentlyBlocked(packageName: String): PolicyChangeResult {
        if (packageName == context.packageName || packageName == PLAY_STORE_PACKAGE) {
            return PolicyChangeResult.FAILED
        }
        val result = setApplicationHidden(packageName, true)
        if (result.applied) {
            addPermanentlyBlockedApplication(packageName)
            removeSoftBlockedApplication(packageName)
        }
        return result
    }

    fun isApplicationPermanentlyBlocked(packageName: String): Boolean {
        return getStoredPermanentlyBlockedApplications().contains(packageName)
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

    fun getInstalledApplication(packageName: String): ManagedApp? {
        return try {
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(INSTALLED_APPLICATION_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, INSTALLED_APPLICATION_FLAGS)
            }
            ManagedApp(
                packageName = applicationInfo.packageName,
                label = packageManager.getApplicationLabel(applicationInfo).toString(),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getApplicationIcon(packageName: String): Drawable {
        return try {
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(INSTALLED_APPLICATION_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, INSTALLED_APPLICATION_FLAGS)
            }
            applicationInfo.loadIcon(packageManager)
        } catch (_: PackageManager.NameNotFoundException) {
            packageManager.defaultActivityIcon
        }
    }

    private fun setUnknownSourcesRestriction(enabled: Boolean) {
        checkNotNull(userManager).setUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, enabled)
    }

    private fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyChangeResult {
        if (!canManagePolicies()) return PolicyChangeResult.FAILED
        return controller.setHidden(packageName, hidden)
    }

    private fun updateBlockedApplications(packageName: String, blocked: Boolean) {
        val blockedApps = getStoredSoftBlockedApplications()
        if (blocked) {
            blockedApps.add(packageName)
        } else if (!isApplicationPermanentlyBlocked(packageName)) {
            blockedApps.remove(packageName)
        }
        preferences.edit().putStringSet(KEY_BLOCKED_APPS, blockedApps).apply()
    }

    private fun addPermanentlyBlockedApplication(packageName: String) {
        val blockedApps = getStoredPermanentlyBlockedApplications()
        blockedApps.add(packageName)
        preferences.edit().putStringSet(KEY_PERMANENTLY_BLOCKED_APPS, blockedApps).apply()
    }

    private fun removeSoftBlockedApplication(packageName: String) {
        val blockedApps = getStoredSoftBlockedApplications()
        if (blockedApps.remove(packageName)) {
            preferences.edit().putStringSet(KEY_BLOCKED_APPS, blockedApps).apply()
        }
    }

    private fun getStoredBlockedApplications(): MutableSet<String> {
        val blocked = getStoredSoftBlockedApplications()
        blocked.addAll(getStoredPermanentlyBlockedApplications())
        return blocked
    }

    private fun getStoredSoftBlockedApplications(): MutableSet<String> {
        val stored = preferences.getStringSet(KEY_BLOCKED_APPS, emptySet())
        return stored?.toMutableSet() ?: mutableSetOf()
    }

    fun getPermanentlyBlockedApplications(): Set<String> {
        return getStoredPermanentlyBlockedApplications()
            .filterNot { it == PLAY_STORE_PACKAGE }
            .toSet()
    }

    private fun getStoredPermanentlyBlockedApplications(): MutableSet<String> {
        val stored = preferences.getStringSet(KEY_PERMANENTLY_BLOCKED_APPS, emptySet())
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
        private const val TAG = "AppBlockerPolicy"
        private const val INSTALLED_APPLICATION_FLAGS =
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        private const val PREF_NAME = "device_owner_restrictions"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_PERMANENTLY_BLOCKED_APPS = "permanently_blocked_apps"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
