# Android release provenance

Dispatch the Android release provenance workflow from the version tag after the version bump is part of that tag. The checkout includes the pinned `commondata` submodule. The workflow builds the unsigned APK with JDK 21 and the Gradle distribution verified by the wrapper.

The workflow checks the tag against the checkout, APK version, and embedded Git revision. It compares each packaged crypto library with the verified Wallet Core AAR and the four committed MPC AARs. The resulting JSON records the APK digest, source commit, submodule revisions, input AAR digests, and native library digests for each ABI.

Download the unsigned APK and JSON from the workflow run and check their provenance before signing.

```sh
gh attestation verify app-release-unsigned.apk --repo vultisig/vultisig-android
gh attestation verify native-inventory.json --repo vultisig/vultisig-android
```

These attestations apply to the downloaded bytes. Signing changes the APK digest. The Play and release signing processes run outside this workflow and must attest the final distributed bytes independently. The workflow uploads build artifacts for release signers to review.

## Crypto inputs

Wallet Core and its proto dependency resolve exclusively through Trust Wallet's GitHub Packages repository. Gradle verifies checksums for the reviewed version, its publication metadata, and source archives. When updating Wallet Core, obtain the checksums by authenticating to that registry, cross-check its published module metadata, and update the version catalog and verification metadata together. The verification file currently scopes the checksums to the Trust Wallet publication.

The inventory pins the committed MPC AARs by Git blob and SHA-256 and verifies their native libraries against the APK. Their source revisions and build tools are unverified where the repository has only the compiled archive. Publish the source revision, toolchain versions, and build recipe with each future MPC binary update.
