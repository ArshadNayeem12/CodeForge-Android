package com.codeforge.android

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RuntimeInstaller {
    const val RUNTIME_URL =
        "https://github.com/ArshadNayeem12/CodeForge-Android/releases/download/v1.0.0/codeforge-runtime-arm64.tar.gz"

    private const val RUNTIME_DIR = "codeforge-runtime"
    private const val ARCHIVE_NAME = "codeforge-runtime-arm64.tar.gz"

    fun install(context: Context, progress: (String) -> Unit): File {
        val base = File(context.filesDir, RUNTIME_DIR)
        val rootfs = File(base, "rootfs")
        val marker = File(base, "installed.ok")
        val archive = File(base, ARCHIVE_NAME)

        val node = File(
            rootfs,
            "root/code-server-4.141.0-linux-arm64/lib/node"
        )

        if (marker.exists() && node.exists()) {
            return rootfs
        }

        base.mkdirs()

        if (!archive.exists() || archive.length() == 0L) {
            progress("Downloading CodeForge runtime...")
            download(archive, progress)
        } else {
            progress("Using downloaded runtime archive...")
        }

        progress("Extracting runtime. Keep CodeForge open...")
        deleteRecursively(rootfs)
        rootfs.mkdirs()

        try {
            extractArchive(archive, rootfs, progress)

            if (!node.exists()) {
                throw IllegalStateException(
                    "Runtime extracted, but the expected code-server Node runtime is missing."
                )
            }

            marker.writeText("runtime-v1")
            archive.delete()
            progress("Runtime installed.")
            return rootfs
        } catch (e: Exception) {
            deleteRecursively(rootfs)
            marker.delete()
            throw e
        }
    }

    private fun download(
        destination: File,
        progress: (String) -> Unit
    ) {
        val connection = (URL(RUNTIME_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CodeForge-Android")
        }

        try {
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Runtime download failed: HTTP $status")
            }

            val total = connection.contentLengthLong
            var received = 0L
            val buffer = ByteArray(1024 * 256)

            connection.inputStream.buffered().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break

                        output.write(buffer, 0, count)
                        received += count

                        if (total > 0) {
                            val percent = (received * 100 / total).toInt()
                            progress(
                                "Downloading runtime: $percent% " +
                                    "(${received / 1_048_576} MB)"
                            )
                        } else {
                            progress(
                                "Downloading runtime: ${received / 1_048_576} MB"
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (destination.length() == 0L) {
                throw IllegalStateException("Downloaded runtime archive is empty.")
            }
        } catch (e: Exception) {
            destination.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun extractArchive(
        archive: File,
        destination: File,
        progress: (String) -> Unit
    ) {
        val root: Path = destination.toPath().toAbsolutePath().normalize()
        val deferredDirectoryModes = mutableListOf<Pair<File, Int>>()
        val buffer = ByteArray(128 * 1024)
        var entries = 0

        FileInputStream(archive).use { fileInput ->
            BufferedInputStream(fileInput).use { buffered ->
                GzipCompressorInputStream(buffered).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            val relative = Paths.get(entry.name).normalize()

                            if (relative.isAbsolute || relative.startsWith("..")) {
                                throw IllegalStateException(
                                    "Unsafe path in runtime archive: ${entry.name}"
                                )
                            }

                            val outputPath = root.resolve(relative).normalize()
                            if (!outputPath.startsWith(root)) {
                                throw IllegalStateException(
                                    "Invalid runtime archive path: ${entry.name}"
                                )
                            }

                            val output = outputPath.toFile()

                            when {
                                entry.isDirectory -> {
                                    output.mkdirs()
                                    deferredDirectoryModes.add(output to (entry.mode and 0x1FF))
                                }

                                entry.isSymbolicLink -> {
                                    output.parentFile?.mkdirs()
                                    if (Files.exists(outputPath)) {
                                        Files.delete(outputPath)
                                    }
                                    Files.createSymbolicLink(
                                        outputPath,
                                        Paths.get(entry.linkName)
                                    )
                                }

                                entry.isLink -> {
                                    output.parentFile?.mkdirs()
                                    val targetRelative = Paths.get(entry.linkName).normalize()
                                    if (targetRelative.isAbsolute ||
                                        targetRelative.startsWith("..")) {
                                        throw IllegalStateException(
                                            "Unsafe hard link in runtime archive."
                                        )
                                    }

                                    val target = root.resolve(targetRelative).normalize()
                                    if (!target.startsWith(root) || !Files.exists(target)) {
                                        throw IllegalStateException(
                                            "Missing hard-link target: ${entry.linkName}"
                                        )
                                    }

                                    Files.createLink(outputPath, target)
                                }

                                entry.isFile -> {
                                    output.parentFile?.mkdirs()

                                    BufferedOutputStream(FileOutputStream(output)).use { out ->
                                        while (true) {
                                            val count = tar.read(buffer)
                                            if (count < 0) break
                                            out.write(buffer, 0, count)
                                        }
                                        out.flush()
                                    }

                                    try {
                                        Os.chmod(output.absolutePath, entry.mode and 0x1FF)
                                    } catch (_: Exception) {
                                        output.setExecutable(
                                            (entry.mode and 0x49) != 0,
                                            false
                                        )
                                    }
                                }
                            }

                            entries++
                            if (entries % 5000 == 0) {
                                progress("Extracting runtime... $entries entries")
                            }
                        }
                    }
                }
            }
        }

        deferredDirectoryModes.asReversed().forEach { (directory, mode) ->
            try {
                Os.chmod(directory.absolutePath, mode)
            } catch (_: Exception) {
                // Keep Android's default directory permissions if chmod fails.
            }
        }
    }

    fun deleteRecursively(file: File) {
        if (!file.exists()) return

        if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }

        try {
            Files.deleteIfExists(file.toPath())
        } catch (_: Exception) {
            file.delete()
        }
    }
}
