package com.theveloper.pixelplay.presentation.components

import java.util.Calendar

/** Parts of the day Home reacts to: the greeting and the "Right now" mix. */
enum class Daypart(val label: String, val greeting: String) {
    Morning("morning", "Morning"),
    Afternoon("afternoon", "Afternoon"),
    Evening("evening", "Evening"),
    Night("night", "Night");

    companion object {
        /** Morning 5–11, Afternoon 12–16, Evening 17–21, Night 22–4. */
        fun of(hour: Int): Daypart = when (hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..21 -> Evening
            else -> Night
        }

        fun now(calendar: Calendar = Calendar.getInstance()): Daypart =
            of(calendar.get(Calendar.HOUR_OF_DAY))

        /** Daypart a played-at timestamp falls in, in the device's time zone. */
        fun ofTimestamp(epochMillis: Long): Daypart {
            val c = Calendar.getInstance()
            c.timeInMillis = epochMillis
            return of(c.get(Calendar.HOUR_OF_DAY))
        }
    }
}
