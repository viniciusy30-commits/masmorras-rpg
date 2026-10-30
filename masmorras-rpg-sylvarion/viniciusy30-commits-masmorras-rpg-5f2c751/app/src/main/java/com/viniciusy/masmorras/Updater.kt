package com.viniciusy.masmorras

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Updater {

    private const val REPO = "viniciusy30-commits/masmorras-rpg"
    private const val PREFS = "updater_prefs"
    private const val KEY_LAST_CHECK = "last_check"
    private const val SIX_HOURS = 6 * 60 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun autoCheck(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < SIX_HOURS) return
        check(activity, silent = true) { hasUpdate, _, _ ->
            if (hasUpdate) {
                // só avisa se tiver nova
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            }
        }
    }

    fun check(
        activity: Activity,
        silent: Boolean = false,
        statusCallback: ((String) -> Unit)? = null,
        resultCallback: ((hasUpdate: Boolean, tag: String?, apkUrl: String?) -> Unit)? = null
    ) {
        if (!silent) statusCallback?.invoke(activity.getString(R.string.procurando))

        executor.execute {
            try {
                val url = URL("https://api.github.com/repos/$REPO/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "Masmorras-App")
                val code = conn.responseCode
                if (code != 200) {
                    mainHandler.post {
                        if (!silent) statusCallback?.invoke("Erro ao verificar ($code)")
                        resultCallback?.invoke(false, null, null)
                    }
                    return@execute
                }
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val versionStr = tag.removePrefix("v").trim()
                val remoteCode = versionStr.toIntOrNull() ?: 0
                val localCode = try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode.toInt()
                } catch (e: Exception) {
                    1
                }

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url", null)
                            break
                        }
                    }
                }

                val hasUpdate = remoteCode > localCode && apkUrl != null

                mainHandler.post {
                    if (hasUpdate) {
                        if (silent) {
                            showUpdateDialog(activity, tag, apkUrl!!)
                        } else {
                            statusCallback?.invoke("Nova versão $tag disponível!")
                            showUpdateDialog(activity, tag, apkUrl!!)
                        }
                    } else {
                        if (!silent) {
                            statusCallback?.invoke(activity.getString(R.string.ja_atualizado))
                        }
                    }
                    resultCallback?.invoke(hasUpdate, tag, apkUrl)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!silent) {
                        statusCallback?.invoke(activity.getString(R.string.sem_internet))
                    }
                    resultCallback?.invoke(false, null, null)
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, tag: String, apkUrl: String) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.nova_versao))
            .setMessage("Versão $tag disponível.\nDeseja baixar e instalar agora?")
            .setPositiveButton(activity.getString(R.string.atualizar)) { _, _ ->
                downloadAndInstall(activity, apkUrl)
            }
            .setNegativeButton(activity.getString(R.string.depois), null)
            .setCancelable(true)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, apkUrl: String) {
        // Verifica permissão de instalar apps desconhecidos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity)
                    .setTitle("Permissão necessária")
                    .setMessage(activity.getString(R.string.instalar_desconhecidos))
                    .setPositiveButton(activity.getString(R.string.abrir_config)) { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return
            }
        }

        val progressDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.baixar))
            .setCancelable(false)
            .create()

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val statusText = TextView(activity).apply {
            text = "0%"
            setPadding(40, 20, 40, 20)
        }
        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(progressBar)
            addView(statusText)
        }
        progressDialog.setView(container)
        progressDialog.show()

        executor.execute {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.connect()
                val total = conn.contentLength
                val dir = File(activity.cacheDir, "updates")
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, "Masmorras.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                mainHandler.post {
                                    progressBar.progress = pct
                                    statusText.text = "$pct%"
                                }
                            }
                        }
                    }
                }

                mainHandler.post {
                    progressDialog.dismiss()
                    installApk(activity, outFile)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "Falha no download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(activity: Activity, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao abrir instalador: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
