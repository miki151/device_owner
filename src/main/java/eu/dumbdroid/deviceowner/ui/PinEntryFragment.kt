package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.databinding.FragmentPinEntryBinding
import com.google.android.material.snackbar.Snackbar

class PinEntryFragment : Fragment() {

    private var _binding: FragmentPinEntryBinding? = null
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
        _binding = FragmentPinEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.continueButton.setOnClickListener { validatePin() }
    }

    override fun onResume() {
        super.onResume()
        binding.pinInput.setText("")
        binding.pinLayout.error = null
    }

    private fun validatePin() {
        val pin = binding.pinInput.text?.toString()?.trim() ?: ""
        val isValid = (requireActivity() as MainActivity).getPinStorage().verifyPin(pin)
        if (isValid) {
            binding.pinLayout.error = null
            callback?.onPinVerified()
        } else {
            binding.pinLayout.error = getString(R.string.pin_invalid)
            Snackbar.make(binding.root, R.string.incorrect_pin, Snackbar.LENGTH_SHORT).show()
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
        fun onPinVerified()
    }

    companion object {
        fun newInstance(): PinEntryFragment = PinEntryFragment()
    }
}
