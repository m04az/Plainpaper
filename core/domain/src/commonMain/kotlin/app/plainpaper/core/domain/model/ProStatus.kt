package app.plainpaper.core.domain.model

/** Whether the one-time Pro purchase is active. Always free until Feature 9. */
public data class ProStatus(
    val isPro: Boolean,
) {
    public companion object {
        public val FREE: ProStatus = ProStatus(isPro = false)
    }
}
