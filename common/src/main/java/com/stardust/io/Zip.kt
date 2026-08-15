package com.stardust.io

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


object Zip {

    @JvmStatic
    fun unzip(stream: InputStream, dir: File) {
        stream.use {
            ZipInputStream(stream).use { zis ->
                var z: ZipEntry?
                while (zis.nextEntry.also { z = it } != null) {
                    val entry = z ?: continue
                    //部分APK(zipflinger/aapt2打包)会包含文件名为空的条目，跳过，避免把目录当文件打开导致EISDIR
                    if (entry.name.isEmpty()) continue
                    val file = File(dir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        file.outputStream().use { fos ->
                            zis.copyTo(fos)
                            zis.closeEntry()
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun unzip(zipFile: File, dir: File) {
        unzip(FileInputStream(zipFile), dir)
    }

    fun unzip(fromFile: File, newDir: File, unzipPath: String) {
        var unzipPath1 = unzipPath
        if (!unzipPath1.endsWith("/")) unzipPath1 += "/"
        if (!newDir.exists()) newDir.mkdirs()
        var z: ZipEntry?
        ZipInputStream(fromFile.inputStream()).use { input ->
            while (input.nextEntry.also { z = it } != null) {
                val zipEntry = z ?: continue
                if (!zipEntry.isDirectory && zipEntry.name.startsWith(unzipPath1)) {
                    val f = File(newDir, zipEntry.name.replace(Regex("^$unzipPath1"), ""))
                    f.parentFile?.let {
                        if (!it.exists()) it.mkdirs()
                    }
                    f.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
            }
        }
    }

    fun zipFileList(fileList: List<File>, zipOut: ZipOutputStream) {
        for (file in fileList) {
            zipFile(file, file.name, zipOut)
        }
    }

    fun zipFile(fileToZip: File, name: String, zipOut: ZipOutputStream) {
        var fileName = name

        // 如果是目录，递归处理其中的文件
        if (fileToZip.isDirectory) {
            if (!fileName.endsWith("/")) {
                fileName += "/"
            }
            val zipEntry = ZipEntry(fileName)
            zipOut.putNextEntry(zipEntry)
            zipOut.closeEntry()

            val children = fileToZip.listFiles() ?: return
            for (childFile in children) {
                zipFile(childFile, fileName + childFile.name, zipOut)
            }
            return
        }

        // 如果是文件，读取其内容并写入压缩文件
        val fis = FileInputStream(fileToZip)
        zipOut.putNextEntry(ZipEntry(fileName))
        fis.copyTo(zipOut)
        zipOut.closeEntry()
    }

}