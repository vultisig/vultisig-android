# vultisig-android

vultisig android app

## Setup GITHUB personal token
This project use [Trust Wallet WalletCore](https://github.com/trustwallet/wallet-core), the WalletCore library is host on github , in order for gradle to access it , you need to have a github personal token and then set the following two ENV variables
You can also add it to your `~/.bashrc` or `~/.zshrc` file, more detail refer to [this guid] (https://developer.trustwallet.com/developer/wallet-core/integration-guide/android-guide)
```bash
export GITHUB_USER=your_github_user
export GITHUB_TOKEN=your_github_token 
```

[How to get a personal github token?] (https://github.com/settings/tokens)


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
  
