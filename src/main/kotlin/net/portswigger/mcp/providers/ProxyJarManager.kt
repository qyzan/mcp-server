package net.portswigger.mcp.providers

import burp.api.montoya.logging.Logging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText

class ProxyJarManager(private val logging: Logging) {

    companion object {
        private const val PROXY_JAR_NAME = "mcp-proxy-all.jar"
    }

    fun getProxyJar(): Path = installProxyJar(burpProxyJarPath())

    /**
     * Brings an already installed proxy jar up to date with the one embedded in this extension.
     *
     * Clients launch the proxy themselves, from an absolute path written into their own
     * configuration, so a jar left behind by an earlier version of this extension keeps being used
     * until the user happens to run an installation again. A proxy that predates the current server
     * can fail to complete the MCP handshake, and the error the client reports says nothing about
     * the jar being out of date.
     *
     * Does nothing when no jar is installed: extracting one in the first place stays the
     * installation flow's job.
     */
    fun refreshInstalledProxyJar(): Path? = refreshInstalledProxyJar(burpProxyJarPath())

    internal fun refreshInstalledProxyJar(
        proxyJarPath: Path,
        embeddedJarBytes: () -> ByteArray = ::embeddedProxyJarBytes,
    ): Path? {
        if (!proxyJarPath.exists()) return null

        return installProxyJar(proxyJarPath, embeddedJarBytes)
    }

    internal fun installProxyJar(
        proxyJarPath: Path,
        embeddedJarBytes: () -> ByteArray = ::embeddedProxyJarBytes,
    ): Path {
        val versionFilePath = proxyJarPath.resolveSibling("$PROXY_JAR_NAME.version")

        if (!proxyJarPath.parent.exists()) {
            try {
                Files.createDirectories(proxyJarPath.parent)
            } catch (e: IOException) {
                throw RuntimeException("Failed to create directory: ${proxyJarPath.parent}", e)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val resourceBytes = embeddedJarBytes()
        val resourceHash = digest.digest(resourceBytes).joinToString("") { "%02x".format(it) }

        val alreadyInstalled = proxyJarPath.exists()
        val needsExtraction =
            !alreadyInstalled || !versionFilePath.exists() || versionFilePath.readText().trim() != resourceHash

        if (needsExtraction) {
            try {
                val tempFile = Files.createTempFile(proxyJarPath.parent, "temp-", ".jar")
                Files.write(tempFile, resourceBytes)

                Files.move(tempFile, proxyJarPath, StandardCopyOption.REPLACE_EXISTING)

                Files.writeString(versionFilePath, resourceHash)

                if (!System.getProperty("os.name").lowercase().contains("win")) {
                    proxyJarPath.toFile().setExecutable(true)
                }

                if (alreadyInstalled) {
                    logging.logToOutput("Updated out of date proxy jar at: $proxyJarPath")
                } else {
                    logging.logToOutput("Extracted proxy jar to: $proxyJarPath")
                }
            } catch (e: IOException) {
                throw RuntimeException("Failed to extract proxy jar to: $proxyJarPath", e)
            }
        }

        return proxyJarPath
    }

    private fun embeddedProxyJarBytes(): ByteArray =
        (javaClass.classLoader.getResourceAsStream(PROXY_JAR_NAME)
            ?: throw RuntimeException("Could not find $PROXY_JAR_NAME in extension resources")).use { it.readAllBytes() }

    internal fun burpProxyJarPath(
        home: String = System.getProperty("user.home"),
        os: String = System.getProperty("os.name").lowercase(),
    ): Path {
        val basePath = when {
            os.contains("win") -> Path.of(home, "AppData", "Roaming", "BurpSuite", "mcp-proxy")
            os.contains("mac") || os.contains("darwin") -> Path.of(home, ".BurpSuite", "mcp-proxy")
            os.contains("linux") || os.contains("unix") -> Path.of(home, ".BurpSuite", "mcp-proxy")
            else -> throw RuntimeException("Unsupported OS: $os")
        }

        return basePath.resolve(PROXY_JAR_NAME)
    }
}
