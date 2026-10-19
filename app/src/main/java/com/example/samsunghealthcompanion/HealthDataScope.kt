package com.example.samsunghealthcompanion

/**
 * The first release's explicit, read-only consent scope.
 *
 * Keep this independent of Samsung's AAR so the UI and privacy contract compile before the SDK is
 * installed. When the AAR is added, map these entries one-for-one to DataTypes.SLEEP,
 * DataTypes.HEART_RATE, and DataTypes.BLOOD_OXYGEN with AccessType.READ.
 */
enum class HealthMetric(
    val consentLabel: String,
    val purpose: String,
    val initialRetention: String,
) {
    SLEEP(
        consentLabel = "Sleep",
        purpose = "Show local sleep sessions and duration summaries.",
        initialRetention = "View on demand; do not retain raw sessions by default.",
    ),
    HEART_RATE(
        consentLabel = "Heart rate",
        purpose = "Show local historical ranges and trends.",
        initialRetention = "View on demand; do not retain raw samples by default.",
    ),
    BLOOD_OXYGEN(
        consentLabel = "Blood oxygen",
        purpose = "Show sleep-related oxygen measurements when Samsung Health has recorded them.",
        initialRetention = "View on demand; do not retain raw samples by default.",
    ),
}

object HealthDataScope {
    val requestedMetrics = HealthMetric.entries
    const val access = "READ_ONLY"
    const val exportDefault = "DISABLED"
}
