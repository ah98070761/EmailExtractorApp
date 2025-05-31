package com.example.emailextractorapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup

class RangeUrlActivity : AppCompatActivity() {

    private lateinit var baseUrlInput: EditText
    private lateinit var beforeIdInput: EditText
    private lateinit var afterIdInput: EditText
    private lateinit var startIdInput: EditText
    private lateinit var endIdInput: EditText
    private lateinit var extractButton: Button
    private lateinit var resultText: TextView
    private lateinit var switchButton: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_range)

        baseUrlInput = findViewById(R.id.baseUrlInput) ?: return showError("Base URL input not found")
        beforeIdInput = findViewById(R.id.beforeIdInput) ?: return showError("Before ID input not found")
        afterIdInput = findViewById(R.id.afterIdInput) ?: return showError("After ID input not found")
        startIdInput = findViewById(R.id.startIdInput) ?: return showError("Start ID input not found")
        endIdInput = findViewById(R.id.endIdInput) ?: return showError("End ID input not found")
        extractButton = findViewById(R.id.extractButton) ?: return showError("Extract button not found")
        resultText = findViewById(R.id.resultText) ?: return showError("Result text not found")
        switchButton = findViewById(R.id.switchButton) ?: return showError("Switch button not found")

        resultText.text = loadResults() ?: ""

        extractButton.setOnClickListener {
            val baseUrl = baseUrlInput.text.toString().trim()
            val beforeId = beforeIdInput.text.toString().trim()
            val afterId = afterIdInput.text.toString().trim()
            val startId = startIdInput.text.toString().trim()
            val endId = endIdInput.text.toString().trim()

            if (startId.isNotEmpty() && endId.isNotEmpty() && isNumeric(startId) && isNumeric(endId)) {
                val start = startId.toIntOrNull() ?: 0
                val end = endId.toIntOrNull() ?: 0
                if (start <= end) {
                    for (i in start..end) {
                        val fullUrl = if (baseUrl.isNotEmpty()) "$baseUrl$beforeId$i$afterId" else "$beforeId$i$afterId"
                        extractEmails(fullUrl)
                    }
                } else {
                    resultText.append("\nError: Start ID must be less than or equal to end ID")
                }
            } else {
                resultText.append("\nError: Invalid ID range")
            }
        }

        switchButton.setOnClickListener {
            startActivity(Intent(this, SingleUrlActivity::class.java))
        }
    }

    private fun showError(message: String) {
        setContentView(TextView(this).apply { text = message })
    }

    private fun extractEmails(url: String) {
        Thread {
            try {
                val doc = Jsoup.connect(url).get()
                val emails = findEmails(doc.body().text())

                handler.post {
                    if (emails.isNotEmpty()) {
                        val currentResults = loadResults() ?: ""
                        val newResults = if (currentResults.isNotEmpty()) "$currentResults\n${emails.joinToString("\n")}" else emails.joinToString("\n")
                        resultText.text = newResults
                        saveResults(newResults)
                    } else {
                        resultText.append("\nNo emails found at $url")
                    }
                }
            } catch (e: Exception) {
                handler.post { resultText.append("\nError fetching $url: ${e.message}") }
            }
        }.start()
    }

    private fun findEmails(text: String): List<String> {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return emailRegex.findAll(text).map { it.value }.toList()
    }

    private fun isNumeric(str: String): Boolean {
        return str.isNotEmpty() && str.all { it.isDigit() }
    }

    private fun loadResults(): String? {
        return getSharedPreferences("EmailExtractorAppPrefs", MODE_PRIVATE)
            .getString("results", null)
    }

    private fun saveResults(results: String) {
        getSharedPreferences("EmailExtractorAppPrefs", MODE_PRIVATE)
            .edit()
            .putString("results", results)
            .apply()
    }
}