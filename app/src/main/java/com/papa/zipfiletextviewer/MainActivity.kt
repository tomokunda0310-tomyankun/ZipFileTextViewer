package com.papa.zipfiletextviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

data class FileEntryInfo(val name: String, val lastModified: Long, val isInsideZip: Boolean = true, val isDirectory: Boolean = false, val uri: Uri? = null)

class MainActivity : AppCompatActivity() {

    private lateinit var textViewer: EditText
    private lateinit var fileInfoText: TextView
    private lateinit var zipPathText: TextView
    
    private val REQUEST_CODE_PICK_FILE = 1001
    private var currentFileUri: Uri? = null
    private var filteredList: List<FileEntryInfo> = mutableListOf()
    private var currentIndex: Int = -1
    private var showFolders = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewer = findViewById(R.id.textViewer)
        fileInfoText = findViewById(R.id.fileInfoText)
        zipPathText = findViewById(R.id.zipPathText)
        
        findViewById<Button>(R.id.btnOpenFile).setOnClickListener { openFilePicker() }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { if (currentIndex > 0) loadEntryByIndex(currentIndex - 1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { if (currentIndex < filteredList.size - 1) loadEntryByIndex(currentIndex + 1) }
        findViewById<Button>(R.id.btnList).setOnClickListener { showFileListDialog() }
        findViewById<Button>(R.id.btnFolderToggle).setOnClickListener { 
            showFolders = !showFolders
            (it as Button).text = if (showFolders) "フォルダ:表示中" else "フォルダ:非表示"
            // ZIP時は再フィルタ、単体ファイル時はそのまま
            if (filteredList.any { item -> item.isInsideZip }) handleSelectedFile(currentFileUri ?: return@setOnClickListener)
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        fileInfoText.setOnClickListener { if (currentIndex in filteredList.indices) showLargeTextPopup("ファイル情報", filteredList[currentIndex].name) }
        zipPathText.setOnClickListener { showLargeTextPopup("パス情報", zipPathText.text.toString()) }
        textViewer.setOnClickListener { performFullCopy() }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            val downloadUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                currentFileUri = uri
                zipPathText.text = uri.path
                handleSelectedFile(uri)
            }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = getFileName(uri)
        if (fileName.lowercase().endsWith(".zip")) {
            scanZipFiles(uri)
        } else {
            scanLocalFolder(uri, fileName)
        }
    }

    // 仕様追加: 単体ファイル時にフォルダ内をファイル名昇順で走査
    private fun scanLocalFolder(targetUri: Uri, fileName: String) {
        thread {
            val tempList = mutableListOf<FileEntryInfo>()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            
            // Android 10環境でパスが取得できる場合のフォールバック走査
            val path = targetUri.path?.substringAfter("/document/primary:", "")
            if (path?.isNotEmpty() == true) {
                val file = File("/sdcard/$path")
                val parent = file.parentFile
                if (parent != null && parent.exists()) {
                    parent.listFiles { f -> f.isFile && f.extension.lowercase() == ext }?.forEach {
                        tempList.add(FileEntryInfo(it.name, it.lastModified(), false, false, Uri.fromFile(it)))
                    }
                }
            }

            // パスが取れない場合は単一ファイルとして扱う
            if (tempList.isEmpty()) {
                tempList.add(FileEntryInfo(fileName, System.currentTimeMillis(), false, false, targetUri))
            }

            // 単体ファイルモード: ファイル名昇順
            filteredList = tempList.sortedBy { it.name }
            currentIndex = filteredList.indexOfFirst { it.name == fileName }.coerceAtLeast(0)
            
            runOnUiThread { loadEntryByIndex(currentIndex) }
        }
    }

    private fun scanZipFiles(uri: Uri) {
        thread {
            val files = mutableListOf<FileEntryInfo>()
            val folders = mutableListOf<FileEntryInfo>()
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val allowedExts = (prefs.getString("ext_list", "txt,xml,py,sql,json,kt,kts,java,log,bat,md,gitignore,pro") ?: "").split(",")

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val info = FileEntryInfo(entry.name, entry.time, true, entry.isDirectory)
                            if (entry.isDirectory) folders.add(info)
                            else if (allowedExts.contains(entry.name.substringAfterLast('.', "").lowercase())) files.add(info)
                            zis.closeEntry(); entry = zis.nextEntry
                        }
                    }
                }
                // ZIPモード: ファイル最新順 -> フォルダ
                val all = files.sortedByDescending { it.lastModified } + folders.sortedByDescending { it.lastModified }
                filteredList = if (showFolders) all else all.filter { !it.isDirectory }
                
                runOnUiThread { if (filteredList.isNotEmpty()) loadEntryByIndex(0) }
            } catch (e: Exception) { runOnUiThread { textViewer.setText("Error: ${e.message}") } }
        }
    }

    private fun loadEntryByIndex(index: Int) {
        if (index !in filteredList.indices) return
        currentIndex = index
        val info = filteredList[index]
        fileInfoText.text = "${info.name}\n(${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(info.lastModified))})"
        
        if (info.isDirectory) { textViewer.setText("[ディレクトリ]"); return }

        thread {
            try {
                val text = if (info.isInsideZip) {
                    var res = ""
                    contentResolver.openInputStream(currentFileUri!!)?.use { ism ->
                        ZipInputStream(ism).use { zis ->
                            var e = zis.nextEntry
                            while (e != null) {
                                if (e.name == info.name) { res = zis.bufferedReader().readText(); break }
                                zis.closeEntry(); e = zis.nextEntry
                            }
                        }
                    }
                    res
                } else {
                    val stream = info.uri?.let { contentResolver.openInputStream(it) } ?: contentResolver.openInputStream(currentFileUri!!)
                    stream?.bufferedReader()?.readText() ?: ""
                }
                runOnUiThread { textViewer.setText(text) }
            } catch (e: Exception) { runOnUiThread { textViewer.setText("Read Error: ${e.message}") } }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        }
        return name
    }

    private fun showLargeTextPopup(title: String, message: String) {
        val tv = TextView(this).apply { text = message; textSize = 22f; setPadding(40,40,40,40); gravity = Gravity.CENTER; setTextIsSelectable(true) }
        AlertDialog.Builder(this).setTitle(title).setView(tv)
            .setPositiveButton("名コピー") { _, _ ->
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Name", message.substringAfterLast('/')))
            }.setNegativeButton("閉じる", null).show()
    }

    private fun performFullCopy() {
        val content = textViewer.text.toString()
        if (content.isEmpty()) return
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("Full", content))
        Toast.makeText(this, "【コピーできました】", Toast.LENGTH_SHORT).show()
    }

    private fun showFileListDialog() {
        if (filteredList.isEmpty()) return
        val names = filteredList.map { (if (it.isDirectory) "[DIR] " else "") + it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("一覧").setItems(names) { _, which -> loadEntryByIndex(which) }.show()
    }
}
