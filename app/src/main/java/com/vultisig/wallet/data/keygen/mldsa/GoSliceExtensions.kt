package com.vultisig.wallet.data.keygen.mldsa

import com.silencelaboratories.godilithium.BufferUtilJNI
import com.silencelaboratories.godilithium.go_slice

/** Wraps this byte array in a godilithium [go_slice] for passing across the JNI boundary. */
internal fun ByteArray.toGoSlice(): go_slice {
    val slice = go_slice()
    BufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}
