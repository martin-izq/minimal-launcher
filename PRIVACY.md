# Privacy Policy — Foco

_Last updated: 2026-07-08_

**Foco** is a minimalist Android launcher. This policy explains what it does with
your information. In short: **Foco collects nothing, stores everything on your
device, and sends nothing anywhere.**

## Data we collect

**None.** Foco has no accounts, no analytics, no ads, no trackers, and no network
calls of its own. It does not transmit any data off your device.

## Data stored on your device

All settings (favorites, hidden apps, renamed labels, gestures, focus sessions,
time limits, etc.) are stored **locally** on your device using Android's
DataStore. This data never leaves the device and is removed when you uninstall
the app.

## Permissions and why they are used

- **See installed apps (`QUERY_ALL_PACKAGES`)** — required for any home-screen
  launcher to list and open your apps. The list is used only on-device.
- **Usage access (`PACKAGE_USAGE_STATS`)** — optional. If you grant it, Foco
  shows your screen time and can enforce per-app daily limits. Usage data is read
  on-device and never stored or shared.
- **Accessibility service** — optional. If you enable it, Foco can pull down the
  notification shade (swipe) and lock the screen (double tap). The service does
  **not** read, log, or transmit screen content; it only performs those two
  actions on your request.
- **Modify secure settings (`WRITE_SECURE_SETTINGS`)** — optional, granted
  manually via adb. Used only to toggle the system grayscale (color correction)
  during focus sessions.
- **Uninstall packages (`REQUEST_DELETE_PACKAGES`)** — lets you uninstall an app
  from the long-press menu, via the standard system dialog.

All permissions are optional except the app list, and the app remains usable
without granting the optional ones.

## Children

Foco is not directed at children and does not knowingly collect any data from
anyone.

## Changes

If this policy changes, the updated version will be posted here.

## Contact

Questions about privacy? Contact the developer at: **<your-contact-email>**
