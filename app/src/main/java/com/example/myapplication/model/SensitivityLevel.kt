package com.example.myapplication.model

/** 노브 회전 감도. 한 칸(step)을 올리기 위해 손가락이 돌아야 하는 각도. */
enum class SensitivityLevel(val id: Int, val angleDeg: Double) {
    LOW(1, 24.0),
    NORMAL(2, 16.0),
    HIGH(3, 10.0);

    companion object {
        val DEFAULT = NORMAL

        fun fromId(id: Int): SensitivityLevel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
