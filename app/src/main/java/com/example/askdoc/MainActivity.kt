package com.example.askdoc

import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity

import androidx.room.Room
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var editPersonalInfo: EditText
    private lateinit var btnSaveInfo: Button
    private lateinit var editQuestion: EditText
    private lateinit var btnAsk: Button
    private lateinit var textResponse: TextView

    private lateinit var db: AppDatabase
    private lateinit var dao: VectorEntryDao
    private val client = OkHttpClient()

    private val GEMINI_API_KEY = "AIzaSyD5ZFyVrZzuw5TJBcdgOZg6clgnGSILDTs"

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editPersonalInfo = findViewById(R.id.edit_personal_info)
        btnSaveInfo = findViewById(R.id.btn_save_info)
        editQuestion = findViewById(R.id.edit_question)
        btnAsk = findViewById(R.id.btn_ask)
        textResponse = findViewById(R.id.text_response)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "askdoc-db"
        ).fallbackToDestructiveMigration()
            .build()

        dao = db.vectorEntryDao()

        // Optional: prebuilt doc embed on app start
        mainScope.launch(Dispatchers.IO) {
            if (dao.getPrebuiltEntry() == null) {
                val docText = assets.open("medical_knowledge.txt").bufferedReader().use { it.readText() }
                if (docText.isNotBlank()) {
                    embedAndStore(docText, true)
                }
            }
        }

        btnSaveInfo.setOnClickListener {
            val info = editPersonalInfo.text.toString().trim()
            if (info.isNotEmpty()) {
                mainScope.launch(Dispatchers.IO) {
                    embedAndStore(info, false)
                    withContext(Dispatchers.Main) {
                        editPersonalInfo.text.clear()
                        Toast.makeText(this@MainActivity, "Personal info saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnAsk.setOnClickListener {
            val question = editQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                textResponse.text = "Thinking..."
                mainScope.launch(Dispatchers.IO) {
                    val answer = answerUserQuery(question)
                    withContext(Dispatchers.Main) {
                        textResponse.text = answer
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private suspend fun embedAndStore(text: String, isPrebuilt: Boolean) {
        val embedding = embedTextWithGemini(text)
        if (embedding != null) {
            dao.insert(
                VectorEntry(
                    text = text,
                    embeddingJson = JSONArray(embedding).toString(),
                    isPrebuilt = isPrebuilt
                )
            )
        }
    }

    private suspend fun answerUserQuery(question: String): String {
        val questionEmbedding = embedTextWithGemini(question) ?: return "Failed to embed question."
        val storedEntries = dao.getAll()
        if (storedEntries.isEmpty()) return "No stored data to search."

        val scored = storedEntries.map {
            val storedEmbedding = jsonToDoubleList(it.embeddingJson)
            val score = cosineSimilarity(questionEmbedding, storedEmbedding)
            it to score
        }.sortedByDescending { it.second }

        val topTexts = scored.take(3).joinToString("\n---\n") { it.first.text }

        val prompt = """
            Context:
            $topTexts

            Question:
            $question
        """.trimIndent()

        val answer = chatWithGemini(prompt)
        return answer ?: "Failed to get answer from Gemini."
    }

    private suspend fun embedTextWithGemini(text: String): List<Double>? = withContext(Dispatchers.IO) {
        val url = "https://gemini.api.openai.com/v1/embeddings"

        val jsonBody = JSONObject().apply {
            put("model", "gemini-embedding-001")
            put("input", text)
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $GEMINI_API_KEY")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string() ?: return@use null)
                val embedding = json.getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding")
                List(embedding.length()) { embedding.getDouble(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun chatWithGemini(prompt: String): String? = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$GEMINI_API_KEY"

        val jsonBody = JSONObject().apply {
            val contents = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            put("contents", JSONArray().put(contents))
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val responseJson = JSONObject(response.body?.string() ?: return@use null)
                val text = responseJson.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonToDoubleList(json: String): List<Double> {
        val array = JSONArray(json)
        return List(array.length()) { array.getDouble(it) }
    }

    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        val dot = vec1.zip(vec2).sumOf { it.first * it.second }
        val mag1 = sqrt(vec1.sumOf { it * it })
        val mag2 = sqrt(vec2.sumOf { it * it })
        return if (mag1 > 0 && mag2 > 0) dot / (mag1 * mag2) else 0.0
    }
}
