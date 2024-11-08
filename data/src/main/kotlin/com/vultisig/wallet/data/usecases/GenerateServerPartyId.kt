package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX
import javax.inject.Inject
import kotlin.random.Random

interface GenerateServerPartyId : () -> String

internal class GenerateServerPartyIdImpl @Inject constructor() : GenerateServerPartyId {
    override fun invoke(): String = "$LOCAL_PARTY_ID_PREFIX-${Random.nextInt(100, 999)}"
}