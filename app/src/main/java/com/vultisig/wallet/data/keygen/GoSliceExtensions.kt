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
 * Callers must call [free] once done with the slice — every dkls-android native function takes
 * `go_slice` as `const`, i.e. it only reads it for the duration of that one call and never retains
 * the pointer, so it's safe to free right after the slice's last use.
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

/** Zeroizes and frees the native payload backing this slice. Safe to call more than once. */
internal fun DklsGoSlice.free() = DklsBufferUtilJNI.free_go_slice_payload(this)

/** Zeroizes and frees the native payload backing this slice. Safe to call more than once. */
internal fun MldsaGoSlice.free() = MldsaBufferUtilJNI.free_go_slice_payload(this)

/** Zeroizes and frees the native payload backing this slice. Safe to call more than once. */
internal fun SchnorrGoSlice.free() = SchnorrBufferUtilJNI.free_go_slice_payload(this)

/** Wraps this array in a `godkls` `go_slice` for [block], freeing it afterwards. */
internal inline fun <T> ByteArray.withDklsGoSlice(block: (DklsGoSlice) -> T): T {
    val slice = toDklsGoSlice()
    try {
        return block(slice)
    } finally {
        slice.free()
    }
}

/** Wraps this array in a `godilithium` `go_slice` for [block], freeing it afterwards. */
internal inline fun <T> ByteArray.withMldsaGoSlice(block: (MldsaGoSlice) -> T): T {
    val slice = toMldsaGoSlice()
    try {
        return block(slice)
    } finally {
        slice.free()
    }
}

/** Wraps this array in a `goschnorr` `go_slice` for [block], freeing it afterwards. */
internal inline fun <T> ByteArray.withSchnorrGoSlice(block: (SchnorrGoSlice) -> T): T {
    val slice = toSchnorrGoSlice()
    try {
        return block(slice)
    } finally {
        slice.free()
    }
}
