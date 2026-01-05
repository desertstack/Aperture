package com.desertstack.aperture

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.aperture.Aperture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    private lateinit var okHttpClient: OkHttpClient
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create OkHttp client with Aperture interceptor
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Aperture.getInterceptor())
            .build()

        setupViews()
    }

    private fun setupViews() {
        val serverUrlText = findViewById<TextView>(R.id.server_url)
        val btnGetRequest = findViewById<Button>(R.id.btn_get_request)
        val btnPostRequest = findViewById<Button>(R.id.btn_post_request)
        val btnError = findViewById<Button>(R.id.btn_error)

        // Display server URLs
        val networkUrl = Aperture.getServerUrl()
        val adbUrl = Aperture.getLocalhostUrl()
        val adbCommand = Aperture.getAdbForwardCommand()

        serverUrlText.text = """
            🌐 Network: $networkUrl
            🔌 ADB: $adbUrl

            💻 $adbCommand
        """.trimIndent()

        btnGetRequest.setOnClickListener {
            makeGetRequest()
        }

        btnPostRequest.setOnClickListener {
            makePostRequest()
        }

        btnError.setOnClickListener {
            makeErrorRequest()
        }
    }

    private fun makeGetRequest() {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/todos/1")
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        response.body?.string() ?: "Empty response"
                    }
                }

                Toast.makeText(this@MainActivity, "GET request successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun makePostRequest() {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val json = """{"title":"Test","body":"Sample post","userId":1}"""
                    val mediaType = "application/json".toMediaTypeOrNull()
                    val requestBody = json.toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts")
                        .post(requestBody)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        response.body?.string() ?: "Empty response"
                    }
                }

                Toast.makeText(this@MainActivity, "POST request successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun makeErrorRequest() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/invalid-endpoint-404")
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        response.body?.string()
                    }
                }

                Toast.makeText(this@MainActivity, "Request completed (check status)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
