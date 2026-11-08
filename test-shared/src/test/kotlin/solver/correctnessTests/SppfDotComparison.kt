package solver.correctnessTests

/** A forest snapshot has traversal-dependent DOT IDs, but distinct nodes must stay distinct. */
internal fun sameSppfDot(expected: String, actual: String): Boolean {
    val left = parseSppfDot(expected)
    val right = parseSppfDot(actual)
    if (left.frame != right.frame || left.clusters.size != right.clusters.size) return false

    val unmatched = right.clusters.toMutableList()
    for (cluster in left.clusters) {
        val index = unmatched.indexOfFirst { sameCluster(cluster, it) }
        if (index < 0) return false
        unmatched.removeAt(index)
    }
    return true
}

private data class DotForest(val frame: List<String>, val clusters: List<DotCluster>)

private data class DotCluster(
    val attributes: List<String>,
    val nodes: Map<String, String>,
    val edges: Map<Pair<String, String>, Int>,
)

private val clusterPattern = Regex("""subgraph cluster_\d+\{""")
private val nodePattern = Regex("""^(_\d+_\d+) \[label = "\d+\s+(.+)", shape = (.+)]$""")
private val edgePattern = Regex("""^(_\d+_\d+)->(_\d+_\d+)$""")

private fun parseSppfDot(dot: String): DotForest {
    val frame = ArrayList<String>()
    val clusters = ArrayList<DotCluster>()
    var clusterLines: ArrayList<String>? = null
    for (raw in dot.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            clusterPattern.matches(line) -> {
                check(clusterLines == null) { "nested DOT cluster" }
                clusterLines = ArrayList()
            }
            line == "}" && clusterLines != null -> {
                clusters.add(parseCluster(clusterLines))
                clusterLines = null
            }
            clusterLines != null -> clusterLines.add(line)
            else -> frame.add(line)
        }
    }
    check(clusterLines == null) { "unclosed DOT cluster" }
    return DotForest(frame.sorted(), clusters)
}

private fun parseCluster(lines: List<String>): DotCluster {
    val nodes = HashMap<String, String>()
    val edges = ArrayList<Pair<String, String>>()
    val attributes = ArrayList<String>()
    for (line in lines) {
        val node = nodePattern.matchEntire(line)
        val edge = edgePattern.matchEntire(line)
        when {
            node != null -> {
                val (id, label, shape) = node.destructured
                check(nodes.put(id, "$label | $shape") == null) { "duplicate DOT node $id" }
            }
            edge != null -> edges.add(edge.groupValues[1] to edge.groupValues[2])
            line.startsWith("_") -> error("unrecognized DOT line: $line")
            else -> attributes.add(line)
        }
    }
    for ((from, to) in edges) {
        check(from in nodes && to in nodes) { "DOT edge references missing node: $from->$to" }
    }
    return DotCluster(attributes.sorted(), nodes, edges.groupingBy { it }.eachCount())
}

/** Exact labelled directed-graph isomorphism within one SPPF root. */
private fun sameCluster(left: DotCluster, right: DotCluster): Boolean {
    if (left.attributes != right.attributes || left.nodes.size != right.nodes.size ||
        left.edges.values.sum() != right.edges.values.sum() ||
        left.nodes.values.groupingBy { it }.eachCount() != right.nodes.values.groupingBy { it }.eachCount()
    ) return false

    fun degrees(cluster: DotCluster, id: String): Pair<Int, Int> =
        cluster.edges.entries.fold(0 to 0) { (inDegree, outDegree), entry ->
            val (edge, count) = entry
            (inDegree + if (edge.second == id) count else 0) to
                (outDegree + if (edge.first == id) count else 0)
        }

    val candidates = left.nodes.keys.associateWith { id ->
        right.nodes.keys.filter { candidate ->
            left.nodes[id] == right.nodes[candidate] && degrees(left, id) == degrees(right, candidate)
        }.sorted()
    }
    if (candidates.values.any { it.isEmpty() }) return false
    val order = left.nodes.keys.sortedWith(compareBy<String> { candidates.getValue(it).size }.thenBy { it })
    val mapping = HashMap<String, String>()
    val used = HashSet<String>()

    fun edgeCount(cluster: DotCluster, from: String, to: String) = cluster.edges[from to to] ?: 0

    fun match(position: Int): Boolean {
        if (position == order.size) return true
        val id = order[position]
        for (candidate in candidates.getValue(id)) {
            if (candidate in used || edgeCount(left, id, id) != edgeCount(right, candidate, candidate)) continue
            if (mapping.any { (old, mapped) ->
                    edgeCount(left, id, old) != edgeCount(right, candidate, mapped) ||
                        edgeCount(left, old, id) != edgeCount(right, mapped, candidate)
                }) continue
            mapping[id] = candidate
            used.add(candidate)
            if (match(position + 1)) return true
            mapping.remove(id)
            used.remove(candidate)
        }
        return false
    }
    return match(0)
}
