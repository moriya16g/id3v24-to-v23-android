package com.example.id3converter

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * 変換済みファイルの進捗を管理するクラス。
 * アプリ内部ストレージにフォルダ別の進捗ファイルを保存し、
 * 中断後に再開する際にスキップすべきファイルを判定する。
 */
class ProgressTracker(private val context: Context) {

    companion object {
        private const val PROGRESS_DIR = "conversion_progress"
    }

    private val progressDir: File by lazy {
        File(context.filesDir, PROGRESS_DIR).also { it.mkdirs() }
    }

    /**
     * 指定フォルダに対応する進捗ファイルを取得
     */
    private fun getProgressFile(folderPath: String): File {
        // フォルダパスをファイル名に変換（/をアンダースコアに）
        val safeName = folderPath.replace(File.separatorChar, '_')
            .replace(':', '_')
            .take(200) + ".txt"
        return File(progressDir, safeName)
    }

    /**
     * 変換済みファイルのセットを読み込む
     */
    fun loadCompletedFiles(folderPath: String): MutableSet<String> {
        val progressFile = getProgressFile(folderPath)
        val completed = mutableSetOf<String>()
        if (!progressFile.exists()) return completed

        try {
            BufferedReader(FileReader(progressFile)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        completed.add(line.trim())
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            // 読み込みエラーの場合は空セットを返す
        }
        return completed
    }

    /**
     * 変換完了したファイルを進捗に追記
     */
    fun markCompleted(folderPath: String, filePath: String) {
        val progressFile = getProgressFile(folderPath)
        try {
            BufferedWriter(FileWriter(progressFile, true)).use { writer ->
                writer.write(filePath)
                writer.newLine()
            }
        } catch (e: Exception) {
            // 書き込みエラーは無視（変換自体は成功しているので）
        }
    }

    /**
     * 指定フォルダの進捗をクリア（最初からやり直す場合）
     */
    fun clearProgress(folderPath: String) {
        val progressFile = getProgressFile(folderPath)
        if (progressFile.exists()) {
            progressFile.delete()
        }
    }

    /**
     * 指定フォルダの進捗ファイルが存在するか
     */
    fun hasProgress(folderPath: String): Boolean {
        val progressFile = getProgressFile(folderPath)
        return progressFile.exists() && progressFile.length() > 0
    }

    /**
     * 進捗の件数を取得
     */
    fun getCompletedCount(folderPath: String): Int {
        return loadCompletedFiles(folderPath).size
    }
}
