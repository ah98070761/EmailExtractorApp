package com.example.emailextractorapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

class SingleUrlActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var multiUrlInput: EditText
    private lateinit var uploadFileButton: Button
    private lateinit var delayInput: EditText
    private lateinit var setDelayButton: Button
    private lateinit var extractButton: Button
    private lateinit var shareResultsButton: Button
    private lateinit var deleteResultsButton: Button
    private lateinit var resultText: TextView
    private lateinit var switchRangeButton: Button
    private lateinit var switchBrowserButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var delaySeconds: Long = 0
    private var currentResults = mutableSetOf<String>()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readUrlsFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single)

        urlInput = findViewById(R.id.urlInput) ?: return showError("URL input not found")
        multiUrlInput = findViewById(R.id.multiUrlInput) ?: return showError("Multi URL input not found")
        uploadFileButton = findViewById(R.id.uploadFileButton) ?: return showError("Upload file button not found")
        delayInput = findViewById(R.id.delayInput) ?: return showError("Delay input not found")
        setDelayButton = findViewById(R.id.setDelayButton) ?: return showError("Set delay button not found")
        extractButton = findViewById(R.id.extractButton) ?: return showError("Extract button not found")
        shareResultsButton = findViewById(R.id.extractResultsButton) ?: return showError("Share results button not found")
        deleteResultsButton = findViewById(R.id.deleteResultsButton) ?: return showError("Delete results button not found")
        resultText = findViewById(R.id.resultText) ?: return showError("Result text not found")
        switchRangeButton = findViewById(R.id.switchRangeButton) ?: return showError("Switch range button not found")
        switchBrowserButton = findViewById(R.id.switchBrowserButton) ?: return showError("Switch browser button not found")
        progressBar = findViewById(R.id.progressBar) ?: return showError("Progress bar not found")

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Android; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0"
            webViewClient = WebViewClient()
        }

        resultText.text = loadResults() ?: ""
        currentResults.addAll(resultText.text.split("\n").filter { it.isNotEmpty() })

        setDelayButton.setOnClickListener {
            val delay = delayInput.text.toString().trim()
            delaySeconds = if (delay.isNotEmpty() && isNumeric(delay)) delay.toLong() * 1000 else 0
            Toast.makeText(this, "Delay set to $delaySeconds ms", Toast.LENGTH_SHORT).show()
        }

        uploadFileButton.setOnClickListener {
            filePickerLauncher.launch("text/plain")
        }

        extractButton.setOnClickListener {
            val singleUrl = urlInput.text.toString().trim()
            val multiUrls = multiUrlInput.text.toString().trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            val urlsToProcess = mutableListOf<String>()
            if (singleUrl.isNotEmpty()) urlsToProcess.add(singleUrl)
            urlsToProcess.addAll(multiUrls)

            if (urlsToProcess.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                extractButton.isEnabled = false
                currentResults.clear()
                resultText.text = ""
                progressBar.max = urlsToProcess.size * 100
                processUrls(urlsToProcess, 0) {
                    progressBar.visibility = View.GONE
                    extractButton.isEnabled = true
                }
            } else {
                resultText.append("\nError: Please enter at least one URL")
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

        switchRangeButton.setOnClickListener {
            startActivity(Intent(this, RangeUrlActivity::class.java))
        }

        switchBrowserButton.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
    }

    private fun readUrlsFromFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val urls = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            inputStream?.close()

            if (urls.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                extractButton.isEnabled = false
                currentResults.clear()
                resultText.text = ""
                progressBar.max = urls.size * 100
                processUrls(urls, 0) {
                    progressBar.visibility = View.GONE
                    extractButton.isEnabled = true
                }
            } else {
                resultText.append("\nError: No URLs found in the file")
            }
        } catch (e: Exception) {
            resultText.append("\nError reading file: ${e.message}")
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
        extractEmails(url) {
            progressBar.progress = ((index + 1) * 100)
            processUrls(urls, index + 1, onComplete)
        }
    }

    private fun showError(message: String) {
        setContentView(TextView(this).apply { text = message })
    }

    private fun extractEmails(url: String, onComplete: () -> Unit) {
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
                        }, delaySeconds)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        handler.post {
                            resultText.append("\nError fetching $url: $description")
                            onComplete()
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