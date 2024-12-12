package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.VaultId
import vultisig.keysign.v1.CustomMessagePayload
import javax.inject.Inject

data class CustomMessagePayloadDto(
    val id: String,
    val vaultId: VaultId,
    val payload: CustomMessagePayload,
)

interface CustomMessagePayloadRepo {
    fun add(payload: CustomMessagePayloadDto)
    fun get(id: String): CustomMessagePayloadDto?
}

internal class CustomMessagePayloadRepoImpl @Inject constructor() : CustomMessagePayloadRepo {
    private val payloads = mutableMapOf<String, CustomMessagePayloadDto>()

    override fun add(payload: CustomMessagePayloadDto) {
        payloads[payload.id] = payload
    }

    override fun get(id: String): CustomMessagePayloadDto? {
        return payloads[id]
    }
}

