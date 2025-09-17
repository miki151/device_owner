package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.databinding.FragmentSetupPinBinding
import com.google.android.material.snackbar.Snackbar

class SetupPinFragment : Fragment() {

    private var _binding: FragmentSetupPinBinding? = null
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
        _binding = FragmentSetupPinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.continueButton.setOnClickListener { handleContinue() }
    }

    override fun onResume() {
        super.onResume()
        binding.pinInput.setText("")
        binding.confirmPinInput.setText("")
        binding.pinLayout.error = null
        binding.confirmPinLayout.error = null
    }

    private fun handleContinue() {
        val pin = binding.pinInput.text?.toString()?.trim() ?: ""
        val confirmPin = binding.confirmPinInput.text?.toString()?.trim() ?: ""

        binding.pinLayout.error = null
        binding.confirmPinLayout.error = null

        when {
            pin.length < MIN_PIN_LENGTH ->
                binding.pinLayout.error = getString(R.string.pin_too_short)
            pin != confirmPin ->
                binding.confirmPinLayout.error = getString(R.string.pin_mismatch)
            else -> {
                val activity = requireActivity() as MainActivity
                activity.getPinStorage().savePin(pin)
                activity.getPinStorage().setRestrictionEnabled(false)
                Snackbar.make(binding.root, R.string.pin_saved, Snackbar.LENGTH_SHORT)
                    .show()
                callback?.onPinCreated()
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
        fun onPinCreated()
    }

    companion object {
        private const val MIN_PIN_LENGTH = 4

        fun newInstance(): SetupPinFragment = SetupPinFragment()
    }
}
