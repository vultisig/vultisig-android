package com.vultisig.wallet.data.usecases

import javax.inject.Inject
import kotlin.random.Random

interface GenerateServiceName : () -> String

internal class GenerateServiceNameImpl @Inject constructor() : GenerateServiceName {
    override fun invoke(): String = "vultisigApp-${Random.nextInt(1, 1000)}"
}