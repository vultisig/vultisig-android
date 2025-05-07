# Bug: Thorchain Autodiscovery Prevents User Token Selection

## Problem
Currently, the autodiscovery feature for Thorchain tokens always toggles tokens on if present on address, regardless of explicit user selection. This results in the user's manual selection (enable/disable) of a token being ignored or overridden by autodiscovery, making it impossible for the user to reliably set the state of individual tokens.

## Expected Behavior
- When a user manually selects or deselects a token, their choice should take precedence over autodiscovery for that specific token.
- Autodiscovery should always run if tokens are present, but it should only affect tokens that have not been explicitly set by the user.

## Proposed Solution
1. **Track User Overrides:**
   - Introduce a data structure (e.g., `userOverriddenTokenIds: Set<String>`) to keep track of tokens that the user has explicitly enabled or disabled.
2. **Update Token Selection Logic:**
   - In the token selection ViewModel (e.g., `TokenSelectionViewModel`), when the user enables or disables a token, add or remove the token ID to/from `userOverriddenTokenIds`.
3. **Modify Autodiscovery Logic:**
   - Autodiscovery should always execute if tokens are present, but when determining which tokens to enable/disable, it must skip any token present in `userOverriddenTokenIds`, leaving those tokens in the state set by the user.

## Implementation Steps
- Update the ViewModel to maintain a set of user-overridden token IDs.
- Update the `enableToken` and `disableToken` methods to update this set accordingly.
- In the autodiscovery process, check if a token is in the user-overridden set before toggling its state.
- Optionally, persist the user-overridden set if needed for consistency across app restarts.

---

This approach ensures that user intent always takes priority for individual tokens, solving the issue where autodiscovery could override manual user actions.