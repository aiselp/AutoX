package com.aiselp.autox.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.IntRange

class DeviceInfo(context: Context) {
    private var versionCode = 0
    private var versionName: String? = null
    private val buildVersion: String = Build.VERSION.INCREMENTAL
    private val releaseVersion: String = Build.VERSION.RELEASE

    @IntRange(from = 0L)
    private val sdkVersion = Build.VERSION.SDK_INT
    private val buildID: String = Build.DISPLAY
    private val brand: String = Build.BRAND
    private val manufacturer: String = Build.MANUFACTURER
    private val device: String = Build.DEVICE
    private val model: String = Build.MODEL
    private val product: String = Build.PRODUCT
    private val hardware: String = Build.HARDWARE

    @SuppressLint("NewApi")
    private val abis: Array<String> =
        Build.SUPPORTED_ABIS

    @SuppressLint("NewApi")
    private val abis32Bits: Array<String>? =
        Build.SUPPORTED_32_BIT_ABIS

    @SuppressLint("NewApi")
    private val abis64Bits: Array<String>? =
        Build.SUPPORTED_64_BIT_ABIS

    init {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (var4: PackageManager.NameNotFoundException) {
            null
        }

        if (packageInfo != null) {
            this.versionCode = packageInfo.versionCode
            this.versionName = packageInfo.versionName
        } else {
            this.versionCode = -1
            this.versionName = null
        }
    }

    fun toMarkdown(): String {
        return """
             Device info:
             ---
             <table>
             <tr><td>App version</td><td>${versionName}</td></tr>
             <tr><td>App version code</td><td>${versionCode}</td></tr>
             <tr><td>Android build version</td><td>${buildVersion}</td></tr>
             <tr><td>Android release version</td><td>${releaseVersion}</td></tr>
             <tr><td>Android SDK version</td><td>${sdkVersion}</td></tr>
             <tr><td>Android build ID</td><td>${buildID}</td></tr>
             <tr><td>Device brand</td><td>${brand}</td></tr>
             <tr><td>Device manufacturer</td><td>${manufacturer}</td></tr>
             <tr><td>Device name</td><td>${device}</td></tr>
             <tr><td>Device model</td><td>${model}</td></tr>
             <tr><td>Device product name</td><td>${product}</td></tr>
             <tr><td>Device hardware name</td><td>${hardware}</td></tr>
             <tr><td>ABIs</td><td>${abis.contentToString()}</td></tr>
             <tr><td>ABIs (32bit)</td><td>${abis32Bits.contentToString()}</td></tr>
             <tr><td>ABIs (64bit)</td><td>${abis64Bits.contentToString()}</td></tr>
             </table>
             
             """.trimIndent()
    }

    override fun toString(): String {
        return """
            App version: ${versionName}
            App version code: ${versionCode}
            Android build version: ${buildVersion}
            Android release version: ${releaseVersion}
            Android SDK version: ${sdkVersion}
            Android build ID: ${buildID}
            Device brand: ${brand}
            Device manufacturer: ${manufacturer}
            Device name: ${device}
            Device model: ${model}
            Device product name: ${product}
            Device hardware name: ${hardware}
            ABIs: ${abis.contentToString()}
            ABIs (32bit): ${abis32Bits.contentToString()}
            ABIs (64bit): ${abis64Bits.contentToString()}
            """.trimIndent()
    }
}
