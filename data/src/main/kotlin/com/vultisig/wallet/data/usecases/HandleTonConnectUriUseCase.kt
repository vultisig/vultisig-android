package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.models.TonConnectSession
import com.vultisig.wallet.data.repositories.TonConnectRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Parses a TonConnect v2 URI, persists the resulting session, and returns the dApp client ID.
 * Returns null if the URI is invalid or missing required parameters.
 */
interface HandleTonConnectUriUseCase : suspend (String) -> String?

/** Default [HandleTonConnectUriUseCase] implementation. */
internal class HandleTonConnectUriUseCaseImpl
@Inject
constructor(private val tonConnectRepository: TonConnectRepository) : HandleTonConnectUriUseCase {

    /** Validates [uri] as a TonConnect v2 URI, persists the session, and returns the client ID. */
    override suspend fun invoke(uri: String): String? {
        val helper = DeepLinkHelper(uri)
        if (!helper.isTonConnectUri()) {
            Timber.w("Not a TonConnect URI: <redacted>")
            return null
        }

        val clientId = helper.getTonConnectClientId()
        if (clientId.isNullOrBlank()) {
            Timber.w("TonConnect URI missing client id")
            return null
        }

        // Validate clientId format (TonConnect v2 ephemeral public key: 64 hex chars)
        if (!clientId.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            Timber.w("TonConnect session rejected: invalid client id format")
            return null
        }

        val requestPayload = helper.getTonConnectRequest()
        if (requestPayload.isNullOrBlank()) {
            Timber.w("TonConnect URI missing request payload")
            return null
        }

        // Validate requestPayload is valid JSON
        val isValidJson =
            runCatching {
                    Json.parseToJsonElement(requestPayload)
                    true
                }
                .getOrElse { false }

        if (!isValidJson) {
            Timber.w("TonConnect session rejected: invalid request payload JSON")
            return null
        }

        val session =
            TonConnectSession(
                clientId = clientId,
                bridgeUrl = null,
                requestPayload = requestPayload,
                vaultId = null,
            )
        tonConnectRepository.saveSession(session)
        Timber.d("TonConnect session persisted")
        return clientId
    }
}
