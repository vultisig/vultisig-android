@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface RequestQrScanUseCase {
    suspend operator fun invoke(): String?
}

internal class RequestQrScanUseCaseImpl @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val requestResultRepo: RequestResultRepository,
) : RequestQrScanUseCase {
    override suspend fun invoke(): String? {
        val requestId = Uuid.random().toString()
        navigator.route(Route.ScanQr(requestId = requestId))
        return requestResultRepo.request<String>(requestId)
    }
}