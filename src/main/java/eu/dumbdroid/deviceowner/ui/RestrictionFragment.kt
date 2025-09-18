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

class RestrictionFragment : Fragment() {

    private var statusText: TextView? = null
    private var restrictionSwitch: SwitchCompat? = null
    private var deviceOwnerWarning: TextView? = null
    private var changePinButton: Button? = null
    private var lockButton: Button? = null
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
        statusText = view.findViewById(R.id.status_text)
        restrictionSwitch = view.findViewById(R.id.restriction_switch)
        deviceOwnerWarning = view.findViewById(R.id.device_owner_warning)
        changePinButton = view.findViewById(R.id.change_pin_button)
        lockButton = view.findViewById(R.id.lock_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changePinButton?.setOnClickListener { callback?.onRequestChangePin() }
        lockButton?.setOnClickListener { callback?.onRequestLock() }
        restrictionSwitch?.setOnCheckedChangeListener(switchListener)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val activity = requireActivity() as MainActivity
        val isRestricted = activity.getPinStorage().isRestrictionEnabled()
        val isDeviceOwner = activity.getRestrictionManager().isDeviceOwner()
        setSwitchCheckedWithoutCallback(isRestricted)
        restrictionSwitch?.isEnabled = isDeviceOwner
        deviceOwnerWarning?.isVisible = !isDeviceOwner
        updateStatusText(isRestricted)
    }

    private fun setSwitchCheckedWithoutCallback(checked: Boolean) {
        val switchView = restrictionSwitch ?: return
        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = checked
        switchView.setOnCheckedChangeListener(switchListener)
    }

    private fun updateStatusText(restricted: Boolean) {
        statusText?.setText(
            if (restricted) R.string.restriction_status_on else R.string.restriction_status_off,
        )
    }

    override fun onDestroyView() {
        restrictionSwitch?.setOnCheckedChangeListener(null)
        changePinButton?.setOnClickListener(null)
        lockButton?.setOnClickListener(null)
        statusText = null
        restrictionSwitch = null
        deviceOwnerWarning = null
        changePinButton = null
        lockButton = null
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    interface Callback {
        fun onRestrictionChanged(enabled: Boolean): Boolean
        fun onRequestLock()
        fun onRequestChangePin()
    }

    companion object {
        fun newInstance(): RestrictionFragment = RestrictionFragment()
    }

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        val applied = callback?.onRestrictionChanged(isChecked) ?: false
        if (applied) {
            updateStatusText(isChecked)
        } else {
            Toast.makeText(requireContext(), R.string.restrictions_error_generic, Toast.LENGTH_SHORT)
                .show()
            setSwitchCheckedWithoutCallback(!isChecked)
        }
    }
}
