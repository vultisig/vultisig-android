package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import java.math.BigInteger


fun BigInteger.toByteString(): ByteString =  ByteString.copyFrom(toByteArray())

