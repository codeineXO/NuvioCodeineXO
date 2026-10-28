# Windows x64 motion smoothing runtime

Source: https://github.com/allecsc/Stremio-Kai/releases/download/v4.8.0/Stremio-Kai_v4.8.0.7z
Archive SHA-256: 12781dcc0d6a0f8d937cef17adc81400e501f29b1a29fb8e3dfc6335d6c927c9

Python 3.12.0, VapourSynth R72, SVPFlow 4.3.0 and supporting DLLs are copied from
that archive. vapoursynth.dll is the archive's lib/site-packages/vapoursynth.dll.
VSScript.dll is the app's existing API 4.0-compatible adapter, retained unchanged.
The two .vpy profiles are the app's existing profiles, retained unchanged.
portable.vs is an empty local marker. python312._pth is a local adaptation with
explicit portable paths and site initialization disabled. No sitecustomize.py is
needed: this prevents Python from writing bytecode into the verified runtime or
importing packages from the working directory or PYTHONPATH.
The runtime contains no pip, WebView, MPV, FFmpeg, MVTools, or player application.

manifest.sha256 is the complete payload allowlist. Gradle verifies every hash and
packages only those entries plus the manifest on Windows. Other platforms do not
receive this payload. To update: verify the source archive hash, curate the same
explicit components, refresh applicable notices, regenerate per-file SHA-256
entries, and run the installer, native API, and interpolation tests. Do not build
this directory from a previously installed user's runtime or a recursive wildcard.

## Third-party notices

- Python 3.12.0: PSF license and bundled third-party notices in licenses/Python-3.12.txt.
  Source: https://github.com/python/cpython/tree/v3.12.0
- VapourSynth R72: LGPL 2.1 or later, licenses/VapourSynth-LGPL-2.1.txt.
  Source: https://github.com/vapoursynth/vapoursynth/tree/R72
- OpenSSL 3.0.11 (libcrypto-3.dll, libssl-3.dll): Apache 2.0,
  licenses/OpenSSL-Apache-2.0.txt. Source: https://github.com/openssl/openssl/tree/openssl-3.0.11
- libffi: MIT, licenses/libffi-MIT.txt. Source: https://github.com/libffi/libffi
- SQLite (sqlite3.dll): public domain, https://sqlite.org/copyright.html
- Microsoft Visual C++ runtime (msvcp140.dll, vcruntime140.dll, vcruntime140_1.dll):
  Microsoft redistribution terms; not an open-source component.
  https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files
- SVPFlow1: GPL; licenses/svpflow1_gpl.txt. Upstream source:
  https://www.svp-team.com/files/gpl/svpflow1-src.zip
- SVPFlow2: proprietary SVP processing-library terms in licenses/svp_libraries.txt;
  this file must not be described as open source or unrestricted redistribution.
  The upstream terms specify personal/noncommercial use and a Windows SVP Pro
  license; they do not establish blanket permission for distributing this app.
  Confirm applicable distribution rights before publishing a release.
- JsonCpp used by SVPFlow: MIT, licenses/jsoncpp_mit.txt.

The three SVP license files are copied verbatim from the official distribution:
https://www.svp-team.com/files/gpl/svpflow-4.3.0.168.zip
Its readme also warns that official Windows/macOS plugins require SVP Manager.
The Stremio-Kai binaries are retained exactly; compatibility and any vendor
licensing/runtime restrictions must be verified before release. No checks are bypassed.
