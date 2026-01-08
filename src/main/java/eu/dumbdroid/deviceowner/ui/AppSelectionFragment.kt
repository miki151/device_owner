package eu.dumbdroid.deviceowner.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import java.util.Locale

class AppSelectionFragment : Fragment() {

    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var deviceOwnerWarning: TextView? = null
    private var confirmButton: Button? = null
    private var cancelButton: Button? = null

    private var apps: List<DeviceRestrictionManager.ManagedApp> = emptyList()
    private val toggleViews = mutableListOf<SwitchCompat>()
    private val initialStates = mutableMapOf<String, Boolean>()
    private val pendingStates = mutableMapOf<String, Boolean>()
    private var playStoreBlockedInitial: Boolean = false
    private var playStoreBlockedPending: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_app_selection, container, false)
        appListContainer = view.findViewById(R.id.app_selection_list_container)
        appListEmptyText = view.findViewById(R.id.app_selection_empty_text)
        deviceOwnerWarning = view.findViewById(R.id.device_owner_warning)
        confirmButton = view.findViewById(R.id.confirm_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmButton?.setOnClickListener { showConfirmDialog() }
        cancelButton?.setOnClickListener { parentFragmentManager.popBackStack() }
        loadApps()
    }

    private fun loadApps() {
        val activity = requireActivity() as MainActivity
        val restrictionManager = activity.getRestrictionManager()
        val pinStorage = activity.getPinStorage()
        val isDeviceOwner = restrictionManager.isDeviceOwner()
        deviceOwnerWarning?.isVisible = !isDeviceOwner
        confirmButton?.isEnabled = isDeviceOwner

        val permanentlyBlocked =
            restrictionManager.getPermanentlyBlockedApplications()
        apps = restrictionManager.getManageableApplications()
            .filterNot { permanentlyBlocked.contains(it.packageName) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        playStoreBlockedInitial = pinStorage.isRestrictionEnabled() ||
            restrictionManager.isApplicationBlocked(DeviceRestrictionManager.PLAY_STORE_PACKAGE)
        playStoreBlockedPending = playStoreBlockedInitial

        initialStates.clear()
        pendingStates.clear()
        apps.forEach { app ->
            val blocked = restrictionManager.isApplicationBlocked(app.packageName)
            initialStates[app.packageName] = blocked
            pendingStates[app.packageName] = blocked
        }
        updateApplicationList(isDeviceOwner)
    }

    private fun updateApplicationList(isDeviceOwner: Boolean) {
        val container = appListContainer ?: return
        toggleViews.forEach { it.setOnCheckedChangeListener(null) }
        toggleViews.clear()
        container.removeAllViews()
        appListEmptyText?.isVisible = false

        val inflater = LayoutInflater.from(container.context)
        // Play Store toggle goes first
        val playStoreView = inflater.inflate(R.layout.item_app_toggle, container, false)
        val playStoreSwitch =
            playStoreView.findViewById<SwitchCompat>(R.id.app_toggle_switch)
        val playStorePackageView =
            playStoreView.findViewById<TextView>(R.id.app_package_text)
        if (playStoreSwitch != null && playStorePackageView != null) {
            playStoreSwitch.text = getString(R.string.play_store_block_label)
            playStoreSwitch.isEnabled = isDeviceOwner
            playStorePackageView.text = DeviceRestrictionManager.PLAY_STORE_PACKAGE
            val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
                playStoreBlockedPending = isChecked
            }
            setSwitchCheckedWithoutCallback(playStoreSwitch, playStoreBlockedPending, listener)
            container.addView(playStoreView)
            toggleViews.add(playStoreSwitch)
        }

        apps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_app_toggle, container, false)
            val switch = itemView.findViewById<SwitchCompat>(R.id.app_toggle_switch)
                ?: return@forEach
            val packageView = itemView.findViewById<TextView>(R.id.app_package_text)
                ?: return@forEach
            val initialBlocked = pendingStates[app.packageName] ?: false
            switch.text = app.label
            switch.isEnabled = isDeviceOwner
            packageView.text = app.packageName
            val listener = object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                    pendingStates[app.packageName] = isChecked
                }
            }
            setSwitchCheckedWithoutCallback(switch, initialBlocked, listener)
            container.addView(itemView)
            toggleViews.add(switch)
        }
    }

    private fun showConfirmDialog() {
        val changes = mutableMapOf<String, Boolean>()
        if (playStoreBlockedPending != playStoreBlockedInitial) {
            changes[DeviceRestrictionManager.PLAY_STORE_PACKAGE] = playStoreBlockedPending
        }
        changes.putAll(
            pendingStates.filter { (packageName, blocked) ->
                blocked != (initialStates[packageName] ?: false)
            },
        )
        if (changes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_changes_to_apply, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val blockList = changes.filter { it.value }.keys.map { labelForPackage(it) }.sorted()
        val unblockList = changes.filter { !it.value }.keys.map { labelForPackage(it) }.sorted()
        val messageParts = mutableListOf<String>()
        if (blockList.isNotEmpty()) {
            messageParts += getString(R.string.confirm_changes_block, blockList.joinToString("\n"))
        }
        if (unblockList.isNotEmpty()) {
            messageParts += getString(R.string.confirm_changes_unblock, unblockList.joinToString("\n"))
        }
        val message = messageParts.joinToString("\n\n")

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_changes_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_changes_positive) { _, _ ->
                applyChanges(changes)
            }
            .setNegativeButton(R.string.confirm_changes_negative, null)
            .show()
    }

    private fun applyChanges(changes: Map<String, Boolean>) {
        val activity = requireActivity() as MainActivity
        val restrictionManager = activity.getRestrictionManager()
        val pinStorage = activity.getPinStorage()
        val failures = mutableListOf<String>()
        changes.forEach { (packageName, blocked) ->
            val applied = if (packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) {
                val result = restrictionManager.setPlayStoreRestricted(blocked)
                if (result) {
                    pinStorage.setRestrictionEnabled(blocked)
                }
                result
            } else {
                restrictionManager.setApplicationBlocked(packageName, blocked)
            }
            if (!applied) {
                failures += packageName
            } else {
                if (packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) {
                    playStoreBlockedInitial = blocked
                } else {
                    initialStates[packageName] = blocked
                }
            }
        }
        if (failures.isEmpty()) {
            Toast.makeText(requireContext(), R.string.app_changes_applied, Toast.LENGTH_SHORT)
                .show()
            parentFragmentManager.popBackStack()
        } else {
            Toast.makeText(requireContext(), R.string.app_changes_failed, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun labelForPackage(packageName: String): String {
        if (packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) {
            return getString(
                R.string.play_store_change_label,
                DeviceRestrictionManager.PLAY_STORE_PACKAGE,
            )
        }
        val app = apps.find { it.packageName == packageName }
        return if (app != null) {
            "${app.label} (${app.packageName})"
        } else {
            packageName
        }
    }

    private fun setSwitchCheckedWithoutCallback(
        switchView: SwitchCompat?,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener,
    ) {
        val view = switchView ?: return
        view.setOnCheckedChangeListener(null)
        view.isChecked = checked
        view.setOnCheckedChangeListener(listener)
    }

    override fun onDestroyView() {
        toggleViews.forEach { it.setOnCheckedChangeListener(null) }
        toggleViews.clear()
        appListContainer?.removeAllViews()
        confirmButton?.setOnClickListener(null)
        cancelButton?.setOnClickListener(null)
        appListContainer = null
        appListEmptyText = null
        deviceOwnerWarning = null
        confirmButton = null
        cancelButton = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): AppSelectionFragment = AppSelectionFragment()
    }
}
