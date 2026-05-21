package com.wuxianggujun.tinaide.editor.language.make

/**
 * 解析 `make -pn`（GNU make 数据库输出）中的变量与目标。
 *
 * 该实现参考了 Python 项目 `py-makefile-dbparse` 的分段策略：
 * - 变量：位于 `# Variables` 段内，直到 `# variable`（小写）标记之前
 * - 规则/目标：位于 `# Files` 与 `# VPATH Search Paths` 之间的 block（以空行分隔）
 */
object MakeDatabaseParser {

    data class TargetInfo(
        val prerequisites: List<String>,
    )

    data class Parsed(
        val variables: Map<String, String>,
        val targets: Map<String, TargetInfo>,
    )

    class StreamingExtractor(
        private val variableOrigins: Set<String> = setOf("environment", "makefile"),
    ) {
        private var inVariables = false
        private var inRules = false
        private var pendingOrigin: String? = null

        private val _variables = LinkedHashMap<String, String>()
        private val _targets = LinkedHashMap<String, TargetInfo>()

        fun acceptLine(lineRaw: String) {
            val line = lineRaw.trimEnd('\r', '\n')
            val trimmed = line.trim()

            if (line.startsWith("# Variables")) {
                inVariables = true
                pendingOrigin = null
            } else if (inVariables && line.trimStart().startsWith("# variable")) {
                inVariables = false
                pendingOrigin = null
            }

            if (line.startsWith("# Files")) {
                inRules = true
            } else if (inRules && line.startsWith("# VPATH Search Paths")) {
                inRules = false
            }

            if (inVariables) {
                if (trimmed.isEmpty()) return
                if (trimmed.startsWith("#")) {
                    val parts = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                    pendingOrigin = parts.getOrNull(1)
                    return
                }
                val origin = pendingOrigin ?: return
                pendingOrigin = null
                if (variableOrigins.isNotEmpty() && origin !in variableOrigins) return

                val parsed = parseMakeDbVariableLine(trimmed) ?: return
                val name = parsed.first
                val value = parsed.second
                if (!isValidVarName(name)) return
                _variables[name] = value
                return
            }

            if (inRules) {
                if (trimmed.isEmpty()) return
                if (trimmed.startsWith("#")) return
                if (line.startsWith("\t")) return

                val parsed = parseMakeDbRuleHeader(trimmed) ?: return
                for (t in parsed.first) {
                    if (!isValidTargetName(t)) continue
                    if (t == ".PHONY") continue
                    _targets.putIfAbsent(t, TargetInfo(prerequisites = parsed.second))
                }
            }
        }

        fun build(): Parsed {
            return Parsed(variables = _variables, targets = _targets)
        }
    }

    fun parse(dbText: String, variableOrigins: Set<String> = setOf("environment", "makefile")): Parsed {
        val normalized = normalizeNewlines(dbText)
        val variables = parseVariables(normalized, variableOrigins)
        val targets = parseTargets(normalized)
        return Parsed(variables = variables, targets = targets)
    }

    fun parseVariables(dbText: String, origins: Set<String>): Map<String, String> {
        val lines = normalizeNewlines(dbText).lineSequence().toList()
        val start = lines.indexOfFirst { it.startsWith("#") && it.contains("Variables") }
        if (start < 0) return emptyMap()

        val end = lines.indexOfFirst { it.startsWith("#") && it.trimStart().startsWith("# variable") }
        if (end < 0 || end <= start) return emptyMap()

        var currentOrigin: String? = null
        val out = LinkedHashMap<String, String>()

        for (i in (start + 1) until end) {
            val raw = lines[i]
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                val parts = line.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                currentOrigin = parts.getOrNull(1)
                continue
            }

            val origin = currentOrigin ?: continue
            currentOrigin = null
            if (origins.isNotEmpty() && origin !in origins) continue

            // Example: VAR = value / VAR := value / VAR += value / VAR ?= value
            val parsed = parseMakeDbVariableLine(line) ?: continue
            val name = parsed.first
            val value = parsed.second
            if (!isValidVarName(name)) continue
            out[name] = value
        }

        return out
    }

    fun parseTargets(dbText: String): Map<String, TargetInfo> {
        val normalized = normalizeNewlines(dbText)
        val blocks = normalized.split("\n\n")
        if (blocks.isEmpty()) return emptyMap()

        val start = blocks.indexOfFirst { it.startsWith("# Files") }
        if (start < 0) return emptyMap()

        val end = blocks.indexOfFirst { it.trimEnd().endsWith("# VPATH Search Paths") }
        if (end < 0 || end <= start) return emptyMap()

        val out = LinkedHashMap<String, TargetInfo>()
        for (i in (start + 1) until end) {
            val block = blocks[i]
            if (block.startsWith("#")) continue

            val firstLine = block.lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isEmpty()) continue

            val parsed = parseMakeDbRuleHeader(firstLine) ?: continue
            for (t in parsed.first) {
                if (!isValidTargetName(t)) continue
                if (t == ".PHONY") continue
                out.putIfAbsent(t, TargetInfo(prerequisites = parsed.second))
            }
        }

        return out
    }

    private fun parseMakeDbVariableLine(line: String): Pair<String, String>? {
        val eqIndex = line.indexOf('=')
        if (eqIndex <= 0) return null

        val left = line.substring(0, eqIndex).trimEnd()
        val nameRaw = left.trimEnd(':', '?', '+', '!')
            .trim()
            .split(WHITESPACE_REGEX, limit = 2)
            .firstOrNull()
            .orEmpty()
        if (nameRaw.isEmpty()) return null

        val value = line.substring(eqIndex + 1).trimStart()
        return nameRaw to value
    }

    private fun parseMakeDbRuleHeader(line: String): Pair<List<String>, List<String>>? {
        val colonIndex = line.indexOf(':')
        if (colonIndex <= 0) return null

        val left = line.substring(0, colonIndex).trim()
        if (left.isEmpty()) return null
        val targets = left.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }

        val right = line.substring(colonIndex + 1)
        val prereqPart = right.substringBefore(';')
        val prereqs = prereqPart
            .split(WHITESPACE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "|" }
            .filter { !it.contains('$') }

        return targets to prereqs
    }

    private fun normalizeNewlines(text: String): String {
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun isValidVarName(name: String): Boolean {
        return VAR_NAME_REGEX.matches(name)
    }

    private fun isValidTargetName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith(".")) return false
        if (name.contains('$')) return false
        if (name.contains('%')) return false
        if (name.contains(' ')) return false
        return true
    }

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val VAR_NAME_REGEX = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
}
