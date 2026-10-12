package eu.dumbdroid.deviceowner.ui

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import eu.dumbdroid.deviceowner.R
import eu.dumbdroid.deviceowner.policy.DeviceRestrictionManager
import eu.dumbdroid.deviceowner.policy.PolicyChangeResult
import java.util.Locale

class RestrictionFragment : Fragment() {

    private var appListContainer: ViewGroup? = null
    private var appListEmptyText: TextView? = null
    private var policyAccessWarning: TextView? = null
    private var changePinButton: Button? = null
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
        updateApplicationList(rows, canManagePolicies, restrictionManager)
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
        fun onRequestLock()
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
