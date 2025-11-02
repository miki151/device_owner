package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import java.util.Locale

class RestrictionFragment : Fragment() {

    private var playStoreStatusText: TextView? = null
    private var playStoreSwitch: SwitchCompat? = null
    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var deviceOwnerWarning: TextView? = null
    private var changePinButton: Button? = null
//    private var lockButton: Button? = null
    private var callback: Callback? = null
    private val appToggleViews = mutableListOf<SwitchCompat>()

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
        playStoreStatusText = view.findViewById(R.id.play_store_status_text)
        playStoreSwitch = view.findViewById(R.id.play_store_switch)
        appListContainer = view.findViewById(R.id.app_list_container)
        appListEmptyText = view.findViewById(R.id.app_list_empty_text)
        deviceOwnerWarning = view.findViewById(R.id.device_owner_warning)
        changePinButton = view.findViewById(R.id.change_pin_button)
        //lockButton = view.findViewById(R.id.lock_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changePinButton?.setOnClickListener { callback?.onRequestChangePin() }
        //lockButton?.setOnClickListener { callback?.onRequestLock() }
        playStoreSwitch?.setOnCheckedChangeListener(playStoreSwitchListener)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val activity = requireActivity() as MainActivity
        val pinStorage = activity.getPinStorage()
        val restrictionManager = activity.getRestrictionManager()
        val isRestricted = pinStorage.isRestrictionEnabled()
        val isDeviceOwner = restrictionManager.isDeviceOwner()
        changePinButton?.setText(
            if (pinStorage.isPinSet()) R.string.change_pin_button else R.string.set_pin_button,
        )
        setSwitchCheckedWithoutCallback(playStoreSwitch, isRestricted, playStoreSwitchListener)
        playStoreSwitch?.isEnabled = isDeviceOwner
        deviceOwnerWarning?.isVisible = !isDeviceOwner
        updatePlayStoreStatusText(isRestricted)
        val apps = restrictionManager.getManageableApplications()
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        updateApplicationList(apps, restrictionManager, isDeviceOwner)
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

    private fun updatePlayStoreStatusText(restricted: Boolean) {
        playStoreStatusText?.setText(
            if (restricted) R.string.restriction_status_on else R.string.restriction_status_off,
        )
    }

    override fun onDestroyView() {
        playStoreSwitch?.setOnCheckedChangeListener(null)
        appToggleViews.forEach { it.setOnCheckedChangeListener(null) }
        appToggleViews.clear()
        appListContainer?.removeAllViews()
        changePinButton?.setOnClickListener(null)
        //lockButton?.setOnClickListener(null)
        playStoreStatusText = null
        playStoreSwitch = null
        appListContainer = null
        appListEmptyText = null
        deviceOwnerWarning = null
        changePinButton = null
        //lockButton = null
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    interface Callback {
        fun onRestrictionChanged(enabled: Boolean): Boolean
        fun onAppRestrictionChanged(packageName: String, blocked: Boolean): Boolean
        fun onRequestLock()
        fun onRequestChangePin()
    }

    companion object {
        fun newInstance(): RestrictionFragment = RestrictionFragment()
    }

    private val playStoreSwitchListener = object : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            val applied = callback?.onRestrictionChanged(isChecked) ?: false
            if (applied) {
                updatePlayStoreStatusText(isChecked)
            } else {
                showRestrictionError()
                setSwitchCheckedWithoutCallback(playStoreSwitch, !isChecked, this)
            }
        }
    }

    private fun showRestrictionError() {
        Toast.makeText(requireContext(), R.string.restrictions_error_generic, Toast.LENGTH_SHORT)
            .show()
    }

    private fun updateApplicationList(
        apps: List<DeviceRestrictionManager.ManagedApp>,
        restrictionManager: DeviceRestrictionManager,
        isDeviceOwner: Boolean,
    ) {
        val container = appListContainer ?: return
        appToggleViews.forEach { it.setOnCheckedChangeListener(null) }
        appToggleViews.clear()
        container.removeAllViews()
        val emptyText = appListEmptyText
        if (apps.isEmpty()) {
            emptyText?.isVisible = true
            return
        } else {
            emptyText?.isVisible = false
        }

        val inflater = LayoutInflater.from(container.context)
        apps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_app_toggle, container, false)
            val switch = itemView.findViewById<SwitchCompat>(R.id.app_toggle_switch)
                ?: return@forEach
            val packageView = itemView.findViewById<TextView>(R.id.app_package_text)
                ?: return@forEach
            switch.text = app.label
            switch.isEnabled = isDeviceOwner
            packageView.text = app.packageName
            val listener = object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                    val applied = callback?.onAppRestrictionChanged(app.packageName, isChecked) ?: false
                    if (!applied) {
                        showRestrictionError()
                        setSwitchCheckedWithoutCallback(switch, !isChecked, this)
                    }
                }
            }
            val initialBlocked = if (isDeviceOwner) {
                restrictionManager.isApplicationBlocked(app.packageName)
            } else {
                false
            }
            setSwitchCheckedWithoutCallback(switch, initialBlocked, listener)
            container.addView(itemView)
            appToggleViews.add(switch)
        }
    }
}
