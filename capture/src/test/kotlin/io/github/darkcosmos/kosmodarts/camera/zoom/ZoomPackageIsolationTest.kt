package io.github.darkcosmos.kosmodarts.camera.zoom

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DZ3: the zoom package stays portable, free of Android and of the rest of :capture. */
class ZoomPackageIsolationTest {
    private val directory = File("src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom")
    private val allowedPrefixes = listOf("kotlin.", "java.math.")
    private val forbiddenPrefixes = listOf("android.", "androidx.", "kotlinx.coroutines")

    private fun files() = directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun headerLines(file: File) = file.readLines().mapIndexed { index, line -> index + 1 to line.trim() }
        .filter { (_, line) -> line.startsWith("package ") || line.startsWith("import ") }

    @Test
    fun hasAtLeastThreeFiles() {
        assertTrue("files in ${directory.absolutePath}: ${files().size}", files().size >= 3)
    }

    @Test
    fun packageLineIsExact() {
        for (file in files()) {
            val packages = headerLines(file).filter { it.second.startsWith("package ") }
            assertEquals("${file.name} package lines", 1, packages.size)
            assertEquals(
                "${file.name}:${packages[0].first}",
                "package io.github.darkcosmos.kosmodarts.camera.zoom",
                packages[0].second,
            )
        }
    }

    @Test
    fun importsStayInAllowList() {
        for (file in files()) {
            for ((number, line) in headerLines(file).filter { it.second.startsWith("import ") }) {
                val name = line.removePrefix("import ")
                val where = "${file.name}:$number $line"
                assertTrue("$where is not allowed", allowedPrefixes.any { name.startsWith(it) })
                assertTrue("$where is forbidden", forbiddenPrefixes.none { name.startsWith(it) })
            }
        }
    }

    @Test
    fun noLineMentionsCapture() {
        for (file in files()) {
            for ((number, line) in headerLines(file)) {
                assertTrue("${file.name}:$number $line mentions capture", !line.contains("capture"))
            }
        }
    }
}
