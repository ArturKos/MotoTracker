package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses `splash_hero_vd.xml` on the JVM (no device/Robolectric needed) and
 * asserts the structural contract that AA3 depends on:
 *  - Correct viewport (1536 × 440)
 *  - All seven required named groups present
 *  - wheelRear / wheelFront pivots at exact spec coordinates
 */
class SplashHeroVectorDrawableTest {

    // Gradle unit tests run from the :app module directory
    private val vdFile = File("src/main/res/drawable/splash_hero_vd.xml")
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val doc by lazy {
        assertTrue("splash_hero_vd.xml not found at ${vdFile.absolutePath}", vdFile.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(vdFile)
    }

    private fun Element.androidAttr(name: String): String? =
        getAttributeNS(androidNs, name).takeIf { it.isNotEmpty() }
            ?: getAttribute("android:$name").takeIf { it.isNotEmpty() }

    /** Recursively collect all <group> elements in the document. */
    private fun collectAllGroups(): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(nl: NodeList) {
            for (i in 0 until nl.length) {
                val node = nl.item(i)
                if (node is Element) {
                    if (node.localName == "group" || node.tagName == "group") result += node
                    walk(node.childNodes)
                }
            }
        }
        walk(doc.documentElement.childNodes)
        return result
    }

    @Test
    fun `vector root has correct viewport dimensions`() {
        val root = doc.documentElement
        assertEquals("viewportWidth", "1536", root.androidAttr("viewportWidth"))
        assertEquals("viewportHeight", "440", root.androidAttr("viewportHeight"))
        assertEquals("width", "1536dp", root.androidAttr("width"))
        assertEquals("height", "440dp", root.androidAttr("height"))
    }

    @Test
    fun `all seven required named groups are present`() {
        val required = setOf("mountains", "trail", "bike", "wheelRear", "wheelFront", "pin", "wordmark")
        val namedGroups = collectAllGroups()
            .mapNotNull { it.androidAttr("name") }
            .toSet()
        val missing = required - namedGroups
        assertTrue("Missing groups: $missing", missing.isEmpty())
    }

    @Test
    fun `wheelRear group has correct pivot coordinates`() {
        val wheelRear = collectAllGroups()
            .firstOrNull { it.androidAttr("name") == "wheelRear" }
        assertNotNull("wheelRear group not found", wheelRear)
        assertEquals("wheelRear pivotX", "307", wheelRear!!.androidAttr("pivotX"))
        assertEquals("wheelRear pivotY", "261", wheelRear.androidAttr("pivotY"))
    }

    @Test
    fun `wheelFront group has correct pivot coordinates`() {
        val wheelFront = collectAllGroups()
            .firstOrNull { it.androidAttr("name") == "wheelFront" }
        assertNotNull("wheelFront group not found", wheelFront)
        assertEquals("wheelFront pivotX", "567", wheelFront!!.androidAttr("pivotX"))
        assertEquals("wheelFront pivotY", "262", wheelFront.androidAttr("pivotY"))
    }

    @Test
    fun `wheelRear and wheelFront start with zero rotation`() {
        val groups = collectAllGroups()
        val wr = groups.firstOrNull { it.androidAttr("name") == "wheelRear" }
        val wf = groups.firstOrNull { it.androidAttr("name") == "wheelFront" }
        assertNotNull("wheelRear not found", wr)
        assertNotNull("wheelFront not found", wf)
        assertEquals("wheelRear rotation", "0", wr!!.androidAttr("rotation"))
        assertEquals("wheelFront rotation", "0", wf!!.androidAttr("rotation"))
    }
}
