package com.senseassistant.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
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
    private val reminders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

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

        addButton(root, "KIRIM PERINTAH") { handleCommand(input.text.toString()) }
        addButton(root, "BICARA DENGAN SENSE") { startVoiceInput() }
        addButton(root, "BUAT REMINDER") { createReminder() }
        addButton(root, "BACA AGENDA") {
            val message = if (reminders.isEmpty()) "Belum ada pengingat tersimpan." else
                "Pengingat Anda: " + reminders.joinToString(". ")
            speak(message)
            status.text = message
        }

        setContentView(root)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
        })
    }

    private fun handleCommand(command: String) {
        val text = command.trim()
        if (text.isEmpty()) {
            speak("Silakan ketik perintah Anda.")
            return
        }
        val reply = when {
            text.contains("halo", true) || text.contains("selamat pagi", true) ->
                "Halo. Saya SENSE, asisten pribadi Anda. Ada yang bisa saya bantu?"
            text.contains("agenda", true) || text.contains("reminder", true) ->
                if (reminders.isEmpty()) "Belum ada pengingat tersimpan." else reminders.joinToString(". ")
            else -> "Perintah diterima: $text. Fitur percakapan AI online akan ditambahkan pada tahap berikutnya."
        }
        status.text = reply
        speak(reply)
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Silakan berbicara dengan SENSE")
        }
        try {
            startActivityForResult(intent, 101)
        } catch (_: Exception) {
            speak("Pengenalan suara tidak tersedia pada perangkat ini.")
        }
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val command = words?.firstOrNull().orEmpty()
            input.setText(command)
            handleCommand(command)
        }
    }

    private fun createReminder() {
        val edit = EditText(this).apply { hint = "Contoh: Hubungi panitia KKR" }
        AlertDialog.Builder(this)
            .setTitle("Pengingat baru")
            .setMessage("Masukkan isi pengingat. Versi awal menyimpan daftar pengingat; alarm terjadwal akan ditambahkan berikutnya.")
            .setView(edit)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val value = edit.text.toString().trim()
                if (value.isNotEmpty()) {
                    reminders.add(value)
                    status.text = "Pengingat disimpan: $value"
                    speak("Pengingat disimpan.")
                }
            }.show()
    }

    private fun speak(message: String) {
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sense_reply")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.US
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
