package eu.dumbdroid.deviceowner.policy

import android.os.Process
import android.util.AtomicFile
import java.io.File

/** The matching resolver/init/SELinux update owns this directory. No separate PIN or DNS service. */
class DomainBlocklist {
    private val directory = File("/data/misc/dumberos")
    private val file = AtomicFile(File(directory, "blocked_domains"))
    val available: Boolean get() = Process.myUserHandle().identifier == 0 && directory.canWrite()

    fun read(): String =
        if (file.baseFile.exists()) file.readFully().toString(Charsets.UTF_8) else ""

    fun save(input: String) {
        check(available) { "The DumberOS resolver update is required" }
        val domains = input.lineSequence().map(String::trim).filter(String::isNotEmpty).map {
            requireNotNull(InternetPolicyController.normalizeHostname(it.removeSuffix("."))) {
                "Enter domains only, one per line (for example reddit.com)"
            }
        }.toSortedSet()
        require(domains.size <= 256) { "Use at most 256 domains" }
        val normalized = domains.joinToString("\n")
        file.write { it.write(normalized.toByteArray(Charsets.UTF_8)) }
        check(read() == normalized) { "The blocklist could not be verified" }
    }
}
