package com.vultisig.wallet.data.models

sealed class PeerDiscoveryPayload {
    data class Keygen(val keygenMessage: KeygenMessage) : PeerDiscoveryPayload()
    data class Reshare(val reshareMessage: ReshareMessage) : PeerDiscoveryPayload()
}