# Vellum privacy notes

Vellum does not add analytics, advertising SDKs, telemetry, accounts, or a Vellum-operated backend. The Vellum-specific ambient canvas, Halo, icon, and welcome experience run entirely on the device.

Vellum is built on Lawnchair and retains its optional integrations. Depending on the features you choose, the app may:

- send search text to the web suggestion provider you select;
- read contacts for local contact search after you grant permission;
- connect to Smartspacer or a selected feed provider;
- fetch public release, contributor, or announcement information from GitHub or Lawnchair services; and
- read wallpaper colors, app metadata, notifications, or usage information after the corresponding Android permission is granted.

Permissions can be declined or revoked through Android Settings. Core launching, folders, gestures, the app drawer, and the Vellum visual experience do not require a Vellum account or a remote service.

For inherited implementation details, see the [Lawnchair privacy policy](https://lawnchair.app/privacy_policy) and inspect this repository's source code. Vellum is provided without warranty under the Apache License 2.0.
