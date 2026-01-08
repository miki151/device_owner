package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import java.util.Locale

class RestrictionFragment : Fragment() {

    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var permanentAppListContainer: ViewGroup? = null
    private var permanentAppListEmptyText: TextView? = null
    private var deviceOwnerWarning: TextView? = null
    private var changePinButton: Button? = null
    private var manageAppsButton: Button? = null
    private var permanentlyBlockAppsButton: Button? = null
//    private var lockButton: Button? = null
    private var callback: Callback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? Callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_restriction, container, false)
        appListContainer = view.findViewById(R.id.app_list_container)
        appListEmptyText = view.findViewById(R.id.app_list_empty_text)
        permanentAppListContainer = view.findViewById(R.id.permanent_app_list_container)
        permanentAppListEmptyText = view.findViewById(R.id.permanent_app_list_empty_text)
        deviceOwnerWarning = view.findViewById(R.id.device_owner_warning)
        changePinButton = view.findViewById(R.id.change_pin_button)
        manageAppsButton = view.findViewById(R.id.manage_apps_button)
        permanentlyBlockAppsButton = view.findViewById(R.id.permanently_block_apps_button)
        //lockButton = view.findViewById(R.id.lock_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changePinButton?.setOnClickListener { callback?.onRequestChangePin() }
        //lockButton?.setOnClickListener { callback?.onRequestLock() }
        manageAppsButton?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content_container, AppSelectionFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        permanentlyBlockAppsButton?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content_container, PermanentBlockSelectionFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val activity = requireActivity() as MainActivity
        val pinStorage = activity.getPinStorage()
        val restrictionManager = activity.getRestrictionManager()
        val isRestricted = pinStorage.isRestrictionEnabled() ||
            restrictionManager.isApplicationBlocked(DeviceRestrictionManager.PLAY_STORE_PACKAGE)
        val isDeviceOwner = restrictionManager.isDeviceOwner()
        changePinButton?.setText(
            if (pinStorage.isPinSet()) R.string.change_pin_button else R.string.set_pin_button,
        )
        deviceOwnerWarning?.isVisible = !isDeviceOwner
        val apps = restrictionManager.getManageableApplications()
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        val appMap = apps.associateBy { it.packageName }
        val permanentlyBlockedPackages = restrictionManager.getPermanentlyBlockedApplications()
        val blockedApps = mutableListOf<DeviceRestrictionManager.ManagedApp>()
        if (isRestricted) {
            blockedApps += DeviceRestrictionManager.ManagedApp(
                packageName = DeviceRestrictionManager.PLAY_STORE_PACKAGE,
                label = getString(R.string.play_store_block_label),
            )
        }
        blockedApps += apps.filter {
            !permanentlyBlockedPackages.contains(it.packageName) &&
                restrictionManager.isApplicationBlocked(it.packageName)
        }
        val permanentlyBlockedApps = permanentlyBlockedPackages.map { packageName ->
            val app = appMap[packageName]
            if (app != null) {
                DeviceRestrictionManager.ManagedApp(packageName = app.packageName, label = app.label)
            } else {
                DeviceRestrictionManager.ManagedApp(packageName = packageName, label = packageName)
            }
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }
        manageAppsButton?.isEnabled = isDeviceOwner
        permanentlyBlockAppsButton?.isEnabled = isDeviceOwner
        updateApplicationList(blockedApps)
        updatePermanentApplicationList(permanentlyBlockedApps)
    }

    override fun onDestroyView() {
        appListContainer?.removeAllViews()
        permanentAppListContainer?.removeAllViews()
        changePinButton?.setOnClickListener(null)
        manageAppsButton?.setOnClickListener(null)
        permanentlyBlockAppsButton?.setOnClickListener(null)
        //lockButton?.setOnClickListener(null)
        appListContainer = null
        appListEmptyText = null
        permanentAppListContainer = null
        permanentAppListEmptyText = null
        deviceOwnerWarning = null
        changePinButton = null
        manageAppsButton = null
        permanentlyBlockAppsButton = null
        //lockButton = null
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    interface Callback {
        fun onRequestLock()
        fun onRequestChangePin()
    }

    companion object {
        fun newInstance(): RestrictionFragment = RestrictionFragment()
    }

    private fun updateApplicationList(
        blockedApps: List<DeviceRestrictionManager.ManagedApp>,
    ) {
        val container = appListContainer ?: return
        container.removeAllViews()
        val emptyText = appListEmptyText
        if (blockedApps.isEmpty()) {
            emptyText?.isVisible = true
            return
        } else {
            emptyText?.isVisible = false
        }

        val inflater = LayoutInflater.from(container.context)
        blockedApps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_blocked_app, container, false)
            val nameView = itemView.findViewById<TextView>(R.id.blocked_app_label)
                ?: return@forEach
            val packageView = itemView.findViewById<TextView>(R.id.blocked_app_package)
                ?: return@forEach
            nameView.text = app.label
            packageView.text = app.packageName
            container.addView(itemView)
        }
    }

    private fun updatePermanentApplicationList(
        blockedApps: List<DeviceRestrictionManager.ManagedApp>,
    ) {
        val container = permanentAppListContainer ?: return
        container.removeAllViews()
        val emptyText = permanentAppListEmptyText
        if (blockedApps.isEmpty()) {
            emptyText?.isVisible = true
            return
        } else {
            emptyText?.isVisible = false
        }

        val inflater = LayoutInflater.from(container.context)
        blockedApps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_blocked_app, container, false)
            val nameView = itemView.findViewById<TextView>(R.id.blocked_app_label)
                ?: return@forEach
            val packageView = itemView.findViewById<TextView>(R.id.blocked_app_package)
                ?: return@forEach
            nameView.text = app.label
            packageView.text = app.packageName
            container.addView(itemView)
        }
    }
}
