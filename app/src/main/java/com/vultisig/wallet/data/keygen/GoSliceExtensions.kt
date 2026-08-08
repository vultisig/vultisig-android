package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godilithium.BufferUtilJNI as MldsaBufferUtilJNI
import com.silencelaboratories.godilithium.go_slice as MldsaGoSlice
import com.silencelaboratories.godkls.BufferUtilJNI as DklsBufferUtilJNI
import com.silencelaboratories.godkls.go_slice as DklsGoSlice
import com.silencelaboratories.goschnorr.BufferUtilJNI as SchnorrBufferUtilJNI
import com.silencelaboratories.goschnorr.go_slice as SchnorrGoSlice

/**
 * Wraps this byte array in a `godkls` `go_slice` for passing across the JNI boundary.
 *
 * The three bindings each declare their own `go_slice`, so the same conversion needs one function
 * per binding; they differ only in return type and so cannot share a name.
 *
 * All three leak the buffer `set_bytes_on_go_slice` mallocs — the bindings expose no way to free
 * it. See https://github.com/vultisig/vultisig-android/issues/5543.
 */
internal fun ByteArray.toDklsGoSlice(): DklsGoSlice {
    val slice = DklsGoSlice()
    DklsBufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}

/** Wraps this byte array in a `godilithium` `go_slice` for passing across the JNI boundary. */
internal fun ByteArray.toMldsaGoSlice(): MldsaGoSlice {
    val slice = MldsaGoSlice()
    MldsaBufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}

/** Wraps this byte array in a `goschnorr` `go_slice` for passing across the JNI boundary. */
internal fun ByteArray.toSchnorrGoSlice(): SchnorrGoSlice {
    val slice = SchnorrGoSlice()
    SchnorrBufferUtilJNI.set_bytes_on_go_slice(slice, this)
    return slice
}
