package com.vultisig.wallet.data.mappers

interface Mapper<I, O> {
    fun map(from: I): O
}