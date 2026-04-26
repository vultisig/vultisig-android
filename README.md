# vultisig-android
[![Android CI](https://github.com/vultisig/vultisig-android/actions/workflows/android.yml/badge.svg)](https://github.com/vultisig/vultisig-android/actions/workflows/android.yml)
vultisig android app

## Setup GitHub personal token
This project uses [Trust Wallet WalletCore](https://github.com/trustwallet/wallet-core). Since WalletCore is hosted on GitHub Packages, Gradle needs a GitHub personal access token. Set the following environment variables:
You can also add it to your `~/.bashrc` or `~/.zshrc` file, more detail refer to [this guide](https://developer.trustwallet.com/developer/wallet-core/integration-guide/android-guide)
```bash
export TRUSTWALLET_USER=your_github_user
export TRUSTWALLET_PAT=your_github_token
```

[How to get a personal github token?] (https://github.com/settings/tokens)

### Migrating from GITHUB_TOKEN/GITHUB_USER

If you previously had `GITHUB_TOKEN` and `GITHUB_USER` set, rename them in your `~/.zshrc` or `~/.bashrc`:
```bash
# Before
export GITHUB_USER=your_github_user
export GITHUB_TOKEN=your_github_token

# After
export TRUSTWALLET_USER=your_github_user
export TRUSTWALLET_PAT=your_github_token
```

Then re-auth `gh` CLI (since it was previously using `GITHUB_TOKEN` for auth):
```bash
gh auth login --web --git-protocol https
gh auth refresh -h github.com -s workflow
```


## Git hooks setup
After cloning the repo, install the project git hooks so Kotlin files are automatically formatted before every commit:
```bash
sh scripts/install-git-hooks.sh
```
This installs a `pre-commit` hook that runs `ktfmtFormat` and re-stages any changed files.

## Test keygen with emulator

When keygen started , the main device will start a mediator server on port 18080, in order for your
pair device to access it , you will need to do the following

1. Setup port forwarding on the main device, using `adb`, `adb` will start to listen on port 18080
   on your emulator's host machine , but only on loopback interface

   ```bash
   adb forward tcp:18080 tcp:18080
   ```

2. use `socat` to listen on the host machine's network interface , and forward the connection to the
   loopback interface

   ```bash
   # my host machine's ip is 192.168.1.35 , update it to your own ip
   socat TCP-LISTEN:18080,bind=192.168.1.35,reuseaddr,fork TCP:localhost:18080
   ```

3. Override service discovery address, when mediator start the service , it register itself, however
   the Ip it register is the emulator's local ip, which is `10.0.2.16`, so the pair device will only
   find address as `10.0.2.16`, need to override it to your host machine's ip , in file https://github.com/vultisig/vultisig-android/blob/main/app/src/main/java/com/vultisig/wallet/ui/models/keygen/JoinKeygenViewModel.kt

## 💰 Bounty Contribution

- **Task:** THORChain LP add (ETH USDT) reverts: gas limit too low for depositWithExpiry on 
- **Reward:** $1
- **Source:** GitHub-Paid
- **Date:** 2026-04-27

