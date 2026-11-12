package net.portswigger.mcp.providers

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class ProxyJarManagerTest {

    private val manager = ProxyJarManager(mockk(relaxed = true))

    private val embeddedJar = "embedded proxy jar".toByteArray()
    private val embeddedJarHash = "9a6bb6e8faa77a828c60ef328243a75e18cabfea772470659c3d863949ab9dc3"

    @Test
    fun `installs the embedded jar when nothing is installed yet`(@TempDir dir: Path) {
        val jar = dir.resolve("mcp-proxy/mcp-proxy-all.jar")

        manager.installProxyJar(jar) { embeddedJar }

        assertArrayEquals(embeddedJar, jar.readBytes())
        assertEquals(embeddedJarHash, jar.resolveSibling("mcp-proxy-all.jar.version").readText().trim())
    }

    @Test
    fun `replaces an installed jar left behind by an earlier extension version`(@TempDir dir: Path) {
        val jar = installed(dir, "jar from an older extension".toByteArray(), version = "0".repeat(64))

        manager.refreshInstalledProxyJar(jar) { embeddedJar }

        assertArrayEquals(embeddedJar, jar.readBytes())
        assertEquals(embeddedJarHash, jar.resolveSibling("mcp-proxy-all.jar.version").readText().trim())
    }

    @Test
    fun `leaves an up to date jar alone`(@TempDir dir: Path) {
        val jar = installed(dir, embeddedJar, version = embeddedJarHash)
        val installedAt = java.nio.file.Files.getLastModifiedTime(jar)

        manager.refreshInstalledProxyJar(jar) { embeddedJar }

        assertEquals(installedAt, java.nio.file.Files.getLastModifiedTime(jar))
    }

    @Test
    fun `installs nothing when no jar is installed`(@TempDir dir: Path) {
        val jar = dir.resolve("mcp-proxy/mcp-proxy-all.jar")

        assertNull(manager.refreshInstalledProxyJar(jar) { embeddedJar })
        assertFalse(jar.exists())
        assertFalse(jar.parent.exists())
    }

    @Test
    fun `resolves the jar path per operating system`(@TempDir home: Path) {
        assertEquals(
            home.resolve("AppData/Roaming/BurpSuite/mcp-proxy/mcp-proxy-all.jar"),
            manager.burpProxyJarPath(home.toString(), "windows 11")
        )
        assertEquals(
            home.resolve(".BurpSuite/mcp-proxy/mcp-proxy-all.jar"),
            manager.burpProxyJarPath(home.toString(), "mac os x")
        )
        assertEquals(
            home.resolve(".BurpSuite/mcp-proxy/mcp-proxy-all.jar"),
            manager.burpProxyJarPath(home.toString(), "linux")
        )
    }

    private fun installed(dir: Path, jarBytes: ByteArray, version: String): Path {
        val jar = dir.resolve("mcp-proxy/mcp-proxy-all.jar")
        jar.parent.createDirectories()
        jar.writeBytes(jarBytes)
        jar.resolveSibling("mcp-proxy-all.jar.version").writeText(version)

        return jar
    }
}
