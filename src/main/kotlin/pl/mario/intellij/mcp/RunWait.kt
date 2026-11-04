package pl.mario.intellij.mcp

enum class WaitVerdict { WAIT, DONE, DONE_TREE_UNFINISHED }

private const val DEFAULT_TIMEOUT_SECONDS = 300
private const val MAX_TIMEOUT_SECONDS = 1800

fun waitVerdict(
    processEnded: Boolean,
    hasTestTree: Boolean,
    treeFinished: Boolean,
    processEndedAtMillis: Long?,
    nowMillis: Long,
    graceMillis: Long = 5_000,
): WaitVerdict =
    when {
        !processEnded -> WaitVerdict.WAIT
        !hasTestTree || treeFinished -> WaitVerdict.DONE
        processEndedAtMillis == null -> WaitVerdict.WAIT
        nowMillis - processEndedAtMillis < graceMillis -> WaitVerdict.WAIT
        else -> WaitVerdict.DONE_TREE_UNFINISHED
    }

fun clampTimeout(requested: Int?): Pair<Int, Boolean> {
    val value = requested ?: return DEFAULT_TIMEOUT_SECONDS to false
    val clamped = value.coerceIn(0, MAX_TIMEOUT_SECONDS)
    return clamped to (clamped != value)
}
