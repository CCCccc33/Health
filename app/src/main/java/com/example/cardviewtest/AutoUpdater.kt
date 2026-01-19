package com.example.cardviewtest

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.security.SecureRandom
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

/**
 * 自动更新工具类（Java 转 Kotlin）
 * 保持原有核心功能：版本检测、APK 下载、进度展示、Android 7.0+ 安装适配
 */
class AutoUpdater(private val mContext: Context) {
    // 下载安装包的网络路径
    private var apkUrl = "https://gitee.com/CCCccc333/Health/releases/download/v1.0/"
    private val checkUrl = apkUrl + "output-metadata.json"

    // 保存APK的文件名
    private val saveFileName = "app-release.apk"
    private val apkFile: File

    // 下载线程
    private var downLoadThread: Thread? = null
    private var progress = 0 // 当前进度
    // 是否是最新的应用,默认为false
    private var isNew = false
    private var intercept = false
    // 进度条与通知UI刷新的handler和msg常量
    private var mProgress: ProgressBar? = null
    private var txtStatus: TextView? = null
    private lateinit var systemUA : String

    // Handler 消息常量
    private val DOWN_UPDATE = 1
    private val DOWN_OVER = 2
    private val SHOWDOWN = 3

    // UI 刷新 Handler（绑定主线程）
    private val mHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            SHOWDOWN -> ShowUpdateDialog()
            DOWN_UPDATE -> {
                txtStatus?.text = "$progress%"
                mProgress?.progress = progress
            }
            DOWN_OVER -> {
                Toast.makeText(mContext, "下载完毕", Toast.LENGTH_SHORT).show()
                try {
                    installAPK()
                }catch (e:Exception){
                    e.printStackTrace()
                }

            }
        }
        true
    }

    init {
        // 初始化APK保存路径（对应 App 外部私有存储的 Download 目录）
        val downloadDir = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        apkFile = File(downloadDir, saveFileName)
        systemUA = getSystemUserAgent(mContext)
    }

    fun getSystemUserAgent(context: Context): String {
        return try {
            // 创建临时 WebView（无需显示，仅用于获取 UA）
            val webView = WebView(context)
            webView.settings.userAgentString ?: getDefaultUserAgent() // 兜底
        } catch (e: Exception) {
            // 异常时返回兜底 UA（适配无 WebView 环境）
            getDefaultUserAgent()
        }
    }

    // 兜底 UA（适配 Android 不同版本）
    private fun getDefaultUserAgent(): String {
        return WebSettings.getDefaultUserAgent(mContext)
    }

    /**
     * 显示版本更新提示弹窗
     */
    fun ShowUpdateDialog() {
        AlertDialog.Builder(mContext)
            .setCancelable(false)
            .setTitle("软件版本更新")
            .setMessage("有最新的软件包，请下载并安装!")
            .setPositiveButton("立即下载") { _, _ ->
                ShowDownloadDialog()
            }
            .setNegativeButton("以后再说") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * 显示下载进度弹窗
     */
    private fun ShowDownloadDialog() {
        AlertDialog.Builder(mContext)
            .setCancelable(false)
            .setTitle("软件版本更新")
            .apply {
                // 加载进度布局
                val inflater = LayoutInflater.from(mContext)
                val v = inflater.inflate(R.layout.progress, null)
                mProgress = v.findViewById(R.id.progress)
                txtStatus = v.findViewById(R.id.txtStatus)
                setView(v)
            }
            .setNegativeButton("取消") { _, _ ->
                intercept = true
            }
            .show()

        // 开始下载APK
        DownloadApk()
    }

    /**
     * 检查是否有版本更新（子线程执行，避免阻塞主线程）
     */
    fun CheckUpdate() {
        Thread {
            // 获取本地版本号
            val localVersion = try {
                mContext.packageManager.getPackageInfo(mContext.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                "1"
            }

            var versionName = "1"
            var outputFile = ""
            Log.d("URL","URL:$checkUrl")
            val config = doGet(checkUrl)

            if (!config.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 匹配 outputFile
                val outputFilePattern = Pattern.compile("\"outputFile\":\\s*\"(?<m>[^\"]*?)\"")
                val outputFileMatcher = outputFilePattern.matcher(config)
                if (outputFileMatcher.find()) {
                    outputFile = outputFileMatcher.group("m") ?: ""
                }

                // 匹配 versionName
                val versionPattern = Pattern.compile("\"versionName\":\\s*\"(?<m>[^\"]*?)\"")
                val versionMatcher = versionPattern.matcher(config)
                if (versionMatcher.find()) {
                    val v = versionMatcher.group("m") ?: ""
                    versionName = v.replace("v1.0.", "")
                }
            }

            // 版本对比（转为 Long 避免字符串对比误差）
            try {
                if (localVersion == null){
                    Log.d("Version","Version为空")
                    return@Thread
                }
                Log.d("Version","localVersion:${localVersion},remoteVer:${versionName}")
                if (isVersionNewer(localVersion, versionName)) {
                    apkUrl += outputFile
                    mHandler.sendEmptyMessage(SHOWDOWN)
                }
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * 分段版本对比工具方法（核心：判断 最新版本 > 本地版本）
     * @param localVersion 本地版本号（如 "1.0"）
     * @param remoteVersion 远程最新版本号（如 "2.0"）
     * @return true 表示有更新，false 表示当前是最新版本
     */
    private fun isVersionNewer(localVersion: String, remoteVersion: String): Boolean {
        // 分割版本号（按 "." 分割，过滤无法转换为数字的片段）
        val localParts = localVersion.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }

        // 逐段对比版本号（长度不一致时，缺失片段按 0 处理）
        val maxLength = maxOf(localParts.size, remoteParts.size)
        for (i in 0 until maxLength) {
            val localPart = if (i < localParts.size) localParts[i] else 0
            val remotePart = if (i < remoteParts.size) remoteParts[i] else 0

            when {
                remotePart > localPart -> return true // 远程版本更高，有更新
                remotePart < localPart -> return false // 本地版本更高，无更新
            }
        }
        return false // 版本号完全一致，无更新
    }

    /**
     * 从服务器下载APK安装包
     */
    fun DownloadApk() {
        downLoadThread = Thread {
            try {
                DownApkWork.run()
            }catch (e: Exception){
                e.printStackTrace()
                Log.e("APK_DOWNLOAD_ERROR", "APK下载过程中出现异常", e)
            }
        }
        downLoadThread?.start()
    }

    /**
     * APK 下载任务（ Runnable 实现）
     */
    private val DownApkWork = Runnable {
        var url: URL? = null
        try {
            // HTTPS 证书信任配置（HTTP 可注释该段）
            val sslContext = SSLContext.getInstance("SSL")
            val tm: Array<TrustManager> = arrayOf(MyX509TrustManager())
            sslContext.init(null, tm, SecureRandom())
            val ssf: SSLSocketFactory = sslContext.socketFactory

            url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            if (conn is HttpsURLConnection) { // 仅对 HTTPS 连接生效，避免类型转换异常
                conn.sslSocketFactory = ssf
            }
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 60000
            // 携带桌面版 User-Agent 解决 403 问题（补充）
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "*/*")
            conn.connect()
            Log.e("TAG_APK_DOWNLOAD", "请求失败，响应码：${conn.responseCode}，响应消息：${conn.responseMessage}")
            val length = conn.contentLength
            val ins = conn.inputStream
            val fos = FileOutputStream(apkFile)
            val buf = ByteArray(1024)
            var count = 0
            var numread: Int

            while (!intercept) {
                numread = ins.read(buf)
                count += numread
                // 计算下载进度
                progress = if (length <= 0) 0 else ((count.toFloat() / length) * 100).toInt()
                // 发送进度更新消息
                mHandler.sendEmptyMessage(DOWN_UPDATE)

                if (numread <= 0) {
                    // 下载完成，发送安装消息
                    mHandler.sendEmptyMessage(DOWN_OVER)
                    break
                }
                fos.write(buf, 0, numread)
            }

            fos.close()
            ins.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 安装APK（适配 Android 7.0+ FileProvider）
     */
    fun installAPK() {
        if (!apkFile.exists()) return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 安装完成后可打开新版本
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授予临时文件读取权限
        }

        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 用 FileProvider 生成安全 Uri
            val packageName = mContext.applicationContext.packageName
            val authority = "$packageName.fileprovider"
            FileProvider.getUriForFile(mContext, authority, apkFile)
        } else {
            // Android 7.0 以下直接用文件 Uri
            Uri.fromFile(apkFile)
        }

        // 设置数据类型和 Uri
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        mContext.startActivity(intent)

        // 结束当前进程（可选，安装完成后用户可选择打开/完成）
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * GET 请求获取版本配置信息
     * @param httpurl 请求地址
     * @return 响应结果字符串
     */
    fun doGet(httpurl: String): String? {
        var connection: HttpURLConnection? = null
        var isStream: InputStream? = null
        var br: BufferedReader? = null
        var result: String? = null

        try {
            val url = URL(httpurl)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                connect()
            }

            if (connection.responseCode == 200) {
                isStream = connection.inputStream
                br = BufferedReader(InputStreamReader(isStream, "UTF-8"))
                val sbf = StringBuffer()
                var temp: String?
                while (br.readLine().also { temp = it } != null) {
                    sbf.append(temp)
                    sbf.append("\r\n")
                }
                result = sbf.toString()
            }
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // 关闭资源
            br?.close()
            isStream?.close()
            connection?.disconnect()
        }

        return result
    }
}