package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.DeviceCapability

/** One attempt of the native analysis. */
public data class ExecutionPlan(
    val capability: DeviceCapability,
    val workingLongEdge: Int,
    val attempt: Int,
)

/** Memory-pressure recovery: retry once at half the working resolution, simplified (FR-013). */
public object RetryPolicy {
    public const val MAX_RETRIES: Int = 1

    public fun initial(
        capability: DeviceCapability,
        workingLongEdge: Int,
    ): ExecutionPlan = ExecutionPlan(capability, workingLongEdge, attempt = 0)

    /** The next plan after an out-of-memory failure, or null when retries are exhausted. */
    public fun afterOutOfMemory(plan: ExecutionPlan): ExecutionPlan? {
        if (plan.attempt >= MAX_RETRIES) return null
        return ExecutionPlan(
            capability = DeviceCapability.Simplified,
            workingLongEdge = (plan.workingLongEdge / 2).coerceAtLeast(WorkingResolution.MIN_LONG_EDGE),
            attempt = plan.attempt + 1,
        )
    }
}
