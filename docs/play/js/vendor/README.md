# Third-party code

## jsQR 1.4.0 (`jsQR.js`)

Reads QR codes from camera frames for Play Nearby ("Scan" buttons). It is loaded only when someone taps Scan.

- Source: <https://github.com/cozmo/jsQR>, npm package `jsqr` 1.4.0 (<https://registry.npmjs.org/jsqr/-/jsqr-1.4.0.tgz>)
- Licence: Apache-2.0, see `jsQR-LICENSE.txt`
- File: `package/dist/jsQR.js` from the tarball, unchanged (257 KB, an unminified webpack bundle)
- Tarball integrity (checked against the registry): `sha512-dxLob7q65Xg2DvstYkRpkYtmKm2sPJ9oFhrhmudT1dZvNFFTlroai3AWSpLey/w5vMcLBXRgOJsbXpdN9HzU/A==`
- It makes no network requests and does not touch the page; it only turns pixels into text.

Added 2026-11-09. To update it, download the new tarball, check it against the registry's integrity value, replace the file, and run `node --test web-tests/qr-scan.test.mjs`.
