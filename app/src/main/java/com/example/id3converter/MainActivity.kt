package com.example.id3converter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.id3converter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var progressTracker: ProgressTracker
    private var selectedFolder: File? = null
    private var overwriteMode = true
    private var conversionJob: Job? = null
    private var isConverting = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openFolderPicker()
        } else {
            Toast.makeText(this, "ストレージへのアクセス権限が必要です", Toast.LENGTH_LONG).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            openFolderPicker()
        } else {
            Toast.makeText(this, "すべてのファイルへのアクセス権限が必要です", Toast.LENGTH_LONG).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            val path = getPathFromUri(treeUri)
            if (path != null) {
                selectedFolder = File(path)
                binding.tvSelectedFolder.text = path
                binding.btnConvert.isEnabled = true
                updateResumeInfo()
            } else {
                Toast.makeText(this, "フォルダパスの取得に失敗しました", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressTracker = ProgressTracker(this)
        setupUI()
    }

    private fun setupUI() {
        binding.btnSelectFolder.setOnClickListener {
            checkPermissionsAndOpenPicker()
        }

        binding.radioGroupSaveMode.setOnCheckedChangeListener { _, checkedId ->
            overwriteMode = (checkedId == R.id.radioOverwrite)
        }

        binding.btnConvert.setOnClickListener {
            selectedFolder?.let { folder ->
                showConfirmDialog(folder)
            }
        }

        binding.btnCancel.setOnClickListener {
            cancelConversion()
        }

        binding.btnClearProgress.setOnClickListener {
            selectedFolder?.let { folder ->
                showClearProgressDialog(folder)
            }
        }

        binding.btnConvert.isEnabled = false
        binding.btnCancel.visibility = android.view.View.GONE
    }

    private fun updateResumeInfo() {
        selectedFolder?.let { folder ->
            val hasProgress = progressTracker.hasProgress(folder.absolutePath)
            if (hasProgress) {
                val count = progressTracker.getCompletedCount(folder.absolutePath)
                binding.checkboxResume.isEnabled = true
                binding.checkboxResume.isChecked = true
                binding.tvResumeInfo.text = "前回の進捗: ${count}件処理済み"
                binding.tvResumeInfo.visibility = android.view.View.VISIBLE
                binding.btnClearProgress.visibility = android.view.View.VISIBLE
            } else {
                binding.checkboxResume.isEnabled = false
                binding.checkboxResume.isChecked = false
                binding.tvResumeInfo.text = ""
                binding.tvResumeInfo.visibility = android.view.View.GONE
                binding.btnClearProgress.visibility = android.view.View.GONE
            }
        }
    }

    private fun checkPermissionsAndOpenPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                openFolderPicker()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("権限が必要です")
                    .setMessage("MP3ファイルの変換のため、すべてのファイルへのアクセス権限が必要です。")
                    .setPositiveButton("設定を開く") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissions.isNotEmpty()) {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            } else {
                openFolderPicker()
            }
        } else {
            openFolderPicker()
        }
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun showConfirmDialog(folder: File) {
        val modeText = if (overwriteMode) "上書き保存" else "別ファイル名で保存（_v23を付加）"
        val resumeText = if (binding.checkboxResume.isChecked) "前回の続きから再開" else "最初から実行"
        AlertDialog.Builder(this)
            .setTitle("変換の確認")
            .setMessage(
                "以下の設定で変換を実行しますか？\n\n" +
                "フォルダ: ${folder.absolutePath}\n" +
                "保存モード: $modeText\n" +
                "再開モード: $resumeText\n\n" +
                "※サブフォルダ内のMP3ファイルも対象になります\n" +
                "※途中で「中断」ボタンで停止できます"
            )
            .setPositiveButton("変換開始") { _, _ ->
                startConversion(folder)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showClearProgressDialog(folder: File) {
        AlertDialog.Builder(this)
            .setTitle("進捗のクリア")
            .setMessage("前回の変換進捗をクリアしますか？\n次回は最初から変換を実行します。")
            .setPositiveButton("クリア") { _, _ ->
                progressTracker.clearProgress(folder.absolutePath)
                updateResumeInfo()
                Toast.makeText(this, "進捗をクリアしました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun cancelConversion() {
        AlertDialog.Builder(this)
            .setTitle("中断の確認")
            .setMessage("変換処理を中断しますか？\n進捗は保存されるので、次回続きから再開できます。")
            .setPositiveButton("中断する") { _, _ ->
                Id3Converter.requestCancel()
                binding.tvStatus.text = "中断処理中..."
            }
            .setNegativeButton("続行する", null)
            .show()
    }

    private fun startConversion(folder: File) {
        isConverting = true
        Id3Converter.resetCancel()

        binding.btnConvert.visibility = android.view.View.GONE
        binding.btnCancel.visibility = android.view.View.VISIBLE
        binding.btnSelectFolder.isEnabled = false
        binding.btnClearProgress.visibility = android.view.View.GONE
        binding.radioGroupSaveMode.isEnabled = false
        binding.checkboxResume.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.tvStatus.text = "変換中..."
        binding.tvLog.text = ""

        val resume = binding.checkboxResume.isChecked

        conversionJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                Id3Converter.convertFolder(
                    folder,
                    overwriteMode,
                    progressTracker,
                    resume,
                    onProgress = { current, total ->
                        lifecycleScope.launch {
                            binding.progressBar.max = total
                            binding.progressBar.progress = current
                            binding.tvStatus.text = "変換中... $current / $total"
                        }
                    },
                    onLog = { message ->
                        lifecycleScope.launch {
                            binding.tvLog.append("$message\n")
                            binding.scrollViewLog.post {
                                binding.scrollViewLog.fullScroll(android.view.View.FOCUS_DOWN)
                            }
                        }
                    }
                )
            }

            isConverting = false
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnConvert.visibility = android.view.View.VISIBLE
            binding.btnCancel.visibility = android.view.View.GONE
            binding.btnConvert.isEnabled = true
            binding.btnSelectFolder.isEnabled = true
            binding.radioGroupSaveMode.isEnabled = true
            updateResumeInfo()

            val statusText = if (result.cancelled) {
                "中断しました: ${result.successCount}件変換済み"
            } else {
                "変換完了: ${result.successCount}件成功, " +
                    "${result.skippedCount}件スキップ, ${result.errorCount}件エラー"
            }
            binding.tvStatus.text = statusText

            val title = if (result.cancelled) "中断しました" else "変換完了"
            val alreadyDoneText = if (result.alreadyDoneCount > 0) {
                "前回処理済み(スキップ): ${result.alreadyDoneCount}件\n"
            } else ""

            AlertDialog.Builder(this@MainActivity)
                .setTitle(title)
                .setMessage(
                    "処理結果:\n\n" +
                    alreadyDoneText +
                    "今回変換成功: ${result.successCount}件\n" +
                    "スキップ(v2.4以外): ${result.skippedCount}件\n" +
                    "エラー: ${result.errorCount}件\n" +
                    "合計処理: ${result.totalCount}件" +
                    if (result.cancelled) "\n\n次回「前回の続きから再開」で続行できます。" else ""
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        val docId = uri.lastPathSegment ?: return null

        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }

        val split = docId.split(":")
        if (split.size == 2) {
            val storageId = split[0]
            val relativePath = split[1]
            val possiblePaths = listOf(
                "/storage/$storageId/$relativePath",
                "/mnt/media_rw/$storageId/$relativePath"
            )
            for (path in possiblePaths) {
                if (File(path).exists()) return path
            }
        }

        return null
    }
}
