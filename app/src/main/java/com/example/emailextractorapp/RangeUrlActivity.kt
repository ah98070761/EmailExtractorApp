package com.example.emailextractorapp

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

class RangeUrlActivity : AppCompatActivity() {

    private lateinit var baseUrlInput: EditText
    private lateinit var beforeIdInput: EditText
    private lateinit var afterIdInput: EditText
    private lateinit var startIdInput: EditText
    private lateinit var endIdInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var setDelayButton: Button
    private lateinit var extractButton: Button
    private lateinit var shareResultsButton: Button
    private lateinit var deleteResultsButton: Button
    private lateinit var resultText: TextView
    private lateinit var switchButton: Button
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var delaySeconds: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_range)

        baseUrlInput = findViewById(R.id.baseUrlInput) ?: return showError("Base URL input not found")
        beforeIdInput = findViewById(R.id.beforeIdInput) ?: return showError("Before ID input not found")
        afterIdInput = findViewById(R.id.afterIdInput) ?: return showError("After ID input not found")
        startIdInput = findViewById(R.id.startIdInput) ?: return showError("Start ID input not found")
        endIdInput = findViewById(R.id.endIdInput) ?: return showError("End ID input not found")
        delayInput = findViewById(R.id.delayInput) ?: return showError("Delay input not found")
        setDelayButton = findViewById(R.id.setDelayButton) ?: return showError("Set delay button not found")
        extractButton = findViewById(R.id.extractButton) ?: return showError("Extract button not found")
        shareResultsButton = findViewById(R.id.extractResultsButton) ?: return showError("Share results button not found")
        deleteResultsButton = findViewById(R.id.deleteResultsButton) ?: return showError("Delete results button not found")
        resultText = findViewById(R.id.resultText) ?: return showError("Result text not found")
        switchButton = findViewById(R.id.switchButton) ?: return showError("Switch button not found")

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Android; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0"
            webViewClient = WebViewClient()
        }

        resultText.text = loadResults() ?: ""

        setDelayButton.setOnClickListener {
            val delay = delayInput.text.toString().trim()
            delaySeconds = if (delay.isNotEmpty() && isNumeric(delay)) delay.toLong() * 1000 else 0
            Toast.makeText(this, "Delay set to $delaySeconds ms", Toast.LENGTH_SHORT).show()
        }

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

        shareResultsButton.setOnClickListener {
            val results = resultText.text.toString()
            if (results.isNotEmpty()) {
                shareResults(results)
            } else {
                Toast.makeText(this, "No results to share", Toast.LENGTH_SHORT).show()
            }
        }

        deleteResultsButton.setOnClickListener {
            saveResults("")
            resultText.text = ""
            Toast.makeText(this, "Results deleted", Toast.LENGTH_SHORT).show()
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
            handler.post {
                webView.loadUrl(url)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        handler.postDelayed({
                            webView.evaluateJavascript("(function() { return document.body.innerText; })();") { result ->
                                if (result != null && result != "null") {
                                    val pageText = result.replace("\"", "")
                                    val emails = findEmails(pageText)

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
                                } else {
                                    handler.post {
                                        resultText.append("\nError: Unable to load page content at $url")
                                    }
                                }
                            }
                        }, delaySeconds)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        handler.post {
                            resultText.append("\nError fetching $url: $description")
                        }
                    }
                }
            }
        }.start()
    }

    private fun findEmails(text: String): List<String> {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return emailRegex.findAll(text).map { it.value }.toList().distinct()
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

    private fun shareResults(results: String) {
        try {
            val file = File(cacheDir, "email_results_${System.currentTimeMillis()}.txt")
            FileWriter(file).use { writer ->
                writer.write(results)
            }

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share Results"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}