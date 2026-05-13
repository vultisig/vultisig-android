@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Navigates to the address-book screen and returns the entry selected by the user, or null. */
fun interface RequestAddressBookEntryUseCase {
    suspend operator fun invoke(chainId: String, excludeVaultId: String): AddressBookEntry?
}

internal class RequestAddressBookEntryUseCaseImpl
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
) : RequestAddressBookEntryUseCase {
    override suspend fun invoke(chainId: String, excludeVaultId: String): AddressBookEntry? {
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.AddressBook(
                requestId = requestId,
                chainId = chainId,
                excludeVaultId = excludeVaultId,
            )
        )
        return requestResultRepository.request(requestId)
    }
}
