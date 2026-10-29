# Dumbdroid parental controls

Used for restricting apps and app installation.

## Internet controls MVP

The existing PIN gate protects configurable strict Private DNS and internet-sharing
controls. A PIN is required and cannot be removed while either control is active
or a failed DNS change still needs recovery. Only the system user can configure
these device-wide settings.

DNS defaults to `family.adguard-dns.com`; enter your curated resolver hostname later.
The original nullable DNS mode/hostname is saved before the first change, preserved
through provider edits, and restored on disable. Failed changes retain this snapshot
for retry. An unreachable strict resolver may stop internet access. AdGuard Family
is not a distracting-site denylist; app-specific DNS/proxies can bypass system DNS.

**Ship the companion Settings patch in the same firmware.** Current Settings ignores
App Blocker's base Private DNS restriction. The patch checks effective restrictions
when displaying, opening, and saving the Private DNS preference. App Blocker alone
cannot enforce that Settings lock.

Android persists DNS and tethering restrictions across reboot; no boot receiver or
reapplication is needed. The DNS restoration snapshot is excluded from cloud backup
and device transfer. Firmware updates must preserve app data. Permanent locking,
app-data clearing, and factory-reset prevention are outside this MVP.

App Blocker controls the system/base tethering and installation flags directly,
matching the existing unknown-sources control. It does not snapshot other writers'
base flags. Independent device/profile-owner restrictions remain effective.
Installation blocking also blocks app updates; temporarily allow installation to
update apps. Review already-installed browsers and VPNs during parental setup.

Run `bash tests/run-internet-policy-tests.sh` with `KOTLINC` set, Kotlin on PATH, or
an Android build environment. Host tests cover restoration, provider edits, failures,
PIN-removal eligibility, platform state, and secondary-user rejection. Live filtering,
Settings enforcement, hotspot shutdown, and firmware upgrades need device checks.

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
