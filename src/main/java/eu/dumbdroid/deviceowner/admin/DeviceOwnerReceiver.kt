package eu.dumbdroid.deviceowner.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import eu.dumbdroid.deviceowner.R

/**
 * Device admin receiver that acts as the entry point for device owner policies.
 */
class DeviceOwnerReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, R.string.device_owner_enabled, Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_owner_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, R.string.device_owner_disabled, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, DeviceOwnerReceiver::class.java)

        fun getDevicePolicyManager(context: Context): DevicePolicyManager =
            checkNotNull(context.getSystemService(DevicePolicyManager::class.java)) {
                "DevicePolicyManager service is required"
            }
    }
}
