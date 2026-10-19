package com.example.samsunghealthcompanion

import android.app.Activity
import android.os.Looper
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import java.util.function.Consumer

/** Samsung's explicit per-data-type consent flow; write access is requested only from its opt-in flow. */
object SamsungHealthPermissions {
    private val nutritionWrite = setOf(
        Permission.of(DataTypes.NUTRITION, AccessType.READ),
        Permission.of(DataTypes.NUTRITION, AccessType.WRITE),
    )
    private val required = setOf(
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.SLEEP_GOAL, AccessType.READ),
        Permission.of(DataTypes.SLEEP_APNEA, AccessType.READ),
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
        Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ),
        Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
        Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ),
        Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ),
        Permission.of(DataTypes.SKIN_TEMPERATURE, AccessType.READ),
        Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ),
        Permission.of(DataTypes.STEPS, AccessType.READ),
        Permission.of(DataTypes.STEPS_GOAL, AccessType.READ),
        Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
        Permission.of(DataTypes.ACTIVE_CALORIES_BURNED_GOAL, AccessType.READ),
        Permission.of(DataTypes.ACTIVE_TIME_GOAL, AccessType.READ),
        Permission.of(DataTypes.EXERCISE, AccessType.READ),
        Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ),
        Permission.of(DataTypes.FLOORS_CLIMBED, AccessType.READ),
        Permission.of(DataTypes.NUTRITION, AccessType.READ),
        Permission.of(DataTypes.NUTRITION_GOAL, AccessType.READ),
        Permission.of(DataTypes.WATER_INTAKE, AccessType.READ),
        Permission.of(DataTypes.WATER_INTAKE_GOAL, AccessType.READ),
        Permission.of(DataTypes.USER_PROFILE, AccessType.READ),
    )

    fun request(
        activity: Activity,
        onGranted: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        requestPermissions(activity, required, onGranted, onError)
    }

    /** Invoke only after a user explicitly enables Samsung Health nutrition sync. */
    fun requestNutritionWrite(
        activity: Activity,
        onGranted: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        requestPermissions(activity, nutritionWrite, onGranted, onError)
    }

    private fun requestPermissions(
        activity: Activity,
        permissions: Set<Permission>,
        onGranted: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        try {
            val store = HealthDataService.getStore(activity.applicationContext)
            store.getGrantedPermissionsAsync(permissions).setCallback(
                Looper.getMainLooper(),
                Consumer { granted ->
                    if (granted.containsAll(permissions)) {
                        onGranted(true)
                    } else {
                        store.requestPermissionsAsync(permissions, activity).setCallback(
                            Looper.getMainLooper(),
                            Consumer { selected -> onGranted(selected.containsAll(permissions)) },
                            Consumer(onError),
                        )
                    }
                },
                Consumer(onError),
            )
        } catch (error: Throwable) {
            onError(error)
        }
    }
}
