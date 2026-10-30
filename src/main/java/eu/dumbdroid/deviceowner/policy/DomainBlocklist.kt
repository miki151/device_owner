package eu.dumbdroid.deviceowner.policy

import android.os.Process
import android.util.AtomicFile
import java.io.File

/** The matching resolver/init/SELinux update owns this directory. No separate PIN or DNS service. */
class DomainBlocklist {
    private val directory = File("/data/misc/dumberos")
    private val file = AtomicFile(File(directory, "blocked_domains"))
    val available: Boolean get() = Process.myUserHandle().identifier == 0 && directory.canWrite()

    fun read(): String = if (file.baseFile.exists()) {
        file.openRead().bufferedReader().use { it.readText() }
    } else ""

    fun save(input: String) {
        check(available) { "The DumberOS resolver update is required" }
        val domains = input.lineSequence().map(String::trim).filter(String::isNotEmpty).map {
            requireNotNull(InternetPolicyController.normalizeHostname(it.removeSuffix("."))) {
                "Enter domains only, one per line (for example reddit.com)"
            }
        }.distinct().sorted().toList()
        require(domains.size <= 256) { "Use at most 256 domains" }
        val normalized = domains.joinToString("\n")
        val output = file.startWrite()
        try {
            output.write(normalized.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
        check(read() == normalized) { "The blocklist could not be verified" }
    }
}
