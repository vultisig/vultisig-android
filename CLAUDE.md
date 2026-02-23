# Vultisig Android - Claude Code Instructions

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
     export GITHUB_USER=your_github_username
     export GITHUB_TOKEN=your_github_personal_access_token
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
