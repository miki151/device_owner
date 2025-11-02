package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R

class ChangePinFragment : Fragment() {

    private var currentPinInput: EditText? = null
    private var newPinInput: EditText? = null
    private var confirmPinInput: EditText? = null
    private var updatePinButton: Button? = null
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
        val view = inflater.inflate(R.layout.fragment_change_pin, container, false)
        currentPinInput = view.findViewById(R.id.current_pin_input)
        newPinInput = view.findViewById(R.id.new_pin_input)
        confirmPinInput = view.findViewById(R.id.confirm_pin_input)
        val button = view.findViewById<Button>(R.id.update_pin_button)
        button!!.setOnClickListener { handleUpdatePin() }
        updatePinButton = button
        return view
    }

    override fun onResume() {
        super.onResume()
        currentPinInput?.setText("")
        newPinInput?.setText("")
        confirmPinInput?.setText("")
        currentPinInput?.error = null
        newPinInput?.error = null
        confirmPinInput?.error = null
        updateCurrentPinVisibility()
    }

    private fun handleUpdatePin() {
        val currentPin = currentPinInput?.text?.toString()?.trim().orEmpty()
        val newPin = newPinInput?.text?.toString()?.trim().orEmpty()
        val confirmPin = confirmPinInput?.text?.toString()?.trim().orEmpty()

        val activity = requireActivity() as MainActivity
        val pinStorage = activity.getPinStorage()
        val hasExistingPin = pinStorage.isPinSet()

        currentPinInput?.error = null
        newPinInput?.error = null
        confirmPinInput?.error = null

        when {
            hasExistingPin && !pinStorage.verifyPin(currentPin) -> {
                currentPinInput?.error = getString(R.string.pin_invalid)
            }
            newPin.isNotEmpty() && newPin.length < MIN_PIN_LENGTH -> {
                newPinInput?.error = getString(R.string.pin_too_short)
            }
            newPin != confirmPin -> {
                confirmPinInput?.error = getString(R.string.pin_mismatch)
            }
            else -> {
                pinStorage.savePin(newPin)
                val message = if (newPin.isEmpty()) {
                    R.string.pin_removed
                } else {
                    R.string.pin_updated
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                callback?.onPinUpdated()
            }
        }
    }

    override fun onDestroyView() {
        updatePinButton?.setOnClickListener(null)
        currentPinInput = null
        newPinInput = null
        confirmPinInput = null
        updatePinButton = null
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private fun updateCurrentPinVisibility() {
        val activity = activity as? MainActivity ?: return
        val hasPin = activity.getPinStorage().isPinSet()
        currentPinInput?.visibility = if (hasPin) View.VISIBLE else View.GONE
    }

    interface Callback {
        fun onPinUpdated()
    }

    companion object {
        private const val MIN_PIN_LENGTH = 4

        fun newInstance(): ChangePinFragment = ChangePinFragment()
    }
}
