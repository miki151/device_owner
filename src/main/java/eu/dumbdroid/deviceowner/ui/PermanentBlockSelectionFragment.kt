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

class PermanentBlockSelectionFragment : Fragment() {

    private var headerText: TextView? = null
    private var descriptionText: TextView? = null
    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var deviceOwnerWarning: TextView? = null
    private var confirmButton: Button? = null
    private var cancelButton: Button? = null

    private var apps: List<DeviceRestrictionManager.ManagedApp> = emptyList()
    private val toggleViews = mutableListOf<SwitchCompat>()
    private val pendingStates = mutableMapOf<String, Boolean>()
    private var permanentlyBlocked: Set<String> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_app_selection, container, false)
        headerText = view.findViewById(R.id.app_selection_header)
        descriptionText = view.findViewById(R.id.app_selection_description)
        appListContainer = view.findViewById(R.id.app_selection_list_container)
        appListEmptyText = view.findViewById(R.id.app_selection_empty_text)
        deviceOwnerWarning = view.findViewById(R.id.device_owner_warning)
        confirmButton = view.findViewById(R.id.confirm_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerText?.setText(R.string.permanent_app_selection_header)
        descriptionText?.setText(R.string.permanent_app_selection_description)
        confirmButton?.setOnClickListener { showConfirmDialog() }
        cancelButton?.setOnClickListener { parentFragmentManager.popBackStack() }
        loadApps()
    }

    private fun loadApps() {
        val activity = requireActivity() as MainActivity
        val restrictionManager = activity.getRestrictionManager()
        val isDeviceOwner = restrictionManager.isDeviceOwner()
        deviceOwnerWarning?.isVisible = !isDeviceOwner
        confirmButton?.isEnabled = isDeviceOwner

        permanentlyBlocked = restrictionManager.getPermanentlyBlockedApplications()
        apps = restrictionManager.getManageableApplications()
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        pendingStates.clear()
        apps.forEach { app ->
            pendingStates[app.packageName] = permanentlyBlocked.contains(app.packageName)
        }
        updateApplicationList(isDeviceOwner)
    }

    private fun updateApplicationList(isDeviceOwner: Boolean) {
        val container = appListContainer ?: return
        toggleViews.forEach { it.setOnCheckedChangeListener(null) }
        toggleViews.clear()
        container.removeAllViews()
        appListEmptyText?.isVisible = false

        if (apps.isEmpty()) {
            appListEmptyText?.isVisible = true
            return
        }

        val inflater = LayoutInflater.from(container.context)
        apps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_app_toggle, container, false)
            val switch = itemView.findViewById<SwitchCompat>(R.id.app_toggle_switch)
                ?: return@forEach
            val packageView = itemView.findViewById<TextView>(R.id.app_package_text)
                ?: return@forEach
            val initiallyBlocked = pendingStates[app.packageName] ?: false
            val isAlreadyPermanent = permanentlyBlocked.contains(app.packageName)
            switch.text = app.label
            switch.isEnabled = isDeviceOwner && !isAlreadyPermanent
            packageView.text = app.packageName
            val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
                pendingStates[app.packageName] = isChecked
            }
            setSwitchCheckedWithoutCallback(switch, initiallyBlocked, listener)
            container.addView(itemView)
            toggleViews.add(switch)
        }
    }

    private fun showConfirmDialog() {
        val changes = pendingStates.filter { (packageName, blocked) ->
            blocked && !permanentlyBlocked.contains(packageName)
        }
        if (changes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_changes_to_apply, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val blockList = changes.keys.map { labelForPackage(it) }.sorted()
        val message = getString(R.string.confirm_permanent_changes_block, blockList.joinToString("\n"))

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_permanent_changes_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_changes_positive) { _, _ ->
                applyChanges(changes.keys)
            }
            .setNegativeButton(R.string.confirm_changes_negative, null)
            .show()
    }

    private fun applyChanges(packagesToBlock: Set<String>) {
        val activity = requireActivity() as MainActivity
        val restrictionManager = activity.getRestrictionManager()
        val failures = mutableListOf<String>()
        packagesToBlock.forEach { packageName ->
            val applied = restrictionManager.setApplicationPermanentlyBlocked(packageName)
            if (!applied) {
                failures += packageName
            } else {
                pendingStates[packageName] = true
            }
        }
        permanentlyBlocked = restrictionManager.getPermanentlyBlockedApplications()
        if (failures.isEmpty()) {
            Toast.makeText(requireContext(), R.string.permanent_app_changes_applied, Toast.LENGTH_SHORT)
                .show()
            parentFragmentManager.popBackStack()
        } else {
            Toast.makeText(requireContext(), R.string.app_changes_failed, Toast.LENGTH_SHORT)
                .show()
            loadApps()
        }
    }

    private fun labelForPackage(packageName: String): String {
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
        headerText = null
        descriptionText = null
        appListContainer = null
        appListEmptyText = null
        deviceOwnerWarning = null
        confirmButton = null
        cancelButton = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): PermanentBlockSelectionFragment = PermanentBlockSelectionFragment()
    }
}
