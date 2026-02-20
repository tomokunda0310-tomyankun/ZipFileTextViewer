package com.papa.zipfiletextviewer

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private val defaultExts = "txt,xml,py,sql,json,kt,kts,java,log,bat,md,gitignore,pro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val editExtensions = findViewById<EditText>(R.id.editExtensions)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        editExtensions.setText(prefs.getString("ext_list", defaultExts))

        btnSave.setOnClickListener {
            val newList = editExtensions.text.toString().lowercase().replace(" ", "")
            prefs.edit().putString("ext_list", newList).apply()
            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
