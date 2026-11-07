package eu.dumbdroid.deviceowner.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DomainBlocklist
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import eu.dumbdroid.deviceowner.policy.PolicyChangeResult
import eu.dumbdroid.deviceowner.policy.InternetPolicyController
import eu.dumbdroid.deviceowner.policy.InternetPolicyResult
import java.util.Locale

class RestrictionFragment : Fragment() {

    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var policyAccessWarning: TextView? = null
    private var changePinButton: Button? = null
    private var dnsDialog: AlertDialog? = null
    private var domainDialog: Dialog? = null
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
        // ScrollView forces itself focusable in its constructor after View has read the XML.
        // Override that default here so D-pad focus is limited to the action buttons.
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.defaultFocusHighlightEnabled = false
        appListContainer = view.findViewById(R.id.app_list_container)
        appListEmptyText = view.findViewById(R.id.app_list_empty_text)
        policyAccessWarning = view.findViewById(R.id.device_owner_warning)
        changePinButton = view.findViewById(R.id.change_pin_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changePinButton?.setOnClickListener { callback?.onRequestChangePin() }
        view.requireViewById<Button>(R.id.edit_domains_button).setOnClickListener {
            showDomainBlocklist()
        }
        view.requireViewById<Switch>(R.id.filtering_dns_switch).setOnClickListener { control ->
            val manager = (requireActivity() as MainActivity).getInternetRestrictionManager()
            if ((control as Switch).isChecked) {
                refreshInternetControls()
                showDnsDialog()
            } else {
                reportInternetResult(manager.setDns(null))
            }
        }
        view.requireViewById<Button>(R.id.edit_dns_button).setOnClickListener { showDnsDialog() }
        view.requireViewById<Switch>(R.id.block_tethering_switch).setOnClickListener { control ->
            val manager = (requireActivity() as MainActivity).getInternetRestrictionManager()
            reportInternetResult(manager.setTetheringBlocked((control as Switch).isChecked))
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
        val canManagePolicies = restrictionManager.canManagePolicies()
        val permanentlyBlockedPackages = restrictionManager.getPermanentlyBlockedApplications()
        val apps = restrictionManager.getManageableApplications().toMutableList()

        restrictionManager.getInstalledApplication(DeviceRestrictionManager.PLAY_STORE_PACKAGE)
            ?.let { playStore ->
                apps += DeviceRestrictionManager.ManagedApp(
                    packageName = playStore.packageName,
                    label = getString(R.string.play_store_block_label),
                )
            }

        val rows = apps
            .distinctBy { it.packageName }
            .map { app ->
                val isPlayStore = app.packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE
                val isPermanent = permanentlyBlockedPackages.contains(app.packageName)
                val state = restrictionManager.getBlockState(app.packageName)
                AppRow(
                    app = app,
                    isBlocked = isPermanent || state.requested,
                    state = state,
                    isPermanent = isPermanent,
                    canBePermanentlyBlocked = !isPlayStore,
                )
            }
            .sortedWith(
                compareBy<AppRow> { row ->
                    if (row.app.packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) 0 else 1
                }.thenBy { it.app.label.lowercase(Locale.getDefault()) },
            )

        changePinButton?.setText(
            if (pinStorage.isPinSet()) R.string.change_pin_button else R.string.set_pin_button,
        )
        policyAccessWarning?.isVisible = !canManagePolicies
        refreshInternetControls()
        updateApplicationList(rows, canManagePolicies, restrictionManager)
    }

    private fun refreshInternetControls() {
        val view = view ?: return
        val activity = requireActivity() as MainActivity
        val manager = activity.getInternetRestrictionManager()
        val dns = manager.dnsState()
        val tethering = manager.isTetheringBlocked()

        val dnsSwitch = view.requireViewById<Switch>(R.id.filtering_dns_switch)
        dnsSwitch.isChecked = dns?.hostname != null
        dnsSwitch.isEnabled = dns != null
        view.requireViewById<TextView>(R.id.filtering_dns_status).text = when {
            !manager.isSupportedUser -> getString(R.string.internet_control_primary_user)
            dns == null -> getString(R.string.internet_control_unknown)
            dns.applied -> getString(R.string.filtering_dns_on, dns.hostname)
            dns.hostname != null -> getString(R.string.filtering_dns_incomplete)
            dns.locked -> getString(R.string.filtering_dns_elsewhere)
            else -> getString(R.string.filtering_dns_off)
        }
        view.requireViewById<Button>(R.id.edit_dns_button).isVisible = dns?.hostname != null

        val tetheringSwitch = view.requireViewById<Switch>(R.id.block_tethering_switch)
        tetheringSwitch.isChecked = tethering == true
        tetheringSwitch.isEnabled = tethering != null
        view.requireViewById<TextView>(R.id.block_tethering_status).setText(when {
            !manager.isSupportedUser -> R.string.internet_control_primary_user
            tethering == null -> R.string.internet_control_unknown
            tethering -> R.string.block_tethering_on
            else -> R.string.block_tethering_off
        })
    }

    private fun showDnsDialog() {
        val manager = (requireActivity() as MainActivity).getInternetRestrictionManager()
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = getString(R.string.filtering_dns_hostname_hint)
            contentDescription = hint
            setText(manager.dnsState()?.hostname ?: InternetPolicyController.DEFAULT_HOSTNAME)
            selectAll()
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val inputContainer = android.widget.FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        dnsDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.filtering_dns_dialog_title)
            .setMessage(R.string.filtering_dns_dialog_message)
            .setView(inputContainer)
            .setPositiveButton(R.string.filtering_dns_save, null)
            .setNegativeButton(R.string.cancel_changes_button, null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val result = manager.setDns(input.text.toString())
                        if (result == InternetPolicyResult.INVALID_HOSTNAME) {
                            input.error = getString(R.string.filtering_dns_invalid)
                        } else {
                            dialog.dismiss()
                            reportInternetResult(result)
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showDomainBlocklist() {
        val blocklist = DomainBlocklist()
        if (!blocklist.available) {
            AlertDialog.Builder(requireContext()).setTitle(R.string.blocked_domains_title)
                .setMessage(R.string.blocked_domains_update_required)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val context = requireContext()
        val padding = (16 * resources.displayMetrics.density).toInt()
        var focusSave: (() -> Unit)? = null
        val input = object : EditText(context) {
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                val textLayout = layout
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && selectionStart == selectionEnd &&
                    selectionEnd >= 0 && textLayout != null &&
                    (text.isNullOrEmpty() ||
                        textLayout.getLineForOffset(selectionEnd) == textLayout.lineCount - 1)) {
                    // T9 consumes Down and uses Back for deletion. Leave the last line directly.
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        focusSave?.invoke()
                    }
                    return true
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(false)
            hint = "reddit.com\nexample.com"
            nextFocusDownId = android.R.id.button1
        }
        try {
            input.setText(blocklist.read())
            input.setSelection(input.text.length)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.blocked_domains_read_failed, Toast.LENGTH_LONG).show()
            return
        }
        // Keep the editor and actions inside the available screen, even with the IME open.
        val dialog = Dialog(context, R.style.Theme_DeviceOwner)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply {
                setText(R.string.blocked_domains_title)
                setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Title)
            })
            addView(TextView(context).apply {
                setText(R.string.blocked_domains_editor_help)
                setPadding(0, padding / 2, 0, padding / 2)
            })
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(context).apply {
            id = android.R.id.button2
            setText(R.string.cancel_changes_button)
            nextFocusUpId = input.id
            nextFocusRightId = android.R.id.button1
            setOnClickListener { dialog.dismiss() }
        }
        val save = Button(context).apply {
            id = android.R.id.button1
            setText(R.string.blocked_domains_save)
            nextFocusUpId = input.id
            nextFocusLeftId = cancel.id
            setOnClickListener {
                try {
                    blocklist.save(input.text.toString())
                    dialog.dismiss()
                } catch (e: IllegalArgumentException) {
                    input.error = e.message
                    input.requestFocus()
                } catch (e: Exception) {
                    input.error = getString(R.string.blocked_domains_save_failed)
                    input.requestFocus()
                }
            }
        }
        focusSave = {
            input.clearComposingText()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
            save.requestFocus()
        }
        for (button in listOf(cancel, save)) {
            actions.addView(button, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(actions)
        dialog.setContentView(content)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { domainDialog = null }
        domainDialog = dialog
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        input.requestFocus()
        input.post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun reportInternetResult(result: InternetPolicyResult) {
        Toast.makeText(requireContext(), when (result) {
            InternetPolicyResult.APPLIED -> R.string.internet_control_updated
            InternetPolicyResult.INVALID_HOSTNAME -> R.string.filtering_dns_invalid
            InternetPolicyResult.RESTRICTED_ELSEWHERE -> R.string.internet_control_external
            InternetPolicyResult.FAILED -> R.string.internet_control_failed
        }, Toast.LENGTH_LONG).show()
        refreshInternetControls()
    }

    private fun updateApplicationList(
        rows: List<AppRow>,
        canManagePolicies: Boolean,
        restrictionManager: DeviceRestrictionManager,
    ) {
        val container = appListContainer ?: return
        container.removeAllViews()
        appListEmptyText?.isVisible = rows.isEmpty()

        val inflater = LayoutInflater.from(container.context)
        rows.forEach { row ->
            val itemView = inflater.inflate(R.layout.item_app_restriction, container, false)
            val iconView = itemView.findViewById<ImageView>(R.id.app_icon)
                ?: return@forEach
            val nameView = itemView.findViewById<TextView>(R.id.app_name)
                ?: return@forEach
            val temporaryBlockButton =
                itemView.findViewById<ImageButton>(R.id.temporary_block_button)
                    ?: return@forEach
            val permanentBlockButton =
                itemView.findViewById<ImageButton>(R.id.permanent_block_button)
                    ?: return@forEach

            iconView.setImageDrawable(restrictionManager.getApplicationIcon(row.app.packageName))
            nameView.text = row.app.label
            val statusView = itemView.findViewById<TextView>(R.id.app_policy_status)
                ?: return@forEach
            statusView.text = row.state.statusText(requireContext())
            statusView.isVisible = statusView.text.isNotEmpty()
            val canEdit = canManagePolicies && row.state.snapshot != null
            if (row.isPermanent) {
                nameView.paintFlags = nameView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                nameView.alpha = PERMANENT_ROW_ALPHA
            }

            val temporaryActionDescription = getString(
                if (row.isPermanent) {
                    R.string.app_is_permanently_blocked_description
                } else if (row.isBlocked) {
                    R.string.temporarily_unblock_app_description
                } else {
                    R.string.temporarily_block_app_description
                },
                row.app.label,
            )
            temporaryBlockButton.setImageResource(
                if (row.isBlocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open,
            )
            temporaryBlockButton.isSelected = row.isBlocked
            temporaryBlockButton.contentDescription = temporaryActionDescription
            temporaryBlockButton.tooltipText = temporaryActionDescription
            temporaryBlockButton.isEnabled = canEdit && !row.isPermanent
            temporaryBlockButton.isFocusable = temporaryBlockButton.isEnabled
            temporaryBlockButton.alpha =
                if (temporaryBlockButton.isEnabled) ENABLED_ICON_ALPHA else DISABLED_ICON_ALPHA
            temporaryBlockButton.setOnClickListener {
                setTemporaryBlock(row.app, !row.isBlocked)
            }

            val permanentActionDescription = when {
                row.isPermanent -> getString(
                    R.string.app_is_permanently_blocked_description,
                    row.app.label,
                )
                row.canBePermanentlyBlocked -> getString(
                    R.string.permanently_block_app_description,
                    row.app.label,
                )
                else -> getString(
                    R.string.permanent_block_unavailable_description,
                    row.app.label,
                )
            }
            permanentBlockButton.contentDescription = permanentActionDescription
            permanentBlockButton.tooltipText = permanentActionDescription
            permanentBlockButton.isEnabled =
                canEdit && row.canBePermanentlyBlocked && !row.isPermanent
            permanentBlockButton.isFocusable = permanentBlockButton.isEnabled
            permanentBlockButton.alpha =
                if (permanentBlockButton.isEnabled) ENABLED_ICON_ALPHA else DISABLED_ICON_ALPHA
            permanentBlockButton.setOnClickListener {
                showPermanentBlockWarning(row.app)
            }

            container.addView(itemView)
        }
    }

    private fun setTemporaryBlock(
        app: DeviceRestrictionManager.ManagedApp,
        blocked: Boolean,
    ) {
        val activity = requireActivity() as MainActivity
        val restrictionManager = activity.getRestrictionManager()
        val result = if (app.packageName == DeviceRestrictionManager.PLAY_STORE_PACKAGE) {
            restrictionManager.setPlayStoreRestricted(blocked)
        } else {
            restrictionManager.setApplicationBlocked(app.packageName, blocked)
        }

        if (result.applied) {
            val message = if (result == PolicyChangeResult.BLOCKED_BY_OTHER) {
                getString(R.string.app_unblocked_but_restricted)
            } else {
                getString(
                    if (blocked) R.string.app_temporarily_blocked else R.string.app_unblocked,
                    app.label,
                )
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        } else {
            result.showFailure(requireContext())
        }
        // Recovery may have recorded a policy even when the requested operation failed.
        refreshState()
    }

    private fun showPermanentBlockWarning(app: DeviceRestrictionManager.ManagedApp) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_permanent_block_title)
            .setMessage(getString(R.string.confirm_permanent_block_message, app.label))
            .setPositiveButton(R.string.confirm_permanent_block_positive) { _, _ ->
                permanentlyBlock(app)
            }
            .setNegativeButton(R.string.confirm_changes_negative, null)
            .show()
    }

    private fun permanentlyBlock(app: DeviceRestrictionManager.ManagedApp) {
        val restrictionManager = (requireActivity() as MainActivity).getRestrictionManager()
        val result = restrictionManager.setApplicationPermanentlyBlocked(app.packageName)
        if (result.applied) {
            Toast.makeText(
                requireContext(),
                getString(R.string.app_permanently_blocked, app.label),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            result.showFailure(requireContext())
        }
        refreshState()
    }

    override fun onDestroyView() {
        appListContainer?.removeAllViews()
        changePinButton?.setOnClickListener(null)
        domainDialog?.dismiss()
        domainDialog = null
        dnsDialog?.dismiss()
        dnsDialog = null
        appListContainer = null
        appListEmptyText = null
        policyAccessWarning = null
        changePinButton = null
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    interface Callback {
        fun onRequestChangePin()
    }

    private data class AppRow(
        val app: DeviceRestrictionManager.ManagedApp,
        val isBlocked: Boolean,
        val state: DeviceRestrictionManager.BlockState,
        val isPermanent: Boolean,
        val canBePermanentlyBlocked: Boolean,
    )

    companion object {
        private const val ENABLED_ICON_ALPHA = 1.0f
        private const val DISABLED_ICON_ALPHA = 0.38f
        private const val PERMANENT_ROW_ALPHA = 0.6f

        fun newInstance(): RestrictionFragment = RestrictionFragment()
    }
}
