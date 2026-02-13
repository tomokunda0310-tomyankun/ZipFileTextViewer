// Path: app/src/main/java/com/papa/zipfileviewer/MainActivity.kt
// Version: v1.00.06
package com.papa.zipfileviewer

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.zip.ZipInputStream
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var textViewer: EditText
    private val REQUEST_CODE_PICK_ZIP = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewer = findViewById(R.id.textViewer)
        val btnOpenFile = findViewById<Button>(R.id.btnOpenFile)
        val btnCopy = findViewById<Button>(R.id.btnCopy)

        // ファイル選択
        btnOpenFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_ZIP)
        }

        // コピーボタン（選択範囲優先、なければ全文）
        btnCopy.setOnClickListener {
            val targetText = getSelectedTextOrAll()
            if (targetText.isNotEmpty()) {
                copyToClipboard(targetText)
                val msg = if (textViewer.hasSelection()) "選択範囲をコピー" else "全文をコピー"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // タップで全選択コピー
        textViewer.setOnClickListener {
            val fullText = textViewer.text.toString()
            if (fullText.isNotEmpty()) {
                textViewer.selectAll()
                copyToClipboard(fullText)
                Toast.makeText(this, "全文をコピーしました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedTextOrAll(): String {
        val start = textViewer.selectionStart
        val end = textViewer.selectionEnd
        return if (start != end && start != -1 && end != -1) {
            textViewer.text.substring(start, end)
        } else {
            textViewer.text.toString()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("ZipViewerContent", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_ZIP && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> processZipFile(uri) }
        }
    }

    private fun processZipFile(uri: Uri) {
        val contentBuilder = StringBuilder()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            contentBuilder.append("=== FILE: ${entry.name} ===\n")
                            val text = zis.bufferedReader(Charsets.UTF_8).readText()
                            contentBuilder.append(text).append("\n\n")
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            textViewer.setText(contentBuilder.toString())
        } catch (e: Exception) {
            textViewer.setText("Error: ${e.message}")
        }
    }
}
