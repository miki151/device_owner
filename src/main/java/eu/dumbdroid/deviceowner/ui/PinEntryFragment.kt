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

class PinEntryFragment : Fragment() {

    private var pinInput: EditText? = null
    private var continueButton: Button? = null
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
        val view = inflater.inflate(R.layout.fragment_pin_entry, container, false)
        pinInput = view.findViewById(R.id.pin_input)
        val button = view.findViewById<Button>(R.id.continue_button)
        button!!.setOnClickListener { validatePin() }
        continueButton = button
        return view
    }

    override fun onResume() {
        super.onResume()
        pinInput?.setText("")
        pinInput?.error = null
    }

    private fun validatePin() {
        val pin = pinInput?.text?.toString()?.trim().orEmpty()
        val isValid = (requireActivity() as MainActivity).getPinStorage().verifyPin(pin)
        if (isValid) {
            pinInput?.error = null
            callback?.onPinVerified()
        } else {
            pinInput?.error = getString(R.string.pin_invalid)
            Toast.makeText(requireContext(), R.string.incorrect_pin, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        continueButton?.setOnClickListener(null)
        pinInput = null
        continueButton = null
        super.onDestroyView()
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
