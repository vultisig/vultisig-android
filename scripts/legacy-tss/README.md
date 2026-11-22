# Legacy GG20 Android library

`app/libs/mobile-tss-lib.aar` contains the Go bindings for legacy GG20 signing.
The build uses [Vultisig's mobile TSS source at 7d3bfb7](https://github.com/vultisig/mobile-tss-lib/commit/7d3bfb72149493b7e09f1d911ef9fcbe12ef9196).
The module files pin that source and the binding tool, BNB TSS library, and
Go dependencies. The source patch uses 64-bit integers while validating
derivation path components so the upstream code can build for 32-bit Android
without changing the permitted uint32 range.

Set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to an SDK with platform 36 and
NDK 28.2.13676358. The build selects Go 1.26.11 through the Go toolchain.
From the repository root, build a candidate AAR outside the checkout:

```sh
scripts/legacy-tss/build-android.sh /tmp/mobile-tss-lib.aar
```

The script verifies module downloads, runs the Go package tests, and packages
all four Android ABIs. Go build metadata records the pinned module revision
and the temporary source path for the compatibility patch. That path can
change the archive digest between builds.

The rebuilt library retains the prior Java signatures and native exports.
When replacing it, compare `classes.jar` with `javap -public -s` and check the
native exports and ELF load alignment for every ABI. Also run the Android
release build and the legacy key generation and signing flows.
