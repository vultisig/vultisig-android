package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.VaultId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vultisig.keysign.v1.CustomMessagePayload

data class CustomMessagePayloadDto(
    val id: String,
    val vaultId: VaultId,
    val payload: CustomMessagePayload,
)

interface CustomMessagePayloadRepo {
    fun add(payload: CustomMessagePayloadDto)

    suspend fun get(id: String): CustomMessagePayloadDto?
}

internal class CustomMessagePayloadRepoImpl @Inject constructor() : CustomMessagePayloadRepo {

    private val payloads = MutableStateFlow(mapOf<String, CustomMessagePayloadDto>())

    override fun add(payload: CustomMessagePayloadDto) {
        payloads.update { it + (payload.id to payload) }
    }

    override suspend fun get(id: String): CustomMessagePayloadDto? = payloads.value[id]
}
