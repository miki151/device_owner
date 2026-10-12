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
import eu.dumbdroid.deviceowner.policy.PolicyChangeResult
import java.util.Locale

class AppSelectionFragment : Fragment() {

    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var policyAccessWarning: TextView? = null
    private var confirmButton: Button? = null
    private var cancelButton: Button? = null

    private var apps: List<DeviceRestrictionManager.ManagedApp> = emptyList()
    private val toggleViews = mutableListOf<SwitchCompat>()
    private val blockStates = mutableMapOf<String, DeviceRestrictionManager.BlockState>()
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
        policyAccessWarning = view.findViewById(R.id.device_owner_warning)
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
        val canManagePolicies = restrictionManager.canManagePolicies()
        policyAccessWarning?.isVisible = !canManagePolicies
        confirmButton?.isEnabled = canManagePolicies

        val permanentlyBlocked =
            restrictionManager.getPermanentlyBlockedApplications()
        apps = restrictionManager.getManageableApplications()
            .filterNot { permanentlyBlocked.contains(it.packageName) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        blockStates.clear()
        val playStore = DeviceRestrictionManager.PLAY_STORE_PACKAGE
        blockStates[playStore] = restrictionManager.getBlockState(playStore)
        playStoreBlockedInitial = blockStates.getValue(playStore).requested
        playStoreBlockedPending = playStoreBlockedInitial

        initialStates.clear()
        pendingStates.clear()
        apps.forEach { app ->
            val state = restrictionManager.getBlockState(app.packageName)
            blockStates[app.packageName] = state
            val blocked = state.requested
            initialStates[app.packageName] = blocked
            pendingStates[app.packageName] = blocked
        }
        updateApplicationList(canManagePolicies)
    }

    private fun updateApplicationList(canManagePolicies: Boolean) {
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
            val state = blockStates.getValue(DeviceRestrictionManager.PLAY_STORE_PACKAGE)
            playStoreSwitch.isEnabled = canManagePolicies && state.snapshot != null
            playStorePackageView.text = listOfNotNull(
                DeviceRestrictionManager.PLAY_STORE_PACKAGE, state.statusText(requireContext()),
            ).joinToString("\n")
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
            val state = blockStates.getValue(app.packageName)
            switch.isEnabled = canManagePolicies && state.snapshot != null
            packageView.text = listOfNotNull(
                app.packageName, state.statusText(requireContext()),
            ).joinToString("\n")
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
        var restrictedByOther = false
        for ((packageName, blocked) in changes) {
            val result = if (packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) {
                restrictionManager.setPlayStoreRestricted(blocked)
            } else {
                restrictionManager.setApplicationBlocked(packageName, blocked)
            }
            if (!result.applied) {
                result.showFailure(requireContext())
                loadApps()
                return
            }
            restrictedByOther = restrictedByOther || result == PolicyChangeResult.BLOCKED_BY_OTHER
        }
        Toast.makeText(
            requireContext(),
            if (restrictedByOther) R.string.app_changes_other_restriction
            else R.string.app_changes_applied,
            Toast.LENGTH_LONG,
        ).show()
        parentFragmentManager.popBackStack()
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
        policyAccessWarning = null
        confirmButton = null
        cancelButton = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): AppSelectionFragment = AppSelectionFragment()
    }
}
