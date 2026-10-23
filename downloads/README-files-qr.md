# LocalLS 0.11.21 — files and QR fixes

Separately tappable checkboxes select files and folders, while a folder name opens the folder. Selection stays consistent with sorting/filtering and transfer sources. The native file browser has phone/server tabs on narrow windows and independent side-by-side panes on wide windows; lists scroll within the screen. Up/Refresh/Menu form one aligned row. Control sizing adapts to leave room for file rows without changing system font settings. Duplicate queue controls are removed from folder menus; cancellation/retry/results remain in transfer details. The main-screen Settings tile is removed; settings stay in the menu. Both client graphs and server/client role separation are retained.

The server QR is shown above the instructions and buttons, completely visible before scrolling. A stopped server can be started from that screen through the existing service with its saved settings and keys. Camera viewport and decoder framing are square. Returning from the camera restores the displayed QR. Key pinning, matching-code approval and permanent saved access remain mandatory; private keys never enter the QR.

UI references: [Files by Google](https://support.google.com/files/answer/9712028?hl=en) and [Solid Explorer](https://neatbytes.com/solidexplorer/index.php/2015/06/23/usage-basics-for-dummies/).

Validation: 299 JVM + 19 Python tests, zero failures/errors/skips, 102/102 original function references, all previous 291 test references retained. Full project/architecture audits, debug/release lint, signed release build and instrumentation APK build passed. Camera hardware and physical phones have not been tested. Preview PNGs use synthetic test states; the QR points only to a stopped private loopback test server, not a real user device.

Version 0.11.21 / code 41; package com.bitpoint.homeservercontrol. Same-version rebuild, not a new release. No VPN added. APK SHA-256: 361cab1cdce7f867a21f4750787f56fdf151a711ddfe82175c6ab36e60658162

Original signing certificate SHA-256: a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a

Install over the existing app without clearing its data. Existing signing keys, access keys and settings are retained. This delivery branch preserves the release source tree and only adds APK/checksum/previews/README. Functional source changes remain local and are not claimed to be committed here. The release branch and published release assets are unchanged.
