
package com.senseassistant.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var input: EditText

    private val reminders = mutableListOf<Reminder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        reminders.addAll(ReminderStore.getAll(this))
        reminders.sortBy { it.timeMillis }

        requestNotificationPermission()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFF07111F.toInt())
        }

        val title = TextView(this).apply {
            text = "S E N S E"
            textSize = 32f
            setTextColor(0xFF62D9FF.toInt())
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "YOUR PERSONAL AI ASSISTANT"
            textSize = 12f
            setTextColor(0xFFB7C9D8.toInt())
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "SENSE SIAP"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 42, 0, 30)
        }

        input = EditText(this).apply {
            hint = "Ketik perintah atau catatan..."
            setHintTextColor(0xFF91A6B8.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(false)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        addButton(root, "KIRIM PERINTAH") {
            handleCommand(input.text.toString())
        }

        addButton(root, "BICARA DENGAN SENSE") {
            startVoiceInput()
        }

        addButton(root, "BUAT REMINDER") {
            createReminder()
        }

        addButton(root, "BACA AGENDA") {
            readAgenda()
        }

        addButton(root, "IZINKAN ALARM TEPAT") {
            requestExactAlarmPermission()
        }

        setContentView(root)
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 12
            }
        )
    }

    private fun handleCommand(command: String) {
        val text = command.trim()

        if (text.isEmpty()) {
            speak("Silakan ketik perintah Anda.")
            return
        }

        val reply = when {
            text.contains("halo", true) ||
                text.contains("selamat pagi", true) ->
                "Halo. Saya SENSE, asisten pribadi Anda. Ada yang bisa saya bantu?"

            text.contains("agenda", true) ||
                text.contains("reminder", true) ->
                buildAgendaMessage()

            else ->
                "Perintah diterima: $text. Fitur percakapan AI online akan ditambahkan pada tahap berikutnya."
        }

        status.text = reply
        speak(reply)
    }

    private fun startVoiceInput() {
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Silakan berbicara dengan SENSE"
            )
        }

        try {
            startActivityForResult(intent, 101)
        } catch (_: Exception) {
            speak("Pengenalan suara tidak tersedia pada perangkat ini.")
        }
    }

    @Deprecated("Deprecated by Android, retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            val command = words?.firstOrNull().orEmpty()
            input.setText(command)
            handleCommand(command)
        }
    }

    private fun createReminder() {
        val edit = EditText(this).apply {
            hint = "Contoh: Hubungi panitia KKR"
            setTextColor(0xFF000000.toInt())
        }

        AlertDialog.Builder(this)
            .setTitle("Pengingat baru")
            .setMessage("Masukkan isi pengingat Anda.")
            .setView(edit)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ ->

                val message = edit.text.toString().trim()

                if (message.isEmpty()) {
                    speak("Isi pengingat tidak boleh kosong.")
                    return@setPositiveButton
                }

                chooseReminderDate(message)
            }
            .show()
    }

    private fun chooseReminderDate(message: String) {
        val now = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->

                chooseReminderTime(
                    message,
                    year,
                    month,
                    day
                )

            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun chooseReminderTime(
        message: String,
        year: Int,
        month: Int,
        day: Int
    ) {
        val now = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->

                val selectedTime = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (selectedTime.timeInMillis <= System.currentTimeMillis()) {
                    speak("Waktu pengingat harus berada di masa depan.")
                    status.text = "Waktu pengingat harus di masa depan."
                    return@TimePickerDialog
                }

                saveAndScheduleReminder(
                    message,
                    selectedTime.timeInMillis
                )

            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveAndScheduleReminder(
        message: String,
        timeMillis: Long
    ) {
        val reminder = Reminder(
            id = System.currentTimeMillis(),
            message = message,
            timeMillis = timeMillis
        )

        ReminderStore.save(this, reminder)

        reminders.removeAll { it.id == reminder.id }
        reminders.add(reminder)
        reminders.sortBy { it.timeMillis }

        scheduleAlarm(reminder)

        val dateText = java.text.SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale("id", "ID")
        ).format(java.util.Date(timeMillis))

        status.text = "Pengingat tersimpan: $message\n$dateText"

        speak("Pengingat berhasil disimpan untuk tanggal $dateText")
    }

    private fun scheduleAlarm(reminder: Reminder) {
        val alarmManager = getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val alarmIntent = Intent(
            this,
            ReminderReceiver::class.java
        ).apply {
            putExtra("message", reminder.message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id.toInt(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )

                status.text =
                    "Pengingat tersimpan. Alarm menggunakan jadwal alternatif. Aktifkan izin alarm tepat untuk ketepatan waktu."

            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )
            }

        } catch (_: SecurityException) {

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.timeMillis,
                pendingIntent
            )
        }
    }

    private fun readAgenda() {
        reminders.clear()
        reminders.addAll(ReminderStore.getAll(this))
        reminders.sortBy { it.timeMillis }

        val message = buildAgendaMessage()

        status.text = message
        speak(message)
    }

    private fun buildAgendaMessage(): String {
        val activeReminders = ReminderStore.getAll(this)
            .filter { it.timeMillis > System.currentTimeMillis() }
            .sortedBy { it.timeMillis }

        if (activeReminders.isEmpty()) {
            return "Belum ada pengingat mendatang."
        }

        return "Agenda Anda: " + activeReminders.joinToString(". ") {
            val date = java.text.SimpleDateFormat(
                "dd MMMM yyyy, HH:mm",
                Locale("id", "ID")
            ).format(java.util.Date(it.timeMillis))

            "${it.message}, pada $date"
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                202
            )
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    speak("Silakan aktifkan izin alarm melalui pengaturan aplikasi.")
                }
            } else {
                speak("Izin alarm tepat sudah aktif.")
            }
        } else {
            speak("Perangkat ini mendukung alarm tepat.")
        }
    }

    private fun speak(message: String) {
        if (::tts.isInitialized) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "sense_reply"
            )
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("id", "ID"))

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.language = Locale.US
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.shutdown()
        }

        super.onDestroy()
    }
}
