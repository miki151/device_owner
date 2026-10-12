# Dumbdroid parental controls

Used for restricting apps and app installation.

App visibility is managed through `DevicePolicyManager.setApplicationHidden(null, ...)`
using the existing platform-signed system UID. The app does not register as a device
owner, profile owner, or delegate. This implementation requires Android 14's policy
state APIs and fails closed when access to them is unavailable.

Each edit checks this app's policy entry, the resolved policy for the current user,
and the actual hidden flag. A successful unblock removes App Blocker's restriction;
another controller's block can still keep the app hidden, which the UI reports.
The API's Boolean return alone is insufficient to verify these outcomes.

Existing blocks are not migrated on startup or on opening the UI. When a user
unblocks a legacy package whose hidden flag is true while policy resolves to false,
App Blocker first adopts that block with a verified true policy, then sets false.
This forces enforcement despite the policy engine's unchanged-value shortcut.
Only the package being edited is touched. If verification fails, one policy write
tries to preserve the pre-operation visibility, and the result is checked again.
There is no fallback to direct PackageManager hiding. Batch operations stop on
failure, and restriction preferences are committed only after a verified policy
result. PIN protection is unchanged. Play Store's placeholder and unknown-sources control remain separate
from its hidden policy; their failures are reported separately.

This prevents new App Blocker writes from silently diverging from policy. It is
not a framework boot-loop recovery patch and does not repair untouched legacy
state or prevent another direct PackageManager caller from creating a mismatch.

## Validation

Run the host policy tests from the Android source root:

```sh
bash packages/apps/DumbdroidAdmin/tests/run-policy-tests.sh
```

Build with the normal platform environment (`m DumbdroidAdmin`). Host tests model
Boolean policy resolution, unchanged-value enforcement, legacy repair, and injected
failures. They do not verify Binder authorization or the device's policy exemptions.

Before rollout, test the built platform-signed APK on a disposable test device:

1. Without supervision, block/unblock Clock and Play Store. Inspect
   `adb shell dumpsys device_policy` and `adb shell dumpsys package <package>`.
   Confirm a distinct App Blocker policy entry for the current user and that the
   resolved value agrees with `hidden=` after every change.
2. With Family Link active, repeat these edits in both controller orders. When
   another controller still blocks an app, removing App Blocker's restriction
   must leave its own value false, show the explanation, and preserve the other
   controller's entry. Verify Play Store's hiding policy is accepted on this image.
3. Upgrade with legacy soft and permanent blocks. Opening the UI must leave
   policy and package state unchanged. Editing a legacy soft block must reconcile
   only that package, including the cached-false/hidden-true unblocking case.
4. Check PIN protection, permanent-block confirmation and inability to unblock,
   D-pad navigation, and Play Store's placeholder and installation controls.
5. After confirming policy/visibility agreement, reboot with supervision and
   blocked apps, then repeat after unblocking. Capture `AppBlockerPolicy` logs for
   failures. Reboot and authorization checks are required device validation;
   passing host tests alone does not establish rollout readiness.
