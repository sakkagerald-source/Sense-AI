package com.senseassistant.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(
    val id: Long,
    val message: String,
    val timeMillis: Long
)

object ReminderStore {

    private const val PREFS = "sense_reminders"
    private const val KEY = "reminder_list"

    fun getAll(context: Context): MutableList<Reminder> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]") ?: "[]"
        val result = mutableListOf<Reminder>()

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                result.add(
                    Reminder(
                        id = item.getLong("id"),
                        message = item.getString("message"),
                        timeMillis = item.getLong("timeMillis")
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }

        return result
    }

    fun save(context: Context, reminder: Reminder) {
        val reminders = getAll(context)
        reminders.removeAll { it.id == reminder.id }
        reminders.add(reminder)
        write(context, reminders)
    }

    fun remove(context: Context, id: Long) {
        val reminders = getAll(context)
        reminders.removeAll { it.id == id }
        write(context, reminders)
    }

    private fun write(context: Context, reminders: List<Reminder>) {
        val array = JSONArray()

        reminders.forEach { reminder ->
            val item = JSONObject().apply {
                put("id", reminder.id)
                put("message", reminder.message)
                put("timeMillis", reminder.timeMillis)
            }
            array.put(item)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
