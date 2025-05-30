package com.example.emailextractorapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
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

        urlInput = findViewById(R.id.urlInput)
        startUrlInput = findViewById(R.id.startUrlInput)
        endUrlInput = findViewById(R.id.endUrlInput)
        extractButton = findViewById(R.id.extractButton)
        removeAllButton = findViewById(R.id.removeAllButton)
        resultText = findViewById(R.id.resultText)

        // Load existing results
        resultText.text = loadResults()

        extractButton.setOnClickListener {
            val singleUrl = urlInput.text.toString()
            val startUrl = startUrlInput.text.toString()
            val endUrl = endUrlInput.text.toString()

            if (singleUrl.isNotEmpty()) {
                extractEmails(singleUrl)
            } else if (startUrl.isNotEmpty() && endUrl.isNotEmpty() && isNumeric(startUrl) && isNumeric(endUrl)) {
                val start = startUrl.toInt()
                val end = endUrl.toInt()
                if (start <= end) {
                    for (i in start..end) {
                        val rangeUrl = urlInput.text.toString().replace(Regex("/\\d+/?$"), "/$i/")
                        extractEmails(rangeUrl)
                    }
                }
            }
        }

        removeAllButton.setOnClickListener {
            resultText.text = ""
            resultsFile.delete()
        }
    }

    private fun extractEmails(url: String) {
        Thread {
            try {
                // Simulate delay for email protection (e.g., JavaScript loading)
                Thread.sleep(5000) // Wait 5 seconds; adjust based on site
                val doc = Jsoup.connect(url).get()
                val emails = doc.body().text().findAllEmails()

                handler.post {
                    val currentResults = loadResults()
                    val newResults = if (currentResults.isNotEmpty()) "$currentResults\n${emails.joinToString("\n")}" else emails.joinToString("\n")
                    resultText.text = newResults
                    saveResults(newResults)
                }
            } catch (e: IOException) {
                handler.post { resultText.append("\nError fetching $url: ${e.message}") }
            }
        }.start()
    }

    private fun String.findAllEmails(): List<String> {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return emailRegex.findAll(this).map { it.value }.toList()
    }

    private fun isNumeric(str: String): Boolean {
        return str.isNotEmpty() && str.all { it.isDigit() }
    }

    private fun loadResults(): String {
        return if (resultsFile.exists()) resultsFile.readText() else ""
    }

    private fun saveResults(results: String) {
        try {
            FileOutputStream(resultsFile, true).use { output ->
                output.write(results.toByteArray())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

// Extension function to find emails
fun String.findAllEmails(): List<String> = this.findAllEmails()