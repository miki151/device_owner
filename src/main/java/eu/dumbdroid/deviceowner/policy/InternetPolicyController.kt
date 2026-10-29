package eu.dumbdroid.deviceowner.policy

/** Nullable values preserve Android's unset/default DNS configuration. */
data class PrivateDnsConfig(val mode: String?, val hostname: String?)
data class DnsSession(val previous: PrivateDnsConfig, val hostname: String)
data class DnsControlState(val hostname: String?, val applied: Boolean, val locked: Boolean)

enum class InternetPolicyResult { APPLIED, FAILED, INVALID_HOSTNAME, RESTRICTED_ELSEWHERE }

interface InternetPolicyBackend {
    var dnsSession: DnsSession?
    fun readDns(): PrivateDnsConfig
    fun writeDns(config: PrivateDnsConfig)
    fun baseRestriction(key: String): Boolean
    fun effectiveRestriction(key: String): Boolean
    fun setRestriction(key: String, enabled: Boolean)
}

/** Android persists controls; only DNS needs a saved original configuration for removal. */
open class InternetPolicyController(
    private val backend: InternetPolicyBackend,
    val isSupportedUser: Boolean = true,
    private val onFailure: (Exception) -> Unit = {},
) {
    fun dnsState(): DnsControlState? = access {
        val hostname = backend.dnsSession?.hostname
        val locked = backend.effectiveRestriction(DNS_RESTRICTION)
        DnsControlState(hostname, hostname != null && locked &&
            backend.readDns() == PrivateDnsConfig(STRICT_MODE, hostname), locked)
    }

    fun isTetheringBlocked(): Boolean? = access {
        backend.effectiveRestriction(TETHERING_RESTRICTION)
    }

    fun canRemovePin(): Boolean = !isSupportedUser || access {
        backend.dnsSession == null && !backend.baseRestriction(TETHERING_RESTRICTION)
    } == true

    /** Null disables filtering. Save before writes; retain the original through edits and failures. */
    fun setDns(input: String?): InternetPolicyResult {
        val hostname = input?.let { normalizeHostname(it) ?: return InternetPolicyResult.INVALID_HOSTNAME }
        return access {
            val session = backend.dnsSession
            if (hostname != null) {
                if (session == null && backend.effectiveRestriction(DNS_RESTRICTION)) {
                    return@access InternetPolicyResult.RESTRICTED_ELSEWHERE
                }
                backend.dnsSession = DnsSession(session?.previous ?: backend.readDns(), hostname)
                setRestriction(DNS_RESTRICTION, true)
                writeDns(PrivateDnsConfig(STRICT_MODE, hostname))
            } else if (session != null) {
                writeDns(session.previous)
                setRestriction(DNS_RESTRICTION, false)
                backend.dnsSession = null
            }
            InternetPolicyResult.APPLIED
        } ?: InternetPolicyResult.FAILED
    }

    fun setTetheringBlocked(blocked: Boolean): InternetPolicyResult = access {
        setRestriction(TETHERING_RESTRICTION, blocked)
        InternetPolicyResult.APPLIED
    } ?: InternetPolicyResult.FAILED

    private fun setRestriction(key: String, enabled: Boolean) {
        backend.setRestriction(key, enabled)
        check(backend.baseRestriction(key) == enabled)
    }

    private fun writeDns(config: PrivateDnsConfig) {
        backend.writeDns(config)
        check(backend.readDns() == config)
    }

    private fun <T> access(operation: () -> T): T? = try {
        check(isSupportedUser)
        operation()
    } catch (e: Exception) {
        onFailure(e)
        null
    }

    companion object {
        const val DEFAULT_HOSTNAME = "family.adguard-dns.com"
        const val STRICT_MODE = "hostname"
        const val DNS_RESTRICTION = "disallow_config_private_dns"
        const val TETHERING_RESTRICTION = "no_config_tethering"

        /** Accept a DNS hostname, never a URL, IP or port. */
        fun normalizeHostname(input: String): String? {
            val hostname = input.trim().lowercase(java.util.Locale.ROOT)
            if (hostname.length !in 3..253 || !hostname.contains('.') ||
                hostname.all { it.isDigit() || it == '.' }) return null
            return hostname.takeIf {
                it.split('.').all { label ->
                    label.length in 1..63 && label.first().isLetterOrDigit() &&
                        label.last().isLetterOrDigit() &&
                        label.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
                }
            }
        }
    }
}
