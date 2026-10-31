package com.devdooly.notificationedge.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Properties

class AtomicConsentStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `기존 동의 파일을 지우지 않고 새 상태로 원자 교체한다`() {
        val file = File(folder.root, "consent.properties")
        val store = AtomicConsentStore(file)
        store.write(properties("accepted"))
        store.write(properties("paused"))
        assertEquals("paused", store.openRead().use { Properties().apply { load(it) }.getProperty("state") })
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun `원자 이동이 지원되지 않거나 실패하면 기존 파일을 보존하고 실패를 전한다`() {
        val file = File(folder.root, "consent.properties")
        AtomicConsentStore(file).write(properties("before"))
        val failing = AtomicConsentStore(file) { _, _ -> throw IOException("원자 교체 미지원 테스트") }
        assertThrows(IOException::class.java) { failing.write(properties("after")) }
        assertEquals("before", AtomicConsentStore(file).openRead().use { Properties().apply { load(it) }.getProperty("state") })
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun `중단된 임시 파일이 있으면 이전 동의도 자동 재사용하지 않는다`() {
        val file = File(folder.root, "consent.properties")
        val store = AtomicConsentStore(file)
        store.write(properties("accepted"))
        File(file.path + ".new").writeText("state=unfinished")
        assertThrows(IOException::class.java) { store.openRead().close() }
        store.write(properties("fresh-consent"))
        assertEquals("fresh-consent", store.openRead().use { Properties().apply { load(it) }.getProperty("state") })
    }

    private fun properties(state: String) = Properties().apply { setProperty("state", state) }
}
