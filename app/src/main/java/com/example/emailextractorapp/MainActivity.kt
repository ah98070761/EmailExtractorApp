package com.example.emailextractorapp

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var extractButton: Button
    private lateinit var exportButton: Button
    private lateinit var emailOutput: TextView
    private val emailList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        urlInput = findViewById(R.id.urlInput)
        extractButton = findViewById(R.id.extractButton)
        exportButton = findViewById(R.id.exportButton)
        emailOutput = findViewById(R.id.emailOutput)
        webView = findViewById(R.id.webView)

        // Configure WebView
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { extractEmails(it) }
            }
        }

        // Extract button click
        extractButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                webView.loadUrl(validUrl)
            } else {
                emailOutput.text = "Please enter a valid URL."
            }
        }

        // Export button click
        exportButton.setOnClickListener {
            if (emailList.isNotEmpty()) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Extracted Emails")
                    putExtra(Intent.EXTRA_TEXT, emailList.joinToString("\n"))
                }
                startActivity(Intent.createChooser(emailIntent, "Send Emails via"))
            } else {
                emailOutput.text = "No emails to export."
            }
        }
    }

    private fun extractEmails(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).get()
                val text = doc.text()
                val emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                val matcher = emailPattern.matcher(text)
                emailList.clear()

                while (matcher.find()) {
                    emailList.add(matcher.group())
                }

                withContext(Dispatchers.Main) {
                    emailOutput.text = if (emailList.isEmpty()) {
                        "No emails found."
                    } else {
                        "Extracted Emails:\n${emailList.joinToString("\n")}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    emailOutput.text = "Error: ${e.message}"
                }
            }
        }
    }
}