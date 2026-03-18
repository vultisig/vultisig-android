# Vultisig Android - Claude Code Instructions

## Security Tier

STANDARD

## Critical Boundaries

- TSS/crypto JNI bindings — do not modify without explicit review from maintainers.
- `commondata/` — Git submodule. Do not edit directly, changes go in the commondata repo.
- `fastlane/` — Automated deployment configs. Changes affect releases.

## Project Overview

**Vultisig** is an Android cryptocurrency wallet application built with modern Android development practices. The app provides secure multi-signature wallet functionality with threshold signature scheme (TSS) support.

**Repository**: https://github.com/vultisig/vultisig-android

## Tech Stack

### Core Technologies
- **Language**: Kotlin (JVM Toolchain 21)
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with clean architecture principles
- **Dependency Injection**: Dagger Hilt
- **Navigation**: Jetpack Navigation Compose
- **Async**: Kotlin Coroutines + Flow
- **Database**: Room
- **Serialization**: Kotlinx Serialization
- **Build System**: Gradle (Kotlin DSL)

### Key Libraries
- **Trust Wallet Core**: Blockchain wallet core functionality
- **TSS Libraries**: mobile-tss-lib, dkls, goschnorr (custom AAR files)
- **AndroidX**: Core KTX, Lifecycle, DataStore, Biometric, Browser
- **Network**: Ktor client
- **Testing**: JUnit 5, Mockk, Kotest

### Android Specs
- **Namespace**: `com.vultisig.wallet`
- **Min SDK**: 26
- **Target SDK**: 35
- **Compile SDK**: 35

## Project Structure

```
vultisig-android/
├── app/                          # Main application module
│   └── src/main/java/com/vultisig/wallet/
│       ├── app/                  # Application class, activities
│       ├── data/                 # Data layer (repositories, models)
│       └── ui/                   # UI layer (screens, components, viewmodels)
│           ├── models/           # ViewModels
│           ├── navigation/       # Navigation setup
│           └── screens/          # Composable screens
├── data/                         # Data module (shared data layer)
├── commondata/                   # Common data models
├── config/                       # Lint and other configs
├── fastlane/                     # CI/CD automation
└── scripts/                      # Build and utility scripts
```

## Development Setup

### Prerequisites

1. **GitHub Personal Access Token** (Required)
   - The project uses Trust Wallet Core hosted on GitHub Packages
   - Set environment variables (add to `~/.zshrc` or `~/.bashrc`):
     ```bash
     export TRUSTWALLET_USER=your_github_username
     export TRUSTWALLET_PAT=your_github_personal_access_token
     ```
   - Get token from: https://github.com/settings/tokens

2. **Android Studio**: Latest stable version with Compose support
3. **JDK**: Version 21

### Testing with Emulator

For keygen/keysign testing with multiple emulators:
1. Forward port: `adb forward tcp:18080 tcp:18080`
2. Use `socat` for network forwarding (see README.md)
3. Override service discovery address in JoinKeygenViewModel.kt

## Code Conventions

### State Management
- **Use StateFlow/MutableStateFlow** for UI state in ViewModels
- **Immutable state objects**: Use `@Immutable` data classes for UI state
- **Avoid `runBlocking`**: Use `suspend` functions and proper coroutines
- **ViewModel pattern**:
  - ViewModels manage business logic and state
  - Screens/composables only handle UI rendering and user interactions
  - Use `collectAsState()` in composables to observe flows

### Coroutines
- Use `viewModelScope` for ViewModel coroutines
- Properly cancel jobs when needed (e.g., `job?.cancel()`)
- Use `LaunchedEffect` for side effects in composables
- Key `LaunchedEffect` properly to control re-execution

### Compose Best Practices
- Keep composables pure and focused on UI
- Move data loading logic to ViewModels
- Use `remember` and `derivedStateOf` appropriately
- Minimize recomposition by using stable parameters
- Use `@Composable` naming convention (PascalCase)

### Design & Figma Workflow

**Figma source of truth**: https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App

All UI work MUST be verified against the Figma design using the Figma MCP server. This is a **blocking requirement** — do not implement or modify UI based on assumptions or guesses.

#### When to use Figma MCP
- **Any new screen or component** — fetch the design before writing code
- **Any UI fix or visual change** — compare current implementation against Figma before and after changes
- **When the user provides a Figma URL** — always use `get_design_context` to fetch the design specs
- **When the user says something "doesn't match Figma"** — fetch the Figma node, get a screenshot, and compare against the implementation
- **Layout, spacing, typography, or color questions** — Figma is authoritative, not guesses

#### How to use Figma MCP
1. **If the Figma MCP server is not connected**, immediately ask the user to connect it before proceeding with UI work
2. Use `get_design_context` with the `fileKey` and `nodeId` extracted from Figma URLs to get code hints, screenshots, and design tokens
3. Use `get_screenshot` to visually compare the current app state against the Figma design
4. Extract exact values from Figma: colors (hex), font sizes (sp), spacing (dp), corner radii, border widths, opacity values
5. Do NOT hardcode colors or dimensions from memory — always verify against the Figma source

#### Figma URL parsing
- `figma.com/design/:fileKey/:fileName?node-id=:nodeId` — convert `-` to `:` in nodeId
- The main Vultisig App file key is `puB2fsVpPrBx3Sup7gaa3v`

#### Design implementation rules
- Match Figma exactly: colors, spacing, typography, corner radii, shadows, opacity
- Use the project's existing theme tokens (`Theme.v2.colors.*`, `Theme.brockmann.*`) when they map to Figma values
- When Figma uses colors not in the theme, define them as private `val` constants at the top of the screen file
- Always cross-reference Figma component names with existing Compose components in the codebase before creating new ones

#### Autopilot: Automatic Skill Routing for UI Work

When the user asks to work on UI, the following skills MUST be used automatically — do NOT ask the user which skill to use, just invoke it:

| User says... | Auto-trigger |
|---|---|
| "fix #3524" / "implement #3524" / "work on issue 3524" (any `ui-mismatch` or `missing-feature` labeled issue) | → `/implement-figma 3524` |
| "fix this UI bug" / "fix the swap button color" / any UI fix request | → `/implement-figma` (find the matching issue first, or use the Figma URL) |
| "audit the screens" / "check Figma" / "find mismatches" | → `/audit-figma all` |
| "audit SwapScreen" / "check this screen against Figma" | → `/audit-figma SwapScreen` |
| "create a PR" / "open PR" (when on a branch with UI changes) | → `/create-figma-pr` |
| Provides a Figma URL and asks to implement it | → `/implement-figma <url>` |

**How to detect `ui-mismatch` issues**: When the user references an issue number, check its labels:
```bash
gh issue view NUMBER --repo vultisig/vultisig-android --json labels --jq '.labels[].name'
```
If it has `ui-mismatch` or `missing-feature` label → use `/implement-figma`.

**Before/After Screenshots are MANDATORY** for all UI changes:
1. Before starting any UI change, capture the current state via PreviewActivity + ADB
2. After implementing, rebuild and capture the new state
3. The PR skill (`/create-figma-pr`) will BLOCK if no before/after evidence exists
4. There is NO exception — text-based comparison tables are NOT acceptable as a substitute for screenshots

**Screenshot rules**:
- Before/after screenshots MUST be **real Android renders** — NEVER use Figma screenshots as before/after
- Figma is only a design reference (the target to match), not a screenshot substitute
- Screenshots MUST show **full screen pages** as the end user would see them — NEVER show isolated components on an empty background. Always render the component inside its real parent screen with mock data so the screenshot looks like the actual app
- When a screen requires complex state to reach (e.g., completing a transaction, finishing keysign), **use mocked data** in PreviewActivity to simulate that state — do NOT skip the screenshot
- Primary method: **PreviewActivity + ADB screencap** — a debug-only activity (`app/src/debug/.../PreviewActivity.kt`) renders composables with mock data on the emulator, then `adb shell screencap` captures the result
- Add new screen previews to PreviewActivity as needed. Use the screen's inner composable (the one that accepts state/UiModel as parameters, not the one with ViewModel injection). Construct the UiModel/state directly with mock data
- Fallback: direct ADB navigation to the screen in the running app

**Screenshot hosting — NEVER commit images to the branch**:
- Upload screenshots to an external image host (e.g., imgur API: `curl -s -X POST "https://api.imgur.com/3/image" -H "Authorization: Client-ID 546c25a59c58ad7" -F "image=@file.png"`)
- Embed the returned URL in the PR description as markdown images so they render inline
- NEVER add screenshot files to the git branch — only use external links in the PR body

**PR description format for screenshots**:
```markdown
## Before / After

| Before | After |
|--------|-------|
| ![before](https://i.imgur.com/XXXXX.png) | ![after](https://i.imgur.com/YYYYY.png) |
```

#### Batch Implementation Mode

When the user says "implement all ui-mismatch issues" or "fix all tickets":
1. List all open `ui-mismatch` issues: `gh issue list --repo vultisig/vultisig-android --label ui-mismatch --json number,title`
2. Group issues by screen file (multiple issues often affect the same file)
3. Launch parallel agents — one per screen file group — each using the `/implement-figma` workflow
4. Each agent creates its own branch: `fix/ui-mismatch-SCREEN_NAME`
5. After all agents complete, create individual PRs with `/create-figma-pr`

### ViewModels
- Inject dependencies via Hilt constructor injection
- Use `@HiltViewModel` annotation
- Expose UI state via `StateFlow`
- Handle errors gracefully with sealed state classes
- Example state pattern:
  ```kotlin
  sealed class ScreenState {
      data object Loading : ScreenState()
      data class Success(val data: Data) : ScreenState()
      data class Error(val message: String) : ScreenState()
  }
  ```

### Navigation
- Use type-safe navigation with `Route` sealed class
- Pass minimal data through navigation arguments
- Load full data in destination ViewModels
- Use Hilt to scope ViewModels appropriately

### Dependency Injection
- Use Hilt for all dependency injection
- Use `@Inject constructor()` for ViewModel dependencies
- Use `@ApplicationContext` when Context is needed
- Scope ViewModels with `@HiltViewModel`

### Error Handling
- Validate data availability early in ViewModels
- Use sealed state classes for error states
- Provide meaningful error messages to users
- Log errors with Timber (use appropriate log levels)
- Use `safeLaunch` instead of `viewModelScope.launch` for coroutines that perform network calls or deserialization
- Use `bodyOrThrow()` instead of raw `.body<T>()` in API methods for consistent error wrapping
- See [docs/network-error-handling.md](docs/network-error-handling.md) for the full two-layer error handling architecture

### Localization
- When adding or modifying string resources in `app/src/main/res/values/strings.xml`, **always** add translations to all supported locale files:
  - `values-de/strings.xml` (German)
  - `values-es/strings.xml` (Spanish)
  - `values-hr/strings.xml` (Croatian)
  - `values-it/strings.xml` (Italian)
  - `values-nl/strings.xml` (Dutch)
  - `values-pt/strings.xml` (Portuguese)
  - `values-ru/strings.xml` (Russian)
  - `values-zh-rCN/strings.xml` (Chinese Simplified)
- Insert new keys in the same relative position across all locale files (next to surrounding keys)
- Before translating, **check existing strings in that locale file** for established terminology (e.g., grep for similar keys). Use the same terms — do not guess translations from English
- "Chains" means blockchains, not literal chains. Each locale has its own established term — e.g., Russian uses "Сети" (networks), German uses "Blockchains", Spanish uses "cadenas", Italian uses "catene", etc. Always match what the locale already uses

### Deleting Screens / Views
When a screen, composable, or UI component is removed from the app, **all resources exclusively used by it must also be deleted**. This includes:

#### String Resources
- Remove the string key from **every** locale file:
  - `values/strings.xml` (English — default)
  - `values-de/strings.xml` (German)
  - `values-es/strings.xml` (Spanish)
  - `values-hr/strings.xml` (Croatian)
  - `values-it/strings.xml` (Italian)
  - `values-nl/strings.xml` (Dutch)
  - `values-pt/strings.xml` (Portuguese)
  - `values-ru/strings.xml` (Russian)
  - `values-zh-rCN/strings.xml` (Chinese Simplified)
- Also remove any XML comment blocks (e.g. `<!-- Register Vault Screen -->`) that only annotated the deleted keys
- Before deleting a string key, **verify it is not referenced elsewhere** in the codebase (grep for the key name across all `.kt` and `.xml` files)

#### Drawable / Image Resources
- Delete any drawable files (`.xml`, `.webp`, `.png`, `.svg`, etc.) in `app/src/main/res/drawable*/` that are **only** referenced by the deleted screen
- Before deleting a drawable, **verify it is not used by any other screen or component** (grep for the drawable name across all `.kt` and `.xml` files)

#### Navigation
- Remove the screen's `Route` data class / object from `Navigation.kt`
- Remove the corresponding `composable<Route.ScreenName>` block from `NavGraph.kt`
- Remove the import of the deleted screen composable from `NavGraph.kt`

#### ViewModel & Model Files
- Delete the screen's `ViewModel` class file
- Delete any UI model / state data classes that are only used by the deleted screen

#### Settings / Menu Entries
- If the screen was reachable from a settings or menu list, remove:
  - The `SettingsItem` (or equivalent) data object from the ViewModel
  - Its entry in the menu item list
  - Its `when` branch in the click handler

#### Checklist
When deleting a screen, go through this checklist:
1. ✅ Delete the `Screen.kt` composable file
2. ✅ Delete the `ViewModel.kt` file
3. ✅ Remove the `Route` entry from `Navigation.kt`
4. ✅ Remove the `composable<Route.X>` block and import from `NavGraph.kt`
5. ✅ Remove the menu/settings item, list entry, and click handler (if applicable)
6. ✅ Delete all exclusively-used drawable resources
7. ✅ Remove all associated string keys from **all 9** locale `strings.xml` files
8. ✅ Remove orphaned XML comment blocks in strings files
9. ✅ Grep the entire codebase to confirm no remaining references to the deleted screen, route, strings, or drawables

## Git Workflow

### Branch Naming
- Feature branches: `username/issue_number_description` (e.g., `aminsato/3217_issue`)
- Keep branch names lowercase with underscores

### Commit Messages
- Use conventional commit style preferred
- Focus on "why" rather than "what"
- Add co-author when appropriate:
  ```
  Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
  ```

### Pull Requests
- Target branch: `main`
- Use descriptive PR titles (under 70 characters)
- Include summary with bullet points
- Add test plan
- Ensure all CI checks pass

### CI/CD
- GitHub Actions runs on all PRs
- Lint must pass (abortOnError = true)
- Build must succeed for all configurations

## Testing

### Test Structure
- Unit tests: `app/src/test/`
- Instrumented tests: `app/src/androidTest/`
- Use JUnit 5 (Jupiter) test platform
- Use Mockk for mocking
- Use Kotest for assertions

### Testing Conventions
- Test ViewModels with unit tests
- Use `HiltTestRunner` for instrumented tests
- Mock external dependencies
- Test state transitions thoroughly
- Test error scenarios

## Common Patterns

### Recent Refactorings
1. **Splash Screen** (Issue #3216): Android 12+ SplashScreen API implementation
2. **Keysign Flow** (Issue #3217): Refactored to use StateFlow, removed blocking calls

### Key Features
- **Keygen**: Multi-party key generation with TSS
- **Keysign**: Transaction signing with threshold signatures
- **Multi-chain support**: Bitcoin, Ethereum, Solana, etc.
- **Swap functionality**: Token swapping
- **Vault management**: Multi-signature vault creation and management

## Important Notes

### Security Considerations
- Never commit sensitive data (.env files, keys, credentials)
- Use proper input validation (especially for blockchain addresses)
- Follow OWASP Mobile security best practices
- Biometric authentication for sensitive operations

### Performance
- Use lazy loading for large lists
- Optimize Compose recompositions
- Profile database queries
- Minimize blocking operations on main thread

### Breaking Changes
- Always test keygen and keysign flows after refactoring
- Verify all transaction types: Send, Swap, Deposit, Sign
- Test with different vault configurations (signer counts)
- Validate QR code generation and peer discovery

## Resources

- Main branch: `main`
- CI Status: https://github.com/vultisig/vultisig-android/actions
- Figma (Mobile App): https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App
- Trust Wallet Core docs: https://developer.trustwallet.com/developer/wallet-core

## Quick Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Clean build
./gradlew clean

# Run lint check script
./check.sh
```

---

**Note**: This is an active project with frequent updates. Always pull latest changes from `main` before starting new work.

## Knowledge Base

For deeper context, see [vultisig-knowledge](https://github.com/vultisig/vultisig-knowledge). Read only when needed:

| Situation | Read |
|-----------|------|
| First time in this repo | [repos/vultisig-android.md](https://github.com/vultisig/vultisig-knowledge/blob/main/repos/vultisig-android.md) |
| Touching crypto/TSS code | [architecture/mpc-tss-explained.md](https://github.com/vultisig/vultisig-knowledge/blob/main/architecture/mpc-tss-explained.md) |
| Signing flow details | [architecture/signing-flow.md](https://github.com/vultisig/vultisig-knowledge/blob/main/architecture/signing-flow.md) |
| Cross-repo gotchas | [coding/gotchas.md](https://github.com/vultisig/vultisig-knowledge/blob/main/coding/gotchas.md) (see Android section) |
| Cross-platform changes | [repos/index.md](https://github.com/vultisig/vultisig-knowledge/blob/main/repos/index.md) (dependency graph) |
