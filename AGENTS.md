# vultisig-android — Agent Reference

## Overview

Android wallet app with Jetpack Compose UI and DKLS23 TSS implementation. Kotlin, Hilt DI, Room DB, MVVM architecture.

## Quick Start

```bash
git clone https://github.com/vultisig/vultisig-android.git
cd vultisig-android
git submodule update --init --recursive
# Set TRUSTWALLET_USER + TRUSTWALLET_PAT in ~/.gradle/gradle.properties (for WalletCore)
./gradlew assembleDebug
./gradlew test
```

## Before You Change Code

1. Run `./gradlew test` to establish baseline
2. If touching crypto/JNI: extra caution required — changes affect signing across all platforms
3. If touching commondata/: that's a submodule — changes go in the commondata repo
4. If adding/removing strings: update ALL 9 locale files (see Localization in CLAUDE.md)
5. If deleting a screen: follow the full deletion checklist in CLAUDE.md

## Patterns

- MVVM with Repository pattern
- Sealed classes for UI state (Loading, Success, Error)
- Coroutines + StateFlow for reactive state
- Hilt for dependency injection
- Jetpack Compose (Material 3) for all UI
- `safeLaunch` for network calls, `bodyOrThrow()` for API methods
- See `docs/network-error-handling.md` for error handling architecture

## Security Notes

- Never log key material or vault shares
- JNI crypto bindings — do not modify without review
- Credential requirements: WalletCore needs GitHub auth token
- Always test keygen and keysign flows after refactoring

## Knowledge Base

For deeper context beyond this file, see [vultisig-knowledge](https://github.com/vultisig/vultisig-knowledge).

Key docs for this repo:
- [repos/vultisig-android.md](https://github.com/vultisig/vultisig-knowledge/blob/main/repos/vultisig-android.md)
- [architecture/mpc-tss-explained.md](https://github.com/vultisig/vultisig-knowledge/blob/main/architecture/mpc-tss-explained.md)
- [architecture/signing-flow.md](https://github.com/vultisig/vultisig-knowledge/blob/main/architecture/signing-flow.md)
- [coding/gotchas.md](https://github.com/vultisig/vultisig-knowledge/blob/main/coding/gotchas.md) (see Android section)
