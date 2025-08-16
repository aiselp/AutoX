package org.autojs.autojs.ui.main.drawer

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.autojs.autojs.network.VersionService2
import org.autojs.autojs.network.entity.GithubReleaseInfo
import org.autojs.autojs.network.entity.isLatestVersion
import org.autojs.autoxjs.R
import java.io.File

class DrawerViewModel(private val context: Application) : AndroidViewModel(context) {

    var githubReleaseInfo by mutableStateOf<GithubReleaseInfo?>(null)
        private set

    fun checkUpdate(onUpdate: () -> Unit = {}, onComplete: () -> Unit = {}, toast: Boolean = true) {
        if (toast)
            showToast(context.getString(R.string.text_checking_for_updates))
        viewModelScope.launch {
            try {
                var releaseInfo = VersionService2.gitUpdateCheckApi.getGithubLastReleaseInfo()

                var isLatestVersion = releaseInfo.isLatestVersion()
                if (isLatestVersion == null) {
                    //Get release list
                    VersionService2.gitUpdateCheckApi.getGithubReleaseInfoList()
                        .firstOrNull { it.targetCommitish == "dev-test" && !it.prerelease }?.let {
                            releaseInfo = it
                            isLatestVersion = releaseInfo.isLatestVersion()
                        }
                }
                if (isLatestVersion == null) {
                    //Can't find information
                    if (toast) showToast(
                        context.getString(
                            R.string.text_check_update_error,
                            context.getString(R.string.text_update_information_not_found)
                        )
                    )
                    return@launch
                }
                if (isLatestVersion == true) {
                    //is the latest version
                    if (toast) showToast(context.getString(R.string.text_is_latest_version))
                } else {
                    //new version
                    githubReleaseInfo = releaseInfo
                    onUpdate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (toast) showToast(
                    context.getString(
                        R.string.text_check_update_error,
                        e.localizedMessage ?: ""
                    )
                )
            } finally {
                onComplete()
            }
        }

    }


    fun getApkNameAndDownloadLink(): Pair<String, String?> {
        val specificAsset = githubReleaseInfo?.assets?.firstOrNull {
            it.browserDownloadUrl.contains(
                getUserArch(), ignoreCase = true
            )
        }

        return Pair(
            specificAsset?.name.orEmpty(), specificAsset?.browserDownloadUrl ?: getUniversalLink()
        )
    }

    private fun getUserArch(): String {
        return Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    }

    private fun getUniversalLink(): String {
        return githubReleaseInfo?.assets?.firstOrNull {
            it.browserDownloadUrl.contains(
                "universal", ignoreCase = true
            )
        }?.browserDownloadUrl.orEmpty()
    }

    fun downloadApk() {
        val (fileName, url) = getApkNameAndDownloadLink()
        /**
         * fix by aiselp:
         * 对于安卓Q以上的系统，必须使用标准的公开储存目录下载
         */
        val filePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        if (filePath.exists()) {
            showToast("文件已存在:${filePath.absolutePath}")
            return
        }

        val request = DownloadManager.Request(Uri.parse(url)).setTitle(fileName).setDescription(url)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(filePath))

        try {
            downloadManager.enqueue(request)
            showToast("正在下载$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("下载出错: ${e.localizedMessage}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}