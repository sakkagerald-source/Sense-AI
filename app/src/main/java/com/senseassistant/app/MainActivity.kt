```kotlin
package com.senseassistant.app

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
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
    private lateinit var reminderList: LinearLayout

    private val reminders = mutableListOf<Reminder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                200
            )
        }

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
            setPadding(0, 30, 0, 25)
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

        addButton(root, "IZIN ALARM TEPAT WAKTU") {
            requestExactAlarmPermission()
        }

        val heading = TextView(this).apply {
            text = "\nDAFTAR PENGINGAT"
            textSize = 18f
            setTextColor(0xFF62D9FF.toInt())
        }

        root.addView(heading)

        reminderList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(reminderList)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        loadReminders()
    }

    override fun onResume() {
        super.onResume()

        if (::reminderList.isInitialized) {
            loadReminders()
        }
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
                topMargin = 10
            }
        )
    }

    // ==========================================
    // PEMROSESAN PERINTAH SENSE
    // ==========================================

    private fun handleCommand(command: String) {

        val text = command.trim()

        if (text.isEmpty()) {
            speak("Silakan ketik perintah Anda.")
            return
        }

        // Periksa perintah membuat pengingat otomatis
        if (createQuickReminder(text)) {
            return
        }

        val reply = when {
            text.contains("halo", true) ||
            text.contains("selamat pagi", true) ->
                "Halo. Saya SENSE, asisten pribadi Anda."

            text.contains("agenda", true) ||
            text.contains("reminder", true) ->
                getAgendaText()

            else ->
                "Perintah diterima: $text. Fitur AI online akan dikembangkan berikutnya."
        }

        status.text = reply
        speak(reply)
    }

    // ==========================================
    // PENGINGAT OTOMATIS MELALUI SUARA ATAU TEKS
    // ==========================================

    private fun createQuickReminder(command: String): Boolean {

        val pattern = Regex(
            """ingatkan\s+saya\s+(\d+)\s+(detik|menit|jam|hari)\s+lagi\s+(?:untuk\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )

        val match = pattern.find(command) ?: return false

        val amount = match.groupValues[1].toLongOrNull()
            ?: return false

        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val message = match.groupValues[3].trim()

        if (amount <= 0 || message.isEmpty()) {
            status.text = "Perintah pengingat tidak valid."
            speak("Mohon periksa kembali perintah pengingat.")
            return true
        }

        val multiplier = when (unit) {
            "detik" -> 1_000L
            "menit" -> 60_000L
            "jam" -> 3_600_000L
            "hari" -> 86_400_000L
            else -> return false
        }

        if (amount > Long.MAX_VALUE / multiplier) {
            status.text = "Durasi pengingat terlalu panjang."
            speak("Durasi pengingat terlalu panjang.")
            return true
        }

        val reminder = Reminder(
            id = System.currentTimeMillis(),
            message = message,
            timeMillis = System.currentTimeMillis() +
                (amount * multiplier)
        )

        saveAndSchedule(reminder)

        return true
    }

    // ==========================================
    // MEMBUAT PENGINGAT MANUAL
    // ==========================================

    private fun createReminder() {

        val edit = EditText(this).apply {
            hint = "Contoh: Hubungi panitia KKR"
        }

        AlertDialog.Builder(this)
            .setTitle("Pengingat Baru")
            .setMessage("Apa yang ingin Anda ingat?")
            .setView(edit)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ ->

                val message = edit.text.toString().trim()

                if (message.isEmpty()) {
                    speak("Isi pengingat tidak boleh kosong.")
                    return@setPositiveButton
                }

                selectReminderDate(message)
            }
            .show()
    }

    private fun selectReminderDate(message: String) {

        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->

                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)

                selectReminderTime(message, calendar)

            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun selectReminderTime(
        message: String,
        calendar: Calendar
    ) {

        TimePickerDialog(
            this,
            { _, hour, minute ->

                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val timeMillis = calendar.timeInMillis

                if (timeMillis <= System.currentTimeMillis()) {
                    speak("Waktu pengingat harus berada di masa depan.")
                    return@TimePickerDialog
                }

                val reminder = Reminder(
                    id = System.currentTimeMillis(),
                    message = message,
                    timeMillis = timeMillis
                )

                saveAndSchedule(reminder)

            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    // ==========================================
    // MENYIMPAN DAN MENJADWALKAN ALARM
    // ==========================================

    private fun saveAndSchedule(reminder: Reminder) {

        // Simpan pengingat secara permanen
        ReminderStore.save(this, reminder)

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

                // Gunakan alarm biasa jika izin alarm tepat waktu belum aktif
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )

            } else {

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )
            }

            status.text = "Pengingat berhasil disimpan: ${reminder.message}"

            speak("Pengingat berhasil disimpan.")

            loadReminders()

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {

                AlertDialog.Builder(this)
                    .setTitle("Aktifkan alarm tepat waktu")
                    .setMessage(
                        "Pengingat sudah disimpan. " +
                        "Aktifkan izin alarm agar notifikasi lebih tepat waktu."
                    )
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        requestExactAlarmPermission()
                    }
                    .setNegativeButton("Nanti", null)
                    .show()
            }

        } catch (_: SecurityException) {

            // Coba jadwalkan alarm biasa jika alarm tepat waktu ditolak
            try {

                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )

                status.text = "Pengingat disimpan dengan alarm biasa."
                speak("Pengingat disimpan.")

                loadReminders()

            } catch (_: Exception) {

                status.text =
                    "Pengingat tersimpan, tetapi alarm gagal dijadwalkan."

                speak("Pengingat tersimpan, tetapi alarm gagal dijadwalkan.")
            }
        }
    }

    // ==========================================
    // IZIN ALARM TEPAT WAKTU
    // ==========================================

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

                    status.text =
                        "Buka pengaturan izin alarm secara manual."
                }

            } else {

                status.text = "Izin alarm tepat waktu sudah aktif."
                speak("Izin alarm sudah aktif.")
            }

        } else {

            status.text = "Izin alarm tepat waktu tidak diperlukan."
        }
    }

    // ==========================================
    // MENAMPILKAN DAFTAR PENGINGAT
    // ==========================================

    private fun loadReminders() {

        reminders.clear()
        reminders.addAll(ReminderStore.getAll(this))

        reminderList.removeAllViews()

        if (reminders.isEmpty()) {

            val empty = TextView(this).apply {
                text = "Belum ada pengingat."
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 12, 0, 12)
            }

            reminderList.addView(empty)
            return
        }

        reminders.sortedBy { it.timeMillis }.forEach { reminder ->

            val date = java.text.SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                Locale("id", "ID")
            ).format(reminder.timeMillis)

            val item = TextView(this).apply {
                text = "• ${reminder.message}\n  $date"
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(8, 14, 8, 14)
            }

            reminderList.addView(item)

            val deleteButton = Button(this).apply {
                text = "HAPUS PENGINGAT"

                setOnClickListener {
                    deleteReminder(reminder)
                }
            }

            reminderList.addView(deleteButton)
        }
    }

    // ==========================================
    // MENGHAPUS PENGINGAT
    // ==========================================

    private fun deleteReminder(reminder: Reminder) {

        val alarmManager = getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val intent = Intent(
            this,
            ReminderReceiver::class.java
        )

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        ReminderStore.remove(this, reminder.id)

        loadReminders()

        status.text = "Pengingat berhasil dihapus."
        speak("Pengingat berhasil dihapus.")
    }

    // ==========================================
    // MEMBACA AGENDA
    // ==========================================

    private fun getAgendaText(): String {

        val all = ReminderStore.getAll(this)

        return if (all.isEmpty()) {

            "Belum ada pengingat tersimpan."

        } else {

            all.sortedBy { it.timeMillis }.joinToString(". ") {
                it.message
            }
        }
    }

    private fun readAgenda() {

        val message = getAgendaText()

        status.text = message
        speak(message)
    }

    // ==========================================
    // PENGENALAN SUARA BAHASA INDONESIA
    // ==========================================

    private fun startVoiceInput() {

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "id-ID"
            )

            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Silakan berbicara dengan SENSE"
            )
        }

        try {

            startActivityForResult(intent, 101)

        } catch (_: Exception) {

            speak("Pengenalan suara tidak tersedia.")
        }
    }

    @Deprecated("Deprecated by Android")
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

    // ==========================================
    // TEXT TO SPEECH
    // ==========================================

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

            val result = tts.setLanguage(
                Locale("id", "ID")
            )

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
```
