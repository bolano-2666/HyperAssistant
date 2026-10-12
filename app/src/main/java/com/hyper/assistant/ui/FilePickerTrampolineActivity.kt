package com.hyper.assistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyper.assistant.util.FileStorageHelper
import kotlinx.coroutines.launch

/**
 * 悬浮窗文件/相册选择透明跳板 Activity
 * 解决 Android 前台悬浮 Service 无法直接调用 ActivityResult 的系统级限制
 */
class FilePickerTrampolineActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "extra_pick_mode"
        const val MODE_IMAGE = "mode_image"
        const val MODE_DOCUMENT = "mode_document"

        // 静态回调，由 FloatingOverlayService 注册监听
        var onFileSelectedListener: ((FileStorageHelper.SelectedFileInfo) -> Unit)? = null
    }

    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val fileInfo = FileStorageHelper.parseSelectedUri(this@FilePickerTrampolineActivity, uri)
                if (fileInfo != null) {
                    onFileSelectedListener?.invoke(fileInfo)
                } else {
                    Toast.makeText(this@FilePickerTrampolineActivity, "无法读取选中文件", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_PICK_MODE) ?: MODE_DOCUMENT
        val mimeType = if (mode == MODE_IMAGE) "image/*" else "*/*"

        try {
            getContentLauncher.launch(mimeType)
        } catch (e: Exception) {
            Toast.makeText(this, "启动系统文件选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
