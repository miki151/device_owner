package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.databinding.FragmentRestrictionBinding
import com.google.android.material.snackbar.Snackbar

class RestrictionFragment : Fragment() {

    private var _binding: FragmentRestrictionBinding? = null
    private val binding get() = _binding!!
    private var callback: Callback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? Callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestrictionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lockButton.setOnClickListener { callback?.onRequestLock() }
        binding.changePinButton.setOnClickListener { callback?.onRequestChangePin() }
        binding.restrictionSwitch.setOnCheckedChangeListener(switchListener)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val binding = _binding ?: return
        val activity = requireActivity() as MainActivity
        val isRestricted = activity.getPinStorage().isRestrictionEnabled()
        val isDeviceOwner = activity.getRestrictionManager().isDeviceOwner()
        setSwitchCheckedWithoutCallback(isRestricted)
        binding.restrictionSwitch.isEnabled = isDeviceOwner
        binding.deviceOwnerWarning.isVisible = !isDeviceOwner
        updateStatusText(isRestricted)
    }

    private fun setSwitchCheckedWithoutCallback(checked: Boolean) {
        val binding = _binding ?: return
        binding.restrictionSwitch.setOnCheckedChangeListener(null)
        binding.restrictionSwitch.isChecked = checked
        binding.restrictionSwitch.setOnCheckedChangeListener(switchListener)
    }

    private fun updateStatusText(restricted: Boolean) {
        _binding?.statusText?.setText(
            if (restricted) R.string.restriction_status_on else R.string.restriction_status_off
        )
    }

    override fun onDestroyView() {
        _binding?.restrictionSwitch?.setOnCheckedChangeListener(null)
        super.onDestroyView()
        _binding = null
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
        val binding = _binding ?: return@OnCheckedChangeListener
        val applied = callback?.onRestrictionChanged(isChecked) ?: false
        if (applied) {
            updateStatusText(isChecked)
        } else {
            Snackbar.make(binding.root, R.string.restrictions_error_generic, Snackbar.LENGTH_SHORT).show()
            setSwitchCheckedWithoutCallback(!isChecked)
        }
    }
}
