package com.example.emailextractorapp

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup
import java.io.File

class SingleUrlActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var setDelayButton: Button
    private lateinit var extractButton: Button
    private lateinit var extractResultsButton: Button
    private lateinit var deleteResultsButton: Button
    private lateinit var resultText: TextView
    private lateinit var switchButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var delaySeconds: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single)

        urlInput = findViewById(R.id.urlInput) ?: return showError("URL input not found")
        delayInput = findViewById(R.id.delayInput) ?: return showError("Delay input not found")
        setDelayButton = findViewById(R.id.setDelayButton) ?: return showError("Set delay button not found")
        extractButton = findViewById(R.id.extractButton) ?: return showError("Extract button not found")
        extractResultsButton = findViewById(R.id.extractResultsButton) ?: return showError("Extract results button not found")
        deleteResultsButton = findViewById(R.id.deleteResultsButton) ?: return showError("Delete results button not found")
        resultText = findViewById(R.id.resultText) ?: return showError("Result text not found")
        switchButton = findViewById(R.id.switchButton) ?: return showError("Switch button not found")

        resultText.text = loadResults() ?: ""

        setDelayButton.setOnClickListener {
            val delay = delayInput.text.toString().trim()
            delaySeconds = if (delay.isNotEmpty() && isNumeric(delay)) delay.toLong() * 1000 else 0
            Toast.makeText(this, "Delay set to $delaySeconds ms", Toast.LENGTH_SHORT).show()
        }

        extractButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                extractEmails(url)
            } else {
                resultText.append("\nError: Please enter a URL")
            }
        }

        extractResultsButton.setOnClickListener {
            val results = resultText.text.toString()
            if (results.isNotEmpty()) {
                saveResultsToFile(results)
            } else {
                Toast.makeText(this, "No results to extract", Toast.LENGTH_SHORT).show()
            }
        }

        deleteResultsButton.setOnClickListener {
            saveResults("")
            resultText.text = ""
            Toast.makeText(this, "Results deleted", Toast.LENGTH_SHORT).show()
        }

        switchButton.setOnClickListener {
            startActivity(Intent(this, RangeUrlActivity::class.java))
        }
    }

    private fun showError(message: String) {
        setContentView(TextView(this).apply { text = message })
    }

    private fun extractEmails(url: String) {
        Thread {
            try {
                if (delaySeconds > 0) Thread.sleep(delaySeconds)
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

    private fun saveResultsToFile(results: String) {
        try {
            val fileName = "email_results_${System.currentTimeMillis()}.txt"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            file.writeText(results)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.fromFile(file)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Saving email results")
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            }
            downloadManager.enqueue(request)
            Toast.makeText(this, "Results saved to Downloads as $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}