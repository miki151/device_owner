package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R

class SetupPinFragment : Fragment() {

    private var pinInput: EditText? = null
    private var confirmPinInput: EditText? = null
    private var continueButton: Button? = null
    private var skipPinCheckbox: CheckBox? = null
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
        val view = inflater.inflate(R.layout.fragment_setup_pin, container, false)
        pinInput = view.findViewById(R.id.pin_input)
        confirmPinInput = view.findViewById(R.id.confirm_pin_input)
        skipPinCheckbox = view.findViewById(R.id.skip_pin_checkbox)
        skipPinCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            pinInput?.isEnabled = !isChecked
            confirmPinInput?.isEnabled = !isChecked
            if (isChecked) {
                pinInput?.setText("")
                confirmPinInput?.setText("")
                pinInput?.error = null
                confirmPinInput?.error = null
            }
        }
        val button = view.findViewById<Button>(R.id.continue_button)
        button!!.setOnClickListener { handleContinue() }
        continueButton = button
        return view
    }

    override fun onResume() {
        super.onResume()
        pinInput?.setText("")
        confirmPinInput?.setText("")
        pinInput?.error = null
        confirmPinInput?.error = null
        skipPinCheckbox?.isChecked = false
        pinInput?.isEnabled = true
        confirmPinInput?.isEnabled = true
    }

    private fun handleContinue() {
        val pin = pinInput?.text?.toString()?.trim().orEmpty()
        val confirmPin = confirmPinInput?.text?.toString()?.trim().orEmpty()
        val skipPin = skipPinCheckbox?.isChecked == true

        pinInput?.error = null
        confirmPinInput?.error = null

        val activity = requireActivity() as MainActivity
        val pinStorage = activity.getPinStorage()

        when {
            skipPin -> {
                pinStorage.savePin("")
                pinStorage.setRestrictionEnabled(false)
                Toast.makeText(requireContext(), R.string.pin_removed, Toast.LENGTH_SHORT).show()
                callback?.onPinCreated()
            }
            pin.length < MIN_PIN_LENGTH ->
                pinInput?.error = getString(R.string.pin_too_short)
            pin != confirmPin ->
                confirmPinInput?.error = getString(R.string.pin_mismatch)
            else -> {
                pinStorage.savePin(pin)
                pinStorage.setRestrictionEnabled(false)
                Toast.makeText(requireContext(), R.string.pin_saved, Toast.LENGTH_SHORT).show()
                callback?.onPinCreated()
            }
        }
    }

    override fun onDestroyView() {
        continueButton?.setOnClickListener(null)
        skipPinCheckbox?.setOnCheckedChangeListener(null)
        pinInput = null
        confirmPinInput = null
        continueButton = null
        skipPinCheckbox = null
        super.onDestroyView()
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
