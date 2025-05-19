package com.example.askdoc
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.askdoc.ui.theme.AskDocTheme
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.RAMDirectory

class MainActivity : ComponentActivity() {

    private lateinit var indexDirectory: RAMDirectory
    private lateinit var analyzer: StandardAnalyzer

    private lateinit var etQuery: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvResults: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etQuery = findViewById(R.id.etQuery)
        btnSearch = findViewById(R.id.btnSearch)
        tvResults = findViewById(R.id.tvResults)

        setupIndex()

        btnSearch.setOnClickListener {
            val queryText = etQuery.text.toString()
            if (queryText.isNotBlank()) {
                val results = searchIndex(queryText)
                tvResults.text = results.ifEmpty { "No results found." }
            } else {
                tvResults.text = "Please enter a query."
            }
        }
    }

    private fun setupIndex() {
        analyzer = StandardAnalyzer()
        indexDirectory = RAMDirectory()

        val config = IndexWriterConfig(analyzer)
        val writer = IndexWriter(indexDirectory, config)

        // Add some dummy documents to index
        addDocument(writer, "1", "The quick brown fox jumps over the lazy dog")
        addDocument(writer, "2", "Lucene is a powerful search library written in Java")
        addDocument(writer, "3", "Android development with Kotlin is fun and productive")
        addDocument(writer, "4", "Open source libraries make development easier")

        writer.close()
    }

    private fun addDocument(writer: IndexWriter, id: String, content: String) {
        val doc = Document()
        doc.add(TextField("id", id, Field.Store.YES))
        doc.add(TextField("content", content, Field.Store.YES))
        writer.addDocument(doc)
    }

    private fun searchIndex(queryString: String): String {
        val reader = DirectoryReader.open(indexDirectory)
        val searcher = IndexSearcher(reader)

        val parser = QueryParser("content", analyzer)
        val query = parser.parse(queryString)

        val topDocs: TopDocs = searcher.search(query, 10)
        val results = StringBuilder()

        for (scoreDoc in topDocs.scoreDocs) {
            val docId = scoreDoc.doc
            val doc = searcher.doc(docId)
            results.append("ID: ${doc.get("id")}\n")
            results.append("Content: ${doc.get("content")}\n\n")
        }

        reader.close()
        return results.toString()
    }
}