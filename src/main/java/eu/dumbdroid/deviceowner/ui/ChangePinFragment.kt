package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.databinding.FragmentChangePinBinding
import com.google.android.material.snackbar.Snackbar

class ChangePinFragment : Fragment() {

    private var _binding: FragmentChangePinBinding? = null
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
        _binding = FragmentChangePinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.updatePinButton.setOnClickListener { handleUpdatePin() }
    }

    override fun onResume() {
        super.onResume()
        binding.currentPinInput.setText("")
        binding.newPinInput.setText("")
        binding.confirmPinInput.setText("")
        binding.currentPinLayout.error = null
        binding.newPinLayout.error = null
        binding.confirmPinLayout.error = null
    }

    private fun handleUpdatePin() {
        val currentPin = binding.currentPinInput.text?.toString()?.trim() ?: ""
        val newPin = binding.newPinInput.text?.toString()?.trim() ?: ""
        val confirmPin = binding.confirmPinInput.text?.toString()?.trim() ?: ""

        val activity = requireActivity() as MainActivity
        val pinStorage = activity.getPinStorage()

        binding.currentPinLayout.error = null
        binding.newPinLayout.error = null
        binding.confirmPinLayout.error = null

        when {
            !pinStorage.verifyPin(currentPin) -> {
                binding.currentPinLayout.error = getString(R.string.pin_invalid)
            }
            newPin.length < MIN_PIN_LENGTH -> {
                binding.newPinLayout.error = getString(R.string.pin_too_short)
            }
            newPin != confirmPin -> {
                binding.confirmPinLayout.error = getString(R.string.pin_mismatch)
            }
            else -> {
                pinStorage.savePin(newPin)
                Snackbar.make(binding.root, R.string.pin_updated, Snackbar.LENGTH_SHORT).show()
                callback?.onPinUpdated()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    interface Callback {
        fun onPinUpdated()
    }

    companion object {
        private const val MIN_PIN_LENGTH = 4

        fun newInstance(): ChangePinFragment = ChangePinFragment()
    }
}
