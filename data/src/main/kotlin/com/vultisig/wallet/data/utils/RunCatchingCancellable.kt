package com.vultisig.wallet.data.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation-aware variant of [kotlin.runCatching].
 *
 * Catches [Throwable] into a [Result] just like the stdlib version, but rethrows
 * [CancellationException] to preserve structured-concurrency semantics — otherwise a coroutine
 * cancellation inside [block] would be swallowed into a silent `Result.failure`.
 *
 * Compose freely with [Result.getOrNull], [Result.getOrElse], [Result.map], etc.
 *
 * ```
 * val value = runCatchingCancellable { repository.fetch() }.getOrNull()
 * val or throw = runCatchingCancellable { parse(input) }
 *     .getOrElse { throw MyDomainException(it) }
 * ```
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
