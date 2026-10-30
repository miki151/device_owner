package eu.dumbdroid.deviceowner.policy

private class FakeBackend : InternetPolicyBackend {
    var config = PrivateDnsConfig(null, null)
    val base = mutableMapOf<String, Boolean>()
    val external = mutableSetOf<String>()
    var failSave = false
    var failClear = false
    var failRead = false
    var failWrite = false
    var ignoreWrite = false
    var failRestriction = false
    var ignoreRestriction = false
    var writes = 0
    override var dnsSession: DnsSession? = null
        set(value) {
            check(!failSave && !(value == null && failClear))
            field = value
        }
    override fun readDns(): PrivateDnsConfig {
        check(!failRead)
        return config
    }
    override fun writeDns(config: PrivateDnsConfig) {
        writes++
        if (ignoreWrite) return
        // Model a partial write (hostname saved, mode write failed).
        this.config = this.config.copy(hostname = config.hostname)
        check(!failWrite)
        this.config = config
    }
    override fun baseRestriction(key: String): Boolean {
        check(!failRead)
        return base[key] ?: false
    }
    override fun effectiveRestriction(key: String) = baseRestriction(key) || key in external
    override fun setRestriction(key: String, enabled: Boolean) {
        check(!failRestriction)
        if (!ignoreRestriction) base[key] = enabled
    }
}

private fun expect(actual: Any?, expected: Any?) = check(actual == expected) {
    "Expected <$expected>, got <$actual>"
}

fun main() {
    var passed = 0
    fun test(name: String, body: (FakeBackend, InternetPolicyController) -> Unit) {
        val backend = FakeBackend()
        body(backend, InternetPolicyController(backend))
        println("PASS $name")
        passed++
    }
    val dnsKey = InternetPolicyController.DNS_RESTRICTION
    val tetherKey = InternetPolicyController.TETHERING_RESTRICTION
    val host = InternetPolicyController.DEFAULT_HOSTNAME

    test("enable strict DNS and lock; restore unset original") { b, c ->
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
        expect(c.dnsState()?.applied, true)
        expect(b.config, PrivateDnsConfig("hostname", host))
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.config, PrivateDnsConfig(null, null))
        expect(b.baseRestriction(dnsKey), false)
        expect(b.dnsSession, null)
    }
    test("resolver edits preserve original and survive controller recreation") { b, c ->
        val original = PrivateDnsConfig("off", "old.example.com")
        b.config = original
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
        expect(c.setDns("  DNS.EXAMPLE.COM  "), InternetPolicyResult.APPLIED)
        expect(b.dnsSession?.previous, original)
        expect(b.dnsSession?.hostname, "dns.example.com")
        val afterRestart = InternetPolicyController(b)
        expect(afterRestart.dnsState()?.applied, true)
        expect(afterRestart.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.config, original)
    }
    test("invalid DNS inputs do not write state") { b, c ->
        listOf("tls://dns.example.com", "https://example.com", "dns.example.com:853", "1.1.1.1",
            "localhost", "-dns.example.com", "dns..example.com", "dns.example.com/", "").forEach {
            expect(c.setDns(it), InternetPolicyResult.INVALID_HOSTNAME)
        }
        expect(b.writes, 0)
        expect(b.dnsSession, null)
    }
    test("do not overwrite an external DNS policy or existing system DNS lock") { b, c ->
        b.external.add(dnsKey)
        expect(c.setDns(host), InternetPolicyResult.RESTRICTED_ELSEWHERE)
        b.external.clear()
        b.base[dnsKey] = true
        expect(c.setDns(host), InternetPolicyResult.RESTRICTED_ELSEWHERE)
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.baseRestriction(dnsKey), true)
        expect(b.writes, 0)
        expect(b.dnsSession, null)
    }
    test("snapshot save failure prevents system mutation") { b, c ->
        b.failSave = true
        expect(c.setDns(host), InternetPolicyResult.FAILED)
        expect(b.writes, 0)
        expect(b.baseRestriction(dnsKey), false)
    }
    test("partial DNS write keeps restoration snapshot and can disable") { b, c ->
        val original = b.config
        b.failWrite = true
        expect(c.setDns(host), InternetPolicyResult.FAILED)
        expect(b.dnsSession?.previous, original)
        expect(c.dnsState()?.applied, false)
        b.failWrite = false
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.config, original)
    }
    test("restriction write failure retains recovery state") { b, c ->
        b.failRestriction = true
        expect(c.setDns(host), InternetPolicyResult.FAILED)
        check(b.dnsSession != null)
        expect(b.writes, 0)
        b.failRestriction = false
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
    }
    test("readback detects ignored restriction") { b, c ->
        b.ignoreRestriction = true
        expect(c.setDns(host), InternetPolicyResult.FAILED)
        check(b.dnsSession != null)
        expect(c.dnsState()?.applied, false)
        expect(b.writes, 0)
    }
    test("readback detects ignored DNS write") { b, c ->
        b.ignoreWrite = true
        expect(c.setDns(host), InternetPolicyResult.FAILED)
        expect(c.dnsState()?.applied, false)
        expect(b.dnsSession?.previous, PrivateDnsConfig(null, null))
    }
    test("failed restore keeps original snapshot for retry") { b, c ->
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
        b.failWrite = true
        expect(c.setDns(null), InternetPolicyResult.FAILED)
        check(b.dnsSession != null)
        expect(b.baseRestriction(dnsKey), true)
        b.failWrite = false
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.dnsSession, null)
    }
    test("failed session clear is retryable") { b, c ->
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
        b.failClear = true
        expect(c.setDns(null), InternetPolicyResult.FAILED)
        b.failClear = false
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(b.config, PrivateDnsConfig(null, null))
    }
    test("external DNS restriction remains after own removal") { b, c ->
        expect(c.setDns(host), InternetPolicyResult.APPLIED)
        b.external.add(dnsKey)
        expect(c.setDns(null), InternetPolicyResult.APPLIED)
        expect(c.dnsState()?.locked, true)
        expect(c.dnsState()?.hostname, null)
    }
    test("tethering uses platform state across controller recreation and is independent of DNS") { b, c ->
        expect(c.setTetheringBlocked(true), InternetPolicyResult.APPLIED)
        val afterRestart = InternetPolicyController(b)
        expect(afterRestart.isTetheringBlocked(), true)
        expect(b.config, PrivateDnsConfig(null, null))
        expect(afterRestart.setTetheringBlocked(false), InternetPolicyResult.APPLIED)
        expect(c.isTetheringBlocked(), false)
    }
    test("external tethering restriction remains after own removal") { b, c ->
        b.external.add(tetherKey)
        expect(c.setTetheringBlocked(true), InternetPolicyResult.APPLIED)
        expect(c.setTetheringBlocked(false), InternetPolicyResult.APPLIED)
        expect(c.isTetheringBlocked(), true)
    }
    test("tethering failure reports actual state and can retry") { b, c ->
        b.ignoreRestriction = true
        expect(c.setTetheringBlocked(true), InternetPolicyResult.FAILED)
        expect(c.isTetheringBlocked(), false)
        b.ignoreRestriction = false
        expect(c.setTetheringBlocked(true), InternetPolicyResult.APPLIED)
        b.failRestriction = true
        expect(c.setTetheringBlocked(false), InternetPolicyResult.FAILED)
        expect(c.isTetheringBlocked(), true)
        b.failRestriction = false
        expect(c.setTetheringBlocked(false), InternetPolicyResult.APPLIED)
    }
    test("read failures show unknown") { b, c ->
        b.failRead = true
        expect(c.dnsState(), null)
        expect(c.isTetheringBlocked(), null)
    }
    test("secondary user cannot configure global DNS or internet controls") { b, _ ->
        val secondary = InternetPolicyController(b, isSupportedUser = false)
        expect(secondary.setDns(host), InternetPolicyResult.FAILED)
        expect(secondary.setTetheringBlocked(true), InternetPolicyResult.FAILED)
        expect(secondary.dnsState(), null)
        expect(b.writes, 0)
        expect(b.base.isEmpty(), true)
    }
    println("$passed internet policy tests passed")
}
