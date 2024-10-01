package com.vultisig.wallet.data.mappers

typealias MapperFunc<I, O> = (from: I) -> O

typealias SuspendMapperFunc<I, O> = suspend (from: I) -> O