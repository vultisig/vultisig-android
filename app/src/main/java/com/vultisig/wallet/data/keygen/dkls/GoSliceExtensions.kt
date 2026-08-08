package com.vultisig.wallet.data.keygen.dkls

import com.silencelaboratories.godkls.BufferUtilJNI
import com.silencelaboratories.godkls.go_slice

/** Wraps this byte array in a godkls [go_slice] for passing across the JNI boundary. */
internal fun ByteArray.toGoSlice(): go_slice {
    val slice = go_slice()
    BufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}
