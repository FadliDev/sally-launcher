package com.sally.launcher.runtime

/**
 * Hasil perencanaan muat.
 *
 * - `order`: nama berkas, dependensi lebih dulu.
 * - `requires`: dependensi langsung tiap berkas yang ada di folder yang sama.
 * - `unresolved`: dependensi yang bukan library sistem dan tidak ada di folder (nama -> peminta).
 */
data class LoadPlan(
    val order: List<String>,
    val requires: Map<String, Set<String>>,
    val unresolved: Map<String, List<String>>,
) {
    /** Berkas yang langsung butuh dependensi yang tidak tersedia. */
    val blockedByUnresolved: Set<String> get() = unresolved.values.flatten().toSet()
}

class LoadCycleException(val chain: List<String>) : Exception(chain.joinToString(" -> "))

object LoadOrder {

    /**
     * Urutan muat dari DT_NEEDED. `libs` berisi nama berkas -> isi ELF-nya. Nama dependensi
     * dicocokkan dengan nama berkas atau DT_SONAME. Hasilnya deterministik.
     */
    @Throws(LoadCycleException::class)
    fun compute(libs: Map<String, ElfInfo>, isSystemLib: (String) -> Boolean): LoadPlan {
        val files = libs.keys.sorted()
        val alias = HashMap<String, String>()
        for (f in files) alias[f] = f
        for (f in files) libs.getValue(f).soname?.let { alias.putIfAbsent(it, f) }

        val state = HashMap<String, Int>() // 1 = sedang dikunjungi, 2 = selesai
        val order = ArrayList<String>()
        val requires = LinkedHashMap<String, Set<String>>()
        val unresolved = java.util.TreeMap<String, MutableList<String>>()

        fun visit(file: String, path: List<String>) {
            when (state[file]) {
                2 -> return
                1 -> throw LoadCycleException(path + file)
            }
            state[file] = 1
            val direct = LinkedHashSet<String>()
            for (dep in libs.getValue(file).needed) {
                val target = alias[dep]
                if (target != null) {
                    if (target != file) {
                        direct.add(target)
                        visit(target, path + file)
                    }
                } else if (!isSystemLib(dep)) {
                    unresolved.getOrPut(dep) { mutableListOf() }.add(file)
                }
            }
            requires[file] = direct
            state[file] = 2
            order.add(file)
        }

        for (f in files) visit(f, emptyList())
        return LoadPlan(order, requires, unresolved)
    }
}
