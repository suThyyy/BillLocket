package com.billlocket.app.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class ResourceParityTest {

    @Test
    fun `default and English strings have 100 percent identical key sets`() {
        val rootDir = File(".").canonicalFile
        val defaultStringsFile = File(rootDir, "src/main/res/values/strings.xml")
            .takeIf { it.exists() }
            ?: File(rootDir, "app/src/main/res/values/strings.xml")

        val enStringsFile = File(rootDir, "src/main/res/values-en/strings.xml")
            .takeIf { it.exists() }
            ?: File(rootDir, "app/src/main/res/values-en/strings.xml")

        assertTrue("Default strings.xml must exist: ${defaultStringsFile.absolutePath}", defaultStringsFile.exists())
        assertTrue("English strings.xml must exist: ${enStringsFile.absolutePath}", enStringsFile.exists())

        val defaultKeys = extractStringKeys(defaultStringsFile)
        val enKeys = extractStringKeys(enStringsFile)

        val missingInEn = defaultKeys - enKeys
        val missingInDefault = enKeys - defaultKeys

        assertEquals(
            "English resources missing keys present in default: $missingInEn",
            emptySet<String>(),
            missingInEn,
        )
        assertEquals(
            "Default resources missing keys present in English: $missingInDefault",
            emptySet<String>(),
            missingInDefault,
        )
        assertEquals(defaultKeys, enKeys)
    }

    private fun extractStringKeys(file: File): Set<String> {
        val content = file.readText()
        val pattern = Pattern.compile("""<string\s+name="([^"]+)"""")
        val matcher = pattern.matcher(content)
        val keys = mutableSetOf<String>()
        while (matcher.find()) {
            keys.add(matcher.group(1))
        }
        return keys
    }
}
