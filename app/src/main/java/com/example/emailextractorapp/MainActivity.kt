package com.example.emailextractorapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var startUrlInput: EditText
    private lateinit var endUrlInput: EditText
    private lateinit var extractButton: Button
    private lateinit var removeAllButton: Button
    private lateinit var resultText: TextView
    private val resultsFile = File(filesDir, "email_results.txt")
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput) ?: run {
            resultText = TextView(this).apply { text = "Error: URL input not found" }
            setContentView(resultText)
            return
        }
        startUrlInput = findViewById(R.id.startUrlInput) ?: run { resultText = TextView(this).apply { text = "Error: Start input not found" }; setContentView(resultText); return }
        endUrlInput = findViewById(R.id.endUrlInput) ?: run { resultText = TextView(this).apply { text = "Error: End input not found" }; setContentView(resultText); return }
        extractButton = findViewById(R.id.extractButton) ?: run { resultText = TextView(this).apply { text = "Error: Extract button not found" }; setContentView(resultText); return }
        removeAllButton = findViewById(R.id.removeAllButton) ?: run { resultText = TextView(this).apply { text = "Error: Remove button not found" }; setContentView(resultText); return }
        resultText = findViewById(R.id.resultText) ?: run { resultText = TextView(this).apply { text = "Error: Result text not found" }; setContentView(resultText); return }

        resultText.text = loadResults()

        extractButton.setOnClickListener {
            val singleUrl = urlInput.text.toString().trim()
            val startUrl = startUrlInput.text.toString().trim()
            val endUrl = endUrlInput.text.toString().trim()

            if (singleUrl.isNotEmpty()) {
                extractEmails(singleUrl)
            } else if (startUrl.isNotEmpty() && endUrl.isNotEmpty() && isNumeric(startUrl) && isNumeric(endUrl)) {
                val start = startUrl.toIntOrNull() ?: 0
                val end = endUrl.toIntOrNull() ?: 0
                if (start <= end) {
                    for (i in start..end) {
                        val baseUrl = urlInput.text.toString().trim()
                        val rangeUrl = if (baseUrl.isNotEmpty()) baseUrl.replace(Regex("/\\d+/?$"), "/$i/") else "https://example.com/$i/"
                        extractEmails(rangeUrl)
                    }
                } else {
                    resultText.append("\nError: Start number must be less than or equal to end number")
                }
            } else {
                resultText.append("\nError: Invalid range input")
            }
        }

        removeAllButton.setOnClickListener {
            resultText.text = ""
            if (resultsFile.exists()) {
                if (!resultsFile.delete()) {
                    resultText.append("\nError: Failed to delete results file")
                }
            }
        }
    }

    private fun extractEmails(url: String) {
        Thread {
            try {
                Thread.sleep(5000) // Wait 5 seconds for email protection
                val doc = Jsoup.connect(url).get()
                val emails = findEmails(doc.body().text())

                handler.post {
                    if (emails.isNotEmpty()) {
                        val currentResults = loadResults()
                        val newResults = if (currentResults.isNotEmpty()) "$currentResults\n${emails.joinToString("\n")}" else emails.joinToString("\n")
                        resultText.text = newResults
                        saveResults(newResults)
                    } else {
                        resultText.append("\nNo emails found at $url")
                    }
                }
            } catch (e: IOException) {
                handler.post { resultText.append("\nError fetching $url: ${e.message}") }
            } catch (e: InterruptedException) {
                handler.post { resultText.append("\nError: Extraction interrupted for $url") }
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

    private fun loadResults(): String {
        return try {
            if (resultsFile.exists()) resultsFile.readText() else ""
        } catch (e: IOException) {
            "Error loading results: ${e.message}"
        }
    }

    private fun saveResults(results: String) {
        try {
            FileOutputStream(resultsFile, false).use { output -> // Changed to false to overwrite file
                output.write(results.toByteArray())
            }
        } catch (e: IOException) {
            handler.post { resultText.append("\nError saving results: ${e.message}") }
        }
    }
}