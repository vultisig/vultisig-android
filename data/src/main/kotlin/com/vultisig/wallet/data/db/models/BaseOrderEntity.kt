package com.vultisig.wallet.data.db.models

abstract class BaseOrderEntity {
    abstract val value: String
    abstract val order: Float
    abstract val parentId: String?
}
