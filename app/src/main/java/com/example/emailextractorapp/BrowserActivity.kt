package com.example.emailextractorapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

class BrowserActivity : AppCompatActivity() {

    private lateinit var browserUrlInput: EditText
    private lateinit var goButton: Button
    private lateinit var webView: WebView
    private lateinit var delayInput: EditText
    private lateinit var setDelayButton: Button
    private lateinit var rangeBaseUrlInput: EditText
    private lateinit var rangeBeforeIdInput: EditText
    private lateinit var rangeAfterIdInput: EditText
    private lateinit var rangeStartIdInput: EditText
    private lateinit var rangeEndIdInput: EditText
    private lateinit var extractRangeButton: Button
    private lateinit var shareResultsButton: Button
    private lateinit var deleteResultsButton: Button
    private lateinit var resultText: TextView
    private lateinit var switchSingleButton: Button
    private lateinit var switchRangeButton: Button
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var delaySeconds: Long = 0
    private var currentResults = mutableSetOf<String>()
    private var isExtractingRange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        browserUrlInput = findViewById(R.id.browserUrlInput) ?: return showError("Browser URL input not found")
        goButton = findViewById(R.id.goButton) ?: return showError("Go button not found")
        webView = findViewById(R.id.webView) ?: return showError("WebView not found")
        delayInput = findViewById(R.id.delayInput) ?: return showError("Delay input not found")
        setDelayButton = findViewById(R.id.setDelayButton) ?: return showError("Set delay button not found")
        rangeBaseUrlInput = findViewById(R.id.rangeBaseUrlInput) ?: return showError("Range base URL input not found")
        rangeBeforeIdInput = findViewById(R.id.rangeBeforeIdInput) ?: return showError("Range before ID input not found")
        rangeAfterIdInput = findViewById(R.id.rangeAfterIdInput) ?: return showError("Range after ID input not found")
        rangeStartIdInput = findViewById(R.id.rangeStartIdInput) ?: return showError("Range start ID input not found")
        rangeEndIdInput = findViewById(R.id.rangeEndIdInput) ?: return showError("Range end ID input not found")
        extractRangeButton = findViewById(R.id.extractRangeButton) ?: return showError("Extract range button not found")
        shareResultsButton = findViewById(R.id.extractResultsButton) ?: return showError("Share results button not found")
        deleteResultsButton = findViewById(R.id.deleteResultsButton) ?: return showError("Delete results button not found")
        resultText = findViewById(R.id.resultText) ?: return showError("Result text not found")
        switchSingleButton = findViewById(R.id.switchSingleButton) ?: return showError("Switch single button not found")
        switchRangeButton = findViewById(R.id.switchRangeButton) ?: return showError("Switch range button not found")
        progressBar = findViewById(R.id.progressBar) ?: return showError("Progress bar not found")

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Android; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0"
            setInitialScale(1)
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            isScrollContainer = false // Prevent WebView from interfering with ScrollView
        }

        resultText.text = loadResults() ?: ""
        currentResults.addAll(resultText.text.split("\n").filter { it.isNotEmpty() })

        setDelayButton.setOnClickListener {
            val delay = delayInput.text.toString().trim()
            delaySeconds = if (delay.isNotEmpty() && isNumeric(delay)) delay.toLong() * 1000 else 0
            Toast.makeText(this, "Delay set to $delaySeconds ms", Toast.LENGTH_SHORT).show()
        }

        goButton.setOnClickListener {
            val url = browserUrlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                goButton.isEnabled = false
                webView.webViewClient = createWebViewClient()
                webView.loadUrl(if (url.startsWith("http")) url else "https://$url")
            } else {
                resultText.append("\nError: Please enter a URL")
            }
        }

        extractRangeButton.setOnClickListener {
            val baseUrl = rangeBaseUrlInput.text.toString().trim()
            val beforeId = rangeBeforeIdInput.text.toString().trim()
            val afterId = rangeAfterIdInput.text.toString().trim()
            val startId = rangeStartIdInput.text.toString().trim()
            val endId = rangeEndIdInput.text.toString().trim()

            if (startId.isNotEmpty() && endId.isNotEmpty() && isNumeric(startId) && isNumeric(endId)) {
                val start = startId.toIntOrNull() ?: 0
                val end = endId.toIntOrNull() ?: 0
                if (start <= end) {
                    progressBar.visibility = View.VISIBLE
                    extractRangeButton.isEnabled = false
                    goButton.isEnabled = false
                    isExtractingRange = true
                    val urls = (start..end).map { i ->
                        if (baseUrl.isNotEmpty()) "$baseUrl$beforeId$i$afterId" else "$beforeId$i$afterId"
                    }
                    progressBar.max = urls.size * 100
                    processUrls(urls, 0) {
                        progressBar.visibility = View.GONE
                        extractRangeButton.isEnabled = true
                        goButton.isEnabled = true
                        isExtractingRange = false
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
            currentResults.clear()
            Toast.makeText(this, "Results deleted", Toast.LENGTH_SHORT).show()
        }

        switchSingleButton.setOnClickListener {
            startActivity(Intent(this, SingleUrlActivity::class.java))
        }

        switchRangeButton.setOnClickListener {
            startActivity(Intent(this, RangeUrlActivity::class.java))
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (isExtractingRange) return
                handler.postDelayed({
                    extractEmails(url ?: "") {
                        progressBar.visibility = View.GONE
                        goButton.isEnabled = true
                    }
                }, delaySeconds)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                handler.post {
                    resultText.append("\nError fetching ${request?.url}: ${error?.description}")
                    progressBar.visibility = View.GONE
                    goButton.isEnabled = true
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                handler.post {
                    resultText.append("\nError fetching $failingUrl: $description")
                    progressBar.visibility = View.GONE
                    goButton.isEnabled = true
                }
            }
        }
    }

    private fun processUrls(urls: List<String>, index: Int, onComplete: () -> Unit) {
        if (index >= urls.size) {
            val finalResults = currentResults.joinToString("\n")
            resultText.text = finalResults
            saveResults(finalResults)
            onComplete()
            return
        }

        val url = urls[index]
        webView.webViewClient = createWebViewClient()
        webView.loadUrl(url)
        extractEmails(url) {
            progressBar.progress = ((index + 1) * 100)
            processUrls(urls, index + 1, onComplete)
        }
    }

    private fun extractEmails(url: String, onComplete: () -> Unit) {
        webView.evaluateJavascript("(function() { return document.body.innerText; })();") { result ->
            if (result != null && result != "null") {
                val pageText = result.replace("\"", "")
                val emails = findEmails(pageText)
                handler.post {
                    if (emails.isNotEmpty()) {
                        currentResults.addAll(emails)
                        resultText.text = currentResults.joinToString("\n")
                    } else {
                        resultText.append("\nNo emails found at $url")
                    }
                    onComplete()
                }
            } else {
                handler.post {
                    resultText.append("\nError: Unable to load page content at $url")
                    onComplete()
                }
            }
        }
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

    private fun showError(message: String) {
        setContentView(TextView(this).apply { text = message })
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}