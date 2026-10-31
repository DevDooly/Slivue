package com.devdooly.notificationedge.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** API 26 이상에서 같은 디렉터리의 동기화된 임시 파일을 원자 교체한다. */
internal class AtomicConsentStore(
    private val file: File,
    private val replaceAtomically: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Unit
    }
) {
    private val pending = File(file.parentFile, file.name + ".new")

    fun openRead(): FileInputStream {
        // 쓰기 도중 프로세스가 끝났다면 미완료 파일을 동의로 승격하지 않는다.
        // 다음 명시적 동의/철회 저장이 성공할 때까지 읽기는 실패로 닫힌다.
        if (pending.exists()) throw IOException("미완료 알림 동의 저장이 있습니다.")
        return FileInputStream(file)
    }

    fun write(properties: Properties) {
        val parent = requireNotNull(file.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("동의 저장 디렉터리를 만들지 못했습니다.")
        var committed = false
        try {
            FileOutputStream(pending).use { output ->
                properties.store(output, null)
                output.flush()
                output.fd.sync()
            }
            // File.renameTo는 Windows에서 기존 대상 교체를 조용히 실패시킬 수 있다.
            // 원본 삭제 후 쓰기나 비원자 이동으로 폴백하지 않는다.
            replaceAtomically(pending.toPath(), file.toPath())
            committed = true
        } finally {
            if (!committed) Files.deleteIfExists(pending.toPath())
        }
    }

    fun delete() {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(pending.toPath())
    }
}
