package com.cylonid.nativealpha.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.databinding.ActivityJsonEditorBinding
import java.io.File
import java.nio.charset.StandardCharsets

class JsonEditorActivity : ToolbarBaseActivity<ActivityJsonEditorBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarTitle("Edit Site Rules")

        val internalFile = File(filesDir, "sites.json")
        if (!internalFile.exists()) {
            // Initial copy from assets if it doesn't exist
            try {
                assets.open("sites.json").use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val jsonContent = if (internalFile.exists()) {
            internalFile.readText(StandardCharsets.UTF_8)
        } else {
            ""
        }

        binding.jsonEditor.setText(jsonContent)

        binding.btnSaveJson.setOnClickListener {
            try {
                val newContent = binding.jsonEditor.text.toString()
                // Simple validation check
                org.json.JSONObject(newContent)
                
                internalFile.writeText(newContent, StandardCharsets.UTF_8)
                Toast.makeText(this, "Rules saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid JSON format: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun inflateBinding(layoutInflater: android.view.LayoutInflater): ActivityJsonEditorBinding {
        return ActivityJsonEditorBinding.inflate(layoutInflater)
    }
}
