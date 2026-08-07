package com.newbieking.springtestgen.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Notifies the user about AI provider degradation events.
 *
 * Uses IntelliJ's notification system to display balloons when:
 * - A primary AI provider fails and fallback is used
 * - All AI providers fail and deterministic template is used instead
 * - AI response validation fails
 *
 * Notifications are throttled to avoid spamming the user with
 * repeated degradation messages within a short time window.
 */
object AIDegradationNotifier {

    private const val NOTIFICATION_GROUP_ID = "Spring Test Generator AI Degradation"

    /** Minimum interval between identical notifications (in ms). */
    private const val THROTTLE_INTERVAL_MS = 60_000L

    /** Track last notification time per provider to throttle. */
    private val lastNotificationTime = mutableMapOf<String, Long>()

    /**
     * Notify the user about a degradation event.
     *
     * @param project the current project
     * @param event the degradation event
     */
    fun notifyDegradation(project: Project, event: DegradationEvent) {
        // Throttle: don't notify about the same provider more than once per minute
        val now = System.currentTimeMillis()
        val lastTime = lastNotificationTime[event.failedProviderId] ?: 0
        if (now - lastTime < THROTTLE_INTERVAL_MS) return
        lastNotificationTime[event.failedProviderId] = now

        val (title, message, type) = when (event.fallbackType) {
            FallbackType.ALTERNATE_PROVIDER -> {
                Triple(
                    "AI Provider Fallback",
                    "Provider '${event.failedProviderId}' failed: ${event.failureReason}. " +
                        "Switched to '${event.fallbackProviderId}'.",
                    NotificationType.WARNING
                )
            }
            FallbackType.DETERMINISTIC_TEMPLATE -> {
                Triple(
                    "AI Unavailable — Using Deterministic Fallback",
                    "All AI providers failed. Using deterministic template generation instead. " +
                        "Last error: ${event.failureReason}",
                    NotificationType.WARNING
                )
            }
            FallbackType.SKIPPED -> {
                Triple(
                    "AI Generation Skipped",
                    "AI generation was skipped: ${event.failureReason}",
                    NotificationType.ERROR
                )
            }
        }

        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, message, type)
                .notify(project)
        } catch (e: Exception) {
            // Notification group may not be registered in test environments
            // Fall back to logging
            com.intellij.openapi.diagnostic.Logger.getInstance(AIDegradationNotifier::class.java)
                .warn("Failed to show degradation notification: ${e.message}")
        }
    }

    /**
     * Notify about a validation failure for AI-generated code.
     *
     * @param project the current project
     * @param errorMessage the validation error summary
     */
    fun notifyValidationFailure(project: Project, errorMessage: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                    "AI Response Validation Failed",
                    "The AI-generated code was rejected: $errorMessage. " +
                        "Falling back to deterministic generation.",
                    NotificationType.WARNING
                )
                .notify(project)
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(AIDegradationNotifier::class.java)
                .warn("Failed to show validation failure notification: ${e.message}")
        }
    }

    /** Clear throttle state (useful for testing). */
    fun clearThrottleState() {
        lastNotificationTime.clear()
    }
}