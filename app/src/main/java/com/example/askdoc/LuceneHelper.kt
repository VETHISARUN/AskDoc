package com.example.askdoc

import android.util.Log
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

class LuceneHelper {
    private val analyzer = StandardAnalyzer()
    private val index = RAMDirectory()
    private val config = IndexWriterConfig(analyzer)
    private val writer = IndexWriter(index, config)

    fun indexText(docId: String, content: String) {
        val doc = Document().apply {
            add(TextField("id", docId, Field.Store.YES))
            add(TextField("content", content, Field.Store.YES))
        }
        writer.addDocument(doc)
        writer.commit()
        Log.d("LuceneHelper", "Indexed docId: $docId")
    }

    fun search(queryStr: String): List<String> {
        val reader = DirectoryReader.open(index)
        val searcher = IndexSearcher(reader)
        val parser = QueryParser("content", analyzer)
        val query = parser.parse(queryStr)
        val hits: TopDocs = searcher.search(query, 10)

        val results = mutableListOf<String>()
        for (scoreDoc in hits.scoreDocs) {
            val doc = searcher.doc(scoreDoc.doc)
            results.add(doc.get("content"))
        }
        reader.close()
        return results
    }

    fun close() {
        writer.close()
        index.close()
        Log.d("LuceneHelper", "Closed Lucene resources")
    }
}
