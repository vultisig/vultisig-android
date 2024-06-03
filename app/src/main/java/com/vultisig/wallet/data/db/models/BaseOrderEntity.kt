package com.vultisig.wallet.data.db.models

internal abstract class BaseOrderEntity {
    abstract val value: String
    abstract val order: Float
    abstract val parentId: String?
}
