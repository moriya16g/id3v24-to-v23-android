package com.example.id3converter

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

data class ConversionResult(
    val totalCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val alreadyDoneCount: Int,
    val cancelled: Boolean
)

object Id3Converter {

    @Volatile
    private var cancelRequested = AtomicBoolean(false)

    init {
        // Suppress JAudioTagger verbose logging
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /**
     * 変換処理のキャンセルを要求する
     */
    fun requestCancel() {
        cancelRequested.set(true)
    }

    /**
     * キャンセル状態をリセットする
     */
    fun resetCancel() {
        cancelRequested.set(false)
    }

    fun convertFolder(
        folder: File,
        overwrite: Boolean,
        progressTracker: ProgressTracker,
        resume: Boolean,
        onProgress: (current: Int, total: Int) -> Unit,
        onLog: (String) -> Unit
    ): ConversionResult {
        var totalCount = 0
        var successCount = 0
        var skippedCount = 0
        var errorCount = 0
        var alreadyDoneCount = 0

        val folderPath = folder.absolutePath

        // 再開モードでない場合、進捗をクリア
        if (!resume) {
            progressTracker.clearProgress(folderPath)
        }

        // 変換済みファイルのセットを読み込む
        val completedFiles = if (resume) {
            progressTracker.loadCompletedFiles(folderPath)
        } else {
            mutableSetOf()
        }

        if (resume && completedFiles.isNotEmpty()) {
            onLog("前回の進捗を読み込みました: ${completedFiles.size}件は変換済み")
        }

        onLog("フォルダを検索中...")
        val mp3Files = findMp3Files(folder)
        val totalFiles = mp3Files.size
        onLog("検索完了: ${totalFiles}個のMP3ファイルが見つかりました")

        if (resume && completedFiles.isNotEmpty()) {
            onLog("未処理のファイル: ${totalFiles - completedFiles.size}件")
        }

        for (file in mp3Files) {
            // キャンセルチェック
            if (cancelRequested.get()) {
                onLog("\n--- 中断されました ---")
                onLog("進捗は保存済みです。次回「前回の続きから再開」で続行できます。")
                return ConversionResult(
                    totalCount, successCount, skippedCount, errorCount, alreadyDoneCount, true
                )
            }

            totalCount++
            onProgress(totalCount, totalFiles)

            // 既に変換済みのファイルはスキップ
            if (completedFiles.contains(file.absolutePath)) {
                alreadyDoneCount++
                continue
            }

            try {
                val result = convertFile(file, overwrite)
                when (result) {
                    ConvertStatus.SUCCESS -> {
                        successCount++
                        // 成功したら進捗に記録
                        progressTracker.markCompleted(folderPath, file.absolutePath)
                        if (successCount % 10 == 0 || successCount <= 5) {
                            onLog("[成功] ${file.name} ($totalCount/$totalFiles)")
                        }
                    }
                    ConvertStatus.SKIPPED -> {
                        skippedCount++
                        // スキップもID3v2.4でないファイルとして進捗に記録
                        progressTracker.markCompleted(folderPath, file.absolutePath)
                        if (skippedCount <= 5) {
                            onLog("[スキップ] ${file.name} (ID3v2.4ではありません)")
                        }
                    }
                    ConvertStatus.ERROR -> {
                        errorCount++
                        onLog("[エラー] ${file.name}")
                    }
                }
            } catch (e: Exception) {
                errorCount++
                onLog("[エラー] ${file.name}: ${e.message}")
            }

            // 100件ごとにサマリーログ
            if (totalCount % 100 == 0) {
                onLog("--- 進捗: $totalCount/$totalFiles 件処理済み (成功:$successCount スキップ:$skippedCount エラー:$errorCount) ---")
            }
        }

        onLog("\n--- 処理完了 ---")
        return ConversionResult(totalCount, successCount, skippedCount, errorCount, alreadyDoneCount, false)
    }

    private fun findMp3Files(folder: File): List<File> {
        val mp3Files = mutableListOf<File>()
        if (!folder.exists() || !folder.isDirectory) return mp3Files

        folder.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.equals("mp3", ignoreCase = true)) {
                mp3Files.add(file)
            }
        }
        // ソートして再実行時の順序を安定させる
        mp3Files.sortBy { it.absolutePath }
        return mp3Files
    }

    private fun convertFile(file: File, overwrite: Boolean): ConvertStatus {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: return ConvertStatus.SKIPPED

        // Check if the tag is ID3v2.4
        if (tag !is ID3v24Tag) {
            return ConvertStatus.SKIPPED
        }

        // Convert ID3v2.4 to ID3v2.3
        val v23Tag = ID3v23Tag(tag)

        if (overwrite) {
            // Overwrite the original file
            audioFile.tag = v23Tag
            audioFile.commit()
        } else {
            // Save as a new file with _v23 suffix
            val newFileName = file.nameWithoutExtension + "_v23." + file.extension
            val newFile = File(file.parent, newFileName)
            file.copyTo(newFile, overwrite = true)

            val newAudioFile = AudioFileIO.read(newFile)
            newAudioFile.tag = v23Tag
            newAudioFile.commit()
        }

        return ConvertStatus.SUCCESS
    }

    private enum class ConvertStatus {
        SUCCESS, SKIPPED, ERROR
    }
}
