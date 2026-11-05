package cafe.miafetta.tsuki.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeAtomic(file: File, bytes: ByteArray) {
    val directory = requireNotNull(file.parentFile)
    require(directory.isDirectory || directory.mkdirs()) { "Cannot create storage" }
    val temp = Files.createTempFile(directory.toPath(), "tsuki-", ".tmp")
    try {
        FileOutputStream(temp.toFile()).use {
            it.write(bytes)
            it.fd.sync()
        }
        try {
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
