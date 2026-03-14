package com.vultisig.wallet.data.usecases.agent

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.agent.AgentActionResult
import com.vultisig.wallet.data.models.agent.AgentBackendAction
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

@Singleton
class AgentToolExecutor
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val addressBookRepository: AddressBookRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) {

    suspend fun execute(action: AgentBackendAction, vault: Vault): AgentActionResult {
        val params = action.params?.jsonObject
        val data: JsonElement =
            when (action.type) {
                "vault_info" -> handleVaultInfo(vault)
                "list_vaults" -> handleListVaults()
                "get_balances" -> handleGetBalances(vault)
                "get_portfolio" -> handleGetPortfolio(vault)
                "get_coins" -> handleGetCoins(vault)
                "get_chains" -> handleGetChains(vault)
                "get_chain_address",
                "get_addresses" -> handleGetAddresses(vault, params)
                "get_address_book" -> handleGetAddressBook(params)
                "get_market_price" -> handleGetMarketPrice(vault, params)
                "add_coin",
                "add_token" -> handleAddCoin(vault, params)
                "add_chain" -> handleAddChain(vault, params)
                "remove_coin",
                "remove_token" -> handleRemoveCoin(vault, params)
                "remove_chain" -> handleRemoveChain(vault, params)
                "add_address_book",
                "address_book_add" -> handleAddAddressBook(params)
                "delete_address_book",
                "address_book_remove" -> handleDeleteAddressBook(params)
                "search_token" -> handleSearchToken(vault, params)
                "sign_tx",
                "sign_typed_data" -> {
                    return AgentActionResult(
                        action = action.type,
                        actionId = action.id,
                        success = false,
                        error = "sign_requires_keysign_flow",
                    )
                }

                else -> {
                    return AgentActionResult(
                        action = action.type,
                        actionId = action.id,
                        success = true,
                        data = buildJsonObject { put("status", "handled_server_side") },
                    )
                }
            }
        return AgentActionResult(
            action = action.type,
            actionId = action.id,
            success = true,
            data = data,
        )
    }

    private fun handleVaultInfo(vault: Vault): JsonElement {
        val chains = vault.coins.filter { it.isNativeToken }.map { it.chain.raw }.distinct()
        val addresses = mutableMapOf<String, String>()
        for (coin in vault.coins) {
            if (coin.isNativeToken && coin.address.isNotBlank()) {
                addresses.putIfAbsent(coin.chain.raw, coin.address)
            }
        }
        return buildJsonObject {
            put("name", vault.name)
            put("pubkey_ecdsa", vault.pubKeyECDSA)
            put("pubkey_eddsa", vault.pubKeyEDDSA)
            put("chains", buildJsonArray { chains.forEach { add(JsonPrimitive(it)) } })
            put("addresses", JsonObject(addresses.mapValues { JsonPrimitive(it.value) }))
            put("coin_count", vault.coins.size)
        }
    }

    private suspend fun handleListVaults(): JsonElement {
        val vaults = vaultRepository.getAll()
        return buildJsonObject {
            put(
                "vaults",
                buildJsonArray {
                    for (v in vaults) {
                        add(
                            buildJsonObject {
                                put("name", v.name)
                                put("pubkey_ecdsa", v.pubKeyECDSA)
                                put("pubkey_eddsa", v.pubKeyEDDSA)
                            }
                        )
                    }
                },
            )
            put("count", vaults.size)
        }
    }

    private suspend fun handleGetBalances(vault: Vault): JsonElement {
        val balances = buildJsonArray {
            for (coin in vault.coins) {
                try {
                    val balance =
                        balanceRepository.getCachedTokenBalanceAndPrice(coin.address, coin)
                    val tokenValue = balance.tokenBalance.tokenValue ?: continue
                    add(
                        buildJsonObject {
                            put("chain", coin.chain.raw)
                            put("ticker", coin.ticker)
                            put("balance", tokenValue.decimal.toPlainString())
                            put(
                                "fiatBalance",
                                balance.tokenBalance.fiatValue?.value?.toPlainString() ?: "0",
                            )
                        }
                    )
                } catch (_: Exception) {
                    // skip coins with no cached balance
                }
            }
        }
        return buildJsonObject { put("balances", balances) }
    }

    private suspend fun handleGetPortfolio(vault: Vault): JsonElement {
        var total = java.math.BigDecimal.ZERO
        for (coin in vault.coins) {
            try {
                val balance = balanceRepository.getCachedTokenBalanceAndPrice(coin.address, coin)
                val fiat = balance.tokenBalance.fiatValue?.value ?: continue
                total = total.add(fiat)
            } catch (_: Exception) {
                // skip
            }
        }
        return buildJsonObject {
            put("totalFiatBalance", "$${total.setScale(2, java.math.RoundingMode.HALF_UP)}")
        }
    }

    private fun handleGetCoins(vault: Vault): JsonElement {
        return buildJsonObject {
            put(
                "coins",
                buildJsonArray {
                    for (coin in vault.coins) {
                        add(
                            buildJsonObject {
                                put("chain", coin.chain.raw)
                                put("ticker", coin.ticker)
                                put("contract_address", coin.contractAddress)
                                put("is_native_token", coin.isNativeToken)
                                put("decimals", coin.decimal)
                                put("address", coin.address)
                            }
                        )
                    }
                },
            )
            put("count", vault.coins.size)
        }
    }

    private fun handleGetChains(vault: Vault): JsonElement {
        val chains = vault.coins.filter { it.isNativeToken }.map { it.chain }.distinct()
        return buildJsonObject {
            put(
                "chains",
                buildJsonArray {
                    for (chain in chains) {
                        add(
                            buildJsonObject {
                                put("chain", chain.raw)
                                val nativeCoin =
                                    vault.coins.firstOrNull {
                                        it.chain == chain && it.isNativeToken
                                    }
                                put("ticker", nativeCoin?.ticker)
                                put("address", nativeCoin?.address)
                            }
                        )
                    }
                },
            )
            put("count", chains.size)
        }
    }

    private fun handleGetAddresses(vault: Vault, params: JsonObject?): JsonElement {
        val chainFilter = params?.get("chain")?.jsonPrimitive?.contentOrNull
        val coins =
            if (chainFilter != null) {
                val chain = resolveChain(chainFilter)
                if (chain != null) vault.coins.filter { it.chain == chain } else emptyList()
            } else {
                vault.coins
            }
        val addresses =
            coins.filter { it.isNativeToken && it.address.isNotBlank() }.distinctBy { it.chain }
        return buildJsonObject {
            put(
                "addresses",
                buildJsonArray {
                    for (coin in addresses) {
                        add(
                            buildJsonObject {
                                put("chain", coin.chain.raw)
                                put("ticker", coin.ticker)
                                put("address", coin.address)
                            }
                        )
                    }
                },
            )
            put("vault_name", vault.name)
            put("pubkey_ecdsa", vault.pubKeyECDSA)
            put("pubkey_eddsa", vault.pubKeyEDDSA)
        }
    }

    private suspend fun handleGetAddressBook(params: JsonObject?): JsonElement {
        val entries =
            try {
                addressBookRepository.getEntries()
            } catch (_: Exception) {
                emptyList()
            }
        val chainFilter = params?.get("chain")?.jsonPrimitive?.contentOrNull
        val filtered =
            if (chainFilter != null) {
                val chain = resolveChain(chainFilter)
                if (chain != null) entries.filter { it.chain == chain } else entries
            } else {
                entries
            }
        return buildJsonObject {
            put(
                "entries",
                buildJsonArray {
                    for (entry in filtered) {
                        add(
                            buildJsonObject {
                                put("title", entry.title)
                                put("address", entry.address)
                                put("chain", entry.chain.raw)
                            }
                        )
                    }
                },
            )
            put("total_count", filtered.size)
        }
    }

    private suspend fun handleGetMarketPrice(vault: Vault, params: JsonObject?): JsonElement {
        val asset =
            params?.get("asset")?.jsonPrimitive?.contentOrNull
                ?: return buildJsonObject { put("error", "missing asset parameter") }

        val coin = vault.coins.firstOrNull { it.ticker.equals(asset, ignoreCase = true) }
        if (coin != null) {
            val price = tokenPriceRepository.getCachedPrice(coin.priceProviderID, AppCurrency.USD)
            if (price != null) {
                return buildJsonObject {
                    put("asset", coin.ticker)
                    put("price", price.toPlainString())
                    put("fiat", "USD")
                }
            }
        }
        return buildJsonObject {
            put("asset", asset)
            put("error", "price_not_found_in_cache")
        }
    }

    private suspend fun handleAddCoin(vault: Vault, params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }

        val tokens = params["tokens"]?.jsonArray
        val tokenParams =
            if (tokens != null) {
                tokens.map { it.jsonObject }
            } else {
                listOf(params)
            }

        val results = buildJsonArray {
            for (tp in tokenParams) {
                val chainName = tp["chain"]?.jsonPrimitive?.contentOrNull ?: continue
                val ticker = tp["ticker"]?.jsonPrimitive?.contentOrNull ?: continue
                val contractAddress =
                    tp["contract_address"]?.jsonPrimitive?.contentOrNull
                        ?: tp["contractAddress"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                val decimals = tp["decimals"]?.jsonPrimitive?.intOrNull ?: 18

                try {
                    val chain = resolveChain(chainName) ?: error("Unknown chain: $chainName")
                    val isNative = contractAddress.isBlank()
                    val coin =
                        Coin(
                            chain = chain,
                            ticker = ticker,
                            logo = tp["logo"]?.jsonPrimitive?.contentOrNull ?: "",
                            address = "",
                            decimal = decimals,
                            hexPublicKey = "",
                            priceProviderID =
                                tp["priceProviderId"]?.jsonPrimitive?.contentOrNull
                                    ?: tp["price_provider_id"]?.jsonPrimitive?.contentOrNull
                                    ?: "",
                            contractAddress = contractAddress,
                            isNativeToken = isNative,
                        )
                    val (address, derivedPublicKey) =
                        chainAccountAddressRepository.getAddress(coin, vault)
                    val updatedCoin = coin.copy(address = address, hexPublicKey = derivedPublicKey)
                    vaultRepository.addTokenToVault(vault.id, updatedCoin)

                    add(
                        buildJsonObject {
                            put("chain", chain.raw)
                            put("ticker", ticker)
                            put("address", address)
                            put("contract_address", contractAddress)
                            put("success", true)
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "AgentTool: add_coin failed for $ticker on $chainName")
                    add(
                        buildJsonObject {
                            put("chain", chainName)
                            put("ticker", ticker)
                            put("success", false)
                            put("error", e.message)
                        }
                    )
                }
            }
        }
        return buildJsonObject { put("results", results) }
    }

    private suspend fun handleAddChain(vault: Vault, params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }

        val chains = params["chains"]?.jsonArray
        val chainParams =
            if (chains != null) {
                chains.map { it.jsonObject }
            } else {
                listOf(params)
            }

        val results = buildJsonArray {
            for (cp in chainParams) {
                val chainName = cp["chain"]?.jsonPrimitive?.contentOrNull ?: continue
                try {
                    val chain = resolveChain(chainName) ?: error("Unknown chain: $chainName")
                    val existing = vault.coins.any { it.chain == chain && it.isNativeToken }
                    if (existing) {
                        add(
                            buildJsonObject {
                                put("chain", chain.raw)
                                put("success", true)
                                put("error", "chain already added")
                            }
                        )
                        continue
                    }
                    val (address, derivedPublicKey) =
                        chainAccountAddressRepository.getAddress(chain, vault)
                    val nativeCoin =
                        Coin(
                            chain = chain,
                            ticker =
                                chain.feeUnit.takeIf {
                                    chain.standard !=
                                        com.vultisig.wallet.data.models.TokenStandard.EVM
                                } ?: "ETH".takeIf { chain == Chain.Ethereum } ?: chain.feeUnit,
                            logo = "",
                            address = address,
                            decimal = getDefaultDecimals(chain),
                            hexPublicKey = derivedPublicKey,
                            priceProviderID = "",
                            contractAddress = "",
                            isNativeToken = true,
                        )
                    vaultRepository.addTokenToVault(vault.id, nativeCoin)
                    add(
                        buildJsonObject {
                            put("chain", chain.raw)
                            put("ticker", nativeCoin.ticker)
                            put("address", address)
                            put("success", true)
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "AgentTool: add_chain failed for $chainName")
                    add(
                        buildJsonObject {
                            put("chain", chainName)
                            put("success", false)
                            put("error", e.message)
                        }
                    )
                }
            }
        }
        return buildJsonObject { put("results", results) }
    }

    private suspend fun handleRemoveCoin(vault: Vault, params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }
        val chainName = params["chain"]?.jsonPrimitive?.contentOrNull ?: ""
        val ticker = params["ticker"]?.jsonPrimitive?.contentOrNull ?: ""

        val chain = resolveChain(chainName)
        val coin =
            vault.coins.firstOrNull { c ->
                (chain == null || c.chain == chain) && c.ticker.equals(ticker, ignoreCase = true)
            }

        return if (coin != null) {
            vaultRepository.deleteTokenFromVault(vault.id, coin)
            buildJsonObject {
                put("chain", coin.chain.raw)
                put("ticker", coin.ticker)
                put("success", true)
            }
        } else {
            buildJsonObject {
                put("chain", chainName)
                put("ticker", ticker)
                put("success", false)
                put("error", "coin not found in vault")
            }
        }
    }

    private suspend fun handleRemoveChain(vault: Vault, params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }
        val chainName = params["chain"]?.jsonPrimitive?.contentOrNull ?: ""
        val chain =
            resolveChain(chainName)
                ?: return buildJsonObject {
                    put("chain", chainName)
                    put("success", false)
                    put("error", "unknown chain")
                }
        vaultRepository.deleteChainFromVault(vault.id, chain)
        return buildJsonObject {
            put("chain", chain.raw)
            put("success", true)
        }
    }

    private suspend fun handleAddAddressBook(params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }

        val entries = params["entries"]?.jsonArray
        val entryParams =
            if (entries != null) {
                entries.map { it.jsonObject }
            } else {
                listOf(params)
            }

        val results = buildJsonArray {
            for (ep in entryParams) {
                val title =
                    ep["title"]?.jsonPrimitive?.contentOrNull
                        ?: ep["name"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                val address = ep["address"]?.jsonPrimitive?.contentOrNull ?: ""
                val chainName = ep["chain"]?.jsonPrimitive?.contentOrNull ?: ""
                try {
                    val chain = resolveChain(chainName) ?: error("Unknown chain: $chainName")
                    addressBookRepository.add(
                        com.vultisig.wallet.data.models.AddressBookEntry(
                            chain = chain,
                            address = address,
                            title = title,
                        )
                    )
                    add(
                        buildJsonObject {
                            put("title", title)
                            put("address", address)
                            put("chain", chain.raw)
                            put("success", true)
                        }
                    )
                } catch (e: Exception) {
                    add(
                        buildJsonObject {
                            put("title", title)
                            put("address", address)
                            put("chain", chainName)
                            put("success", false)
                            put("error", e.message)
                        }
                    )
                }
            }
        }
        return buildJsonObject { put("results", results) }
    }

    private suspend fun handleDeleteAddressBook(params: JsonObject?): JsonElement {
        params ?: return buildJsonObject { put("error", "missing params") }

        val entries = params["entries"]?.jsonArray
        val entryParams =
            if (entries != null) {
                entries.map { it.jsonObject }
            } else {
                listOf(params)
            }

        val results = buildJsonArray {
            for (ep in entryParams) {
                val address = ep["address"]?.jsonPrimitive?.contentOrNull ?: ""
                val chainName = ep["chain"]?.jsonPrimitive?.contentOrNull ?: ""
                try {
                    val chain = resolveChain(chainName) ?: error("Unknown chain: $chainName")
                    addressBookRepository.delete(chain.id, address)
                    add(
                        buildJsonObject {
                            put("address", address)
                            put("chain", chain.raw)
                            put("success", true)
                        }
                    )
                } catch (e: Exception) {
                    add(
                        buildJsonObject {
                            put("address", address)
                            put("chain", chainName)
                            put("success", false)
                            put("error", e.message)
                        }
                    )
                }
            }
        }
        return buildJsonObject { put("results", results) }
    }

    private fun handleSearchToken(vault: Vault, params: JsonObject?): JsonElement {
        val query = params?.get("query")?.jsonPrimitive?.contentOrNull ?: ""
        val chainFilter = params?.get("chain")?.jsonPrimitive?.contentOrNull

        val matchingCoins =
            vault.coins.filter { coin ->
                val matchesQuery =
                    coin.ticker.contains(query, ignoreCase = true) ||
                        coin.chain.raw.contains(query, ignoreCase = true) ||
                        coin.contractAddress.contains(query, ignoreCase = true)
                val matchesChain =
                    if (chainFilter != null) {
                        val chain = resolveChain(chainFilter)
                        chain != null && coin.chain == chain
                    } else true
                matchesQuery && matchesChain
            }

        return buildJsonObject {
            put(
                "results",
                buildJsonArray {
                    for (coin in matchingCoins) {
                        add(
                            buildJsonObject {
                                put("chain", coin.chain.raw)
                                put("ticker", coin.ticker)
                                put("contract_address", coin.contractAddress)
                                put("decimals", coin.decimal)
                                put("is_native", coin.isNativeToken)
                                put("already_in_vault", true)
                            }
                        )
                    }
                },
            )
            put("count", matchingCoins.size)
        }
    }

    private fun resolveChain(name: String): Chain? =
        try {
            Chain.fromRaw(name)
        } catch (_: Exception) {
            Chain.entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.raw.equals(name, ignoreCase = true)
            }
        }

    private fun getDefaultDecimals(chain: Chain): Int =
        when (chain.standard) {
            com.vultisig.wallet.data.models.TokenStandard.EVM -> 18
            com.vultisig.wallet.data.models.TokenStandard.UTXO -> 8
            com.vultisig.wallet.data.models.TokenStandard.SOL -> 9
            com.vultisig.wallet.data.models.TokenStandard.COSMOS,
            com.vultisig.wallet.data.models.TokenStandard.THORCHAIN -> 8

            else -> 8
        }
}
