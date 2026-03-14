package com.vultisig.wallet.data.usecases.agent

import com.vultisig.wallet.data.models.agent.AgentBackendAction

private val alwaysAutoExecute =
    setOf(
        "add_chain",
        "add_coin",
        "remove_coin",
        "remove_chain",
        "address_book_add",
        "address_book_remove",
        "get_address_book",
        "get_market_price",
        "get_balances",
        "get_portfolio",
        "search_token",
        "list_vaults",
        "plugin_list",
        "plugin_spec",
        "plugin_installed",
        "plugin_uninstall",
        "build_swap_tx",
        "build_send_tx",
        "build_custom_tx",
        "mcp_status",
        "sign_tx",
        "read_evm_contract",
        "scan_tx",
        "thorchain_query",
    )

private val passwordRequired =
    setOf("plugin_install", "create_policy", "delete_policy", "sign_tx", "sign_typed_data")

private val confirmationRequired = setOf("plugin_install", "create_policy", "delete_policy")

private val actionTypeToToolName =
    mapOf(
        "address_book_add" to "add_address_book_entry",
        "address_book_remove" to "remove_address_book_entry",
        "create_policy" to "policy_add",
        "delete_policy" to "policy_delete",
        "build_swap_tx" to "build_swap_tx",
        "build_send_tx" to "build_send_tx",
        "build_custom_tx" to "build_custom_tx",
    )

fun shouldAutoExecute(action: AgentBackendAction): Boolean =
    action.type in alwaysAutoExecute || action.autoExecute

fun filterProtectedActions(
    actions: List<AgentBackendAction>
): Pair<List<AgentBackendAction>, List<AgentBackendAction>> {
    val unprotected = mutableListOf<AgentBackendAction>()
    val protected = mutableListOf<AgentBackendAction>()
    for (action in actions) {
        val isProtected =
            (action.type in passwordRequired || action.type in confirmationRequired) &&
                action.type !in alwaysAutoExecute
        if (isProtected) protected.add(action) else unprotected.add(action)
    }
    return unprotected to protected
}

fun filterAutoActions(actions: List<AgentBackendAction>): List<AgentBackendAction> =
    actions.filter(::shouldAutoExecute)

fun filterNonAutoActions(actions: List<AgentBackendAction>): List<AgentBackendAction> =
    actions.filterNot(::shouldAutoExecute)

fun filterBuildTx(
    actions: List<AgentBackendAction>
): Pair<List<AgentBackendAction>, AgentBackendAction?> {
    var build: AgentBackendAction? = null
    val remaining = mutableListOf<AgentBackendAction>()
    for (action in actions) {
        if (action.type in setOf("build_swap_tx", "build_send_tx", "build_custom_tx")) {
            build = action
        } else {
            remaining.add(action)
        }
    }
    return remaining to build
}

fun filterSignTx(
    actions: List<AgentBackendAction>
): Pair<List<AgentBackendAction>, AgentBackendAction?> {
    var sign: AgentBackendAction? = null
    val remaining = mutableListOf<AgentBackendAction>()
    for (action in actions) {
        if (action.type == "sign_tx") {
            sign = action
        } else {
            remaining.add(action)
        }
    }
    return remaining to sign
}

fun resolveToolName(actionType: String): String = actionTypeToToolName[actionType] ?: actionType

fun needsPassword(actionType: String): Boolean = actionType in passwordRequired

fun needsConfirmation(actionType: String): Boolean = actionType in confirmationRequired
