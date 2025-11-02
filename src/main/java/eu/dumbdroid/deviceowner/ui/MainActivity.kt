package eu.dumbdroid.deviceowner.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import eu.dumbdroid.deviceowner.storage.PinStorage

class MainActivity : AppCompatActivity(),
    SetupPinFragment.Callback,
    PinEntryFragment.Callback,
    RestrictionFragment.Callback,
    ChangePinFragment.Callback {

    private lateinit var pinStorage: PinStorage
    private lateinit var restrictionManager: DeviceRestrictionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pinStorage = PinStorage(this)
        restrictionManager = DeviceRestrictionManager(this)

        if (savedInstanceState == null) {
            showInitialScreen()
        }
    }

    private fun showInitialScreen() {
        if (!pinStorage.isSetupComplete()) {
            showSetupPin()
        } else {
            showNextScreen()
        }
    }

    override fun onStop() {
        if (pinStorage.isPinSet()) {
            showPinEntry()
        }
        super.onStop()
    }

    override fun onPause() {
        if (pinStorage.isPinSet()) {
            showPinEntry()
        }
        super.onPause()
    }

    private fun showSetupPin() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, SetupPinFragment.newInstance())
            .commit()
    }

    private fun showPinEntry() {
        if (!pinStorage.isPinSet()) {
            showRestrictionScreen()
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, PinEntryFragment.newInstance())
            .commit()
    }

    private fun showRestrictionScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, RestrictionFragment.newInstance())
            .commit()
    }

    override fun onPinCreated() {
        showNextScreen()
    }

    override fun onPinVerified() {
        showRestrictionScreen()
    }

    override fun onRequestLock() {
        showPinEntry()
    }

    override fun onRestrictionChanged(enabled: Boolean): Boolean {
        val applied = restrictionManager.setPlayStoreRestricted(enabled)
        if (applied) {
            pinStorage.setRestrictionEnabled(enabled)
        }
        return applied
    }

    override fun onAppRestrictionChanged(packageName: String, blocked: Boolean): Boolean =
        restrictionManager.setApplicationBlocked(packageName, blocked)

    override fun onRequestChangePin() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, ChangePinFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    override fun onPinUpdated() {
        supportFragmentManager.popBackStack()
    }

    fun getPinStorage(): PinStorage = pinStorage
    fun getRestrictionManager(): DeviceRestrictionManager = restrictionManager

    private fun showNextScreen() {
        if (pinStorage.isPinSet()) {
            showPinEntry()
        } else {
            showRestrictionScreen()
        }
    }
}
