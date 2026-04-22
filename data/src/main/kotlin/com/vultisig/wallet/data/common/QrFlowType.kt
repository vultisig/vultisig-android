package com.vultisig.wallet.data.common

/** QR flow type for joining an existing keysign session. */
const val JOIN_KEYSIGN_FLOW = "SignTransaction"

/** QR flow type for joining a new vault keygen session. */
const val JOIN_KEYGEN_FLOW = "NewVault"

/** QR flow type for initiating a send to a pre-filled address. */
const val JOIN_SEND_ON_ADDRESS_FLOW = "SendOnAddress"

/** QR flow type for the standard send flow. */
const val SEND_FLOW = "Send"

/** QR flow type for TonConnect deep-link URIs. */
const val TONCONNECT_FLOW = "TonConnect"

/** QR flow type returned when no other type matches. */
const val UNKNOWN_FLOW = "Unknown"
