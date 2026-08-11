package com.vultisig.wallet.data.common

data class AppZipEntry(val name: String, val content: String)

/**
 * Outcome of walking a `.zip` backup.
 *
 * @property entries the importable entries collected from the archive.
 * @property isComplete `false` when traversal stopped early — a size or entry cap was hit, or an
 *   entry could not be read — so [entries] may hold fewer vault shares than the archive contains.
 */
data class AppZipContents(val entries: List<AppZipEntry>, val isComplete: Boolean)
