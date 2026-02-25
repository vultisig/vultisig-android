package com.vultisig.wallet.data.api.models

sealed interface BlockChainStatusDeserialized {
    data class Result(val data: BlockChairStatusResponse) : BlockChainStatusDeserialized
    data class Error(val errorMessage: String) : BlockChainStatusDeserialized
}