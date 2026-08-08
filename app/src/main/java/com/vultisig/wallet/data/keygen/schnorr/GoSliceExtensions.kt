package com.vultisig.wallet.data.keygen.schnorr

import com.silencelaboratories.goschnorr.BufferUtilJNI
import com.silencelaboratories.goschnorr.go_slice

/** Wraps this byte array in a goschnorr [go_slice] for passing across the JNI boundary. */
internal fun ByteArray.toGoSlice(): go_slice {
    val slice = go_slice()
    BufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}
