package io.github.darkcosmos.kosmodarts.capture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every translatable resource of values/ exists in values-fr/ (strings, plurals and arrays), and nothing else does. */
class StringResourcesTest {
    private val tag = Regex("<(string|plurals|string-array)\\s+([^>]*)>")
    private val nameOf = Regex("name=\"([^\"]+)\"")

    private fun keys(directory: String): Set<String> =
        tag.findAll(File("src/main/res/$directory/strings.xml").readText())
            .map { it.groupValues[2] }
            .filterNot { it.contains("translatable=\"false\"") }
            .map { checkNotNull(nameOf.find(it)).groupValues[1] }
            .toSet()

    @Test
    fun `U13 every translatable English resource has a French translation and vice versa`() {
        val english = keys("values")
        val french = keys("values-fr")
        assertTrue("English resources were not found", english.isNotEmpty())
        assertEquals("Missing in values-fr", emptySet<String>(), english - french)
        assertEquals("Only in values-fr", emptySet<String>(), french - english)
    }
}
