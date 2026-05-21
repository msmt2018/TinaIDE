package com.wuxianggujun.tinaide.editor.navigation

import android.content.Context
import com.wuxianggujun.tinaide.core.config.Prefs
import org.json.JSONArray
import java.io.File
import timber.log.Timber

/**
 * 头文件路径解析器
 *
 * 解析顺序：
 * 1. 相对于当前文件目录（仅 "header.h" 形式）
 * 2. 相对于项目根目录
 * 3. 项目内的 include 目录
 * 4. compile_commands.json 中的 include 路径（如果存在）
 * 5. 系统头文件目录（rootfs 中的 /usr/include 等）
 * 6. 递归搜索项目目录（作为最后手段）
 */
class HeaderPathResolver(
    private val context: Context,
    private val currentFile: File,
    private val projectRoot: String
) {
    
    companion object {
        private const val TAG = "HeaderPathResolver"
        
        // 常见的 include 目录名
        private val COMMON_INCLUDE_DIRS = listOf(
            "include",
            "inc",
            "src",
            "headers",
            "Include",
            "Inc",
            "source",
            "Sources"
        )
        
        // compile_commands.json 可能的位置
        private val COMPILE_COMMANDS_PATHS = listOf(
            "build/compile_commands.json",
            "build/debug/compile_commands.json",
            "build/release/compile_commands.json",
            "cmake-build-debug/compile_commands.json",
            "cmake-build-release/compile_commands.json",
            "compile_commands.json",
            "out/compile_commands.json"
        )
        
        // 系统头文件目录（相对于 rootfs）
        private val SYSTEM_INCLUDE_DIRS = listOf(
            "usr/include/c++/v1",      // libc++ 头文件（C++ 标准库）
            "usr/include",              // 系统头文件
            "usr/local/include"         // 本地安装的头文件
        )
        
        // 搜索限制
        private const val MAX_SEARCH_COUNT = 1000
        private const val MAX_SEARCH_DEPTH = 5
    }
    
    // 缓存 compile_commands.json 中的 include 路径
    private var cachedIncludePaths: List<String>? = null
    
    /**
     * 解析头文件路径
     * 
     * @param includeInfo #include 信息
     * @return 头文件的 File 对象，如果找不到返回 null
     */
    fun resolve(includeInfo: IncludeInfo): File? {
        val headerPath = includeInfo.path
        
        Timber.tag(TAG).d("Resolving header: $headerPath (system: ${includeInfo.isSystemHeader})")
        
        // 1. 相对于当前文件目录（仅用户头文件）
        if (!includeInfo.isSystemHeader) {
            val relativeToFile = File(currentFile.parentFile, headerPath)
            if (relativeToFile.exists()) {
                Timber.tag(TAG).d("Found relative to current file: ${relativeToFile.absolutePath}")
                return relativeToFile.canonicalFile
            }
        }
        
        // 2. 相对于项目根目录
        val relativeToProject = File(projectRoot, headerPath)
        if (relativeToProject.exists()) {
            Timber.tag(TAG).d("Found relative to project root: ${relativeToProject.absolutePath}")
            return relativeToProject.canonicalFile
        }
        
        // 3. 在项目的 include 目录中查找
        for (includeDir in COMMON_INCLUDE_DIRS) {
            val inIncludeDir = File(File(projectRoot, includeDir), headerPath)
            if (inIncludeDir.exists()) {
                Timber.tag(TAG).d("Found in include dir ($includeDir): ${inIncludeDir.absolutePath}")
                return inIncludeDir.canonicalFile
            }
        }
        
        // 4. 从 compile_commands.json 获取 include 路径
        val compileCommandsIncludes = getCompileCommandsIncludes()
        for (includePath in compileCommandsIncludes) {
            val inCompileInclude = File(includePath, headerPath)
            if (inCompileInclude.exists()) {
                Timber.tag(TAG).d("Found via compile_commands.json: ${inCompileInclude.absolutePath}")
                return inCompileInclude.canonicalFile
            }
        }
        
        // 5. 在系统头文件目录中查找（rootfs）
        val systemHeader = findInSystemIncludes(headerPath)
        if (systemHeader != null) {
            Timber.tag(TAG).d("Found in system includes: ${systemHeader.absolutePath}")
            return systemHeader
        }
        
        // 6. 递归搜索项目目录（作为最后手段，可能较慢）
        val searchResult = searchInProject(headerPath)
        if (searchResult != null) {
            Timber.tag(TAG).d("Found via recursive search: ${searchResult.absolutePath}")
            return searchResult
        }
        
        Timber.tag(TAG).w("Header not found: $headerPath")
        return null
    }
    
    /**
     * 获取 compile_commands.json 中的 include 路径（带缓存）
     */
    private fun getCompileCommandsIncludes(): List<String> {
        cachedIncludePaths?.let { return it }
        
        val paths = parseCompileCommandsIncludes()
        cachedIncludePaths = paths
        return paths
    }
    
    /**
     * 解析 compile_commands.json 中的 include 路径
     */
    private fun parseCompileCommandsIncludes(): List<String> {
        val compileCommandsFile = findCompileCommandsJson() ?: return emptyList()
        
        return try {
            val json = compileCommandsFile.readText()
            extractIncludePaths(json)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse compile_commands.json")
            emptyList()
        }
    }
    
    /**
     * 查找 compile_commands.json
     */
    private fun findCompileCommandsJson(): File? {
        for (relativePath in COMPILE_COMMANDS_PATHS) {
            val file = File(projectRoot, relativePath)
            if (file.exists()) {
                Timber.tag(TAG).d("Found compile_commands.json at: ${file.absolutePath}")
                return file
            }
        }
        return null
    }
    
    /**
     * 从 compile_commands.json 内容中提取 include 路径
     */
    private fun extractIncludePaths(json: String): List<String> {
        val paths = mutableSetOf<String>()
        
        try {
            val jsonArray = JSONArray(json)
            val entry = selectCompileEntry(jsonArray)
            if (entry != null) {
                val baseDir = entry.optString("directory")
                    .takeIf { it.isNotBlank() }
                    ?.let { File(it) }
                    ?.takeIf { it.isDirectory }
                    ?: File(projectRoot)

                // 尝试从 "command" 字段提取
                if (entry.has("command")) {
                    val command = entry.getString("command")
                    extractIncludePathsFromCommand(command, baseDir, paths)
                }

                // 尝试从 "arguments" 字段提取
                if (entry.has("arguments")) {
                    val arguments = entry.getJSONArray("arguments")
                    var j = 0
                    while (j < arguments.length()) {
                        val arg = arguments.optString(j)
                        when (arg) {
                            "-I", "-isystem" -> {
                                val next = arguments.optString(j + 1)
                                if (next.isNotEmpty()) {
                                    paths.add(resolvePath(baseDir, next))
                                }
                                j += 2
                            }

                            else -> {
                                extractIncludePathFromArg(arg, baseDir, paths)
                                j++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse compile_commands.json as JSON array")
            // 回退到正则表达式解析
            extractIncludePathsViaRegex(json, paths)
        }
        
        Timber.tag(TAG).d("Extracted ${paths.size} include paths from compile_commands.json")
        return paths.toList()
    }
    
    /**
     * 从编译命令字符串中提取 include 路径
     */
    private fun extractIncludePathsFromCommand(command: String, baseDir: File, paths: MutableSet<String>) {
        // 匹配 -I/path/to/include 或 -I /path/to/include 或 -I"/path/to/include"
        val includeRegex = Regex("""-I\s*"?([^"\s]+)"?""")
        includeRegex.findAll(command).forEach { match ->
            val path = match.groupValues[1]
            if (path.isNotEmpty()) {
                paths.add(resolvePath(baseDir, path))
            }
        }

        // 匹配 -isystem/path/to/include 或 -isystem /path/to/include
        val systemRegex = Regex("""-isystem\s*"?([^"\s]+)"?""")
        systemRegex.findAll(command).forEach { match ->
            val path = match.groupValues[1]
            if (path.isNotEmpty()) {
                paths.add(resolvePath(baseDir, path))
            }
        }
    }
    
    /**
     * 从单个参数中提取 include 路径
     */
    private fun extractIncludePathFromArg(arg: String, baseDir: File, paths: MutableSet<String>) {
        when {
            arg.startsWith("-I") && arg.length > 2 -> {
                val path = arg.substring(2)
                if (path.isNotEmpty()) {
                    paths.add(resolvePath(baseDir, path))
                }
            }

            arg.startsWith("-isystem") && arg.length > 8 -> {
                val path = arg.substring(8)
                if (path.isNotEmpty()) {
                    paths.add(resolvePath(baseDir, path))
                }
            }
        }
    }
    
    /**
     * 使用正则表达式从 JSON 文本中提取 include 路径（回退方案）
     */
    private fun extractIncludePathsViaRegex(json: String, paths: MutableSet<String>) {
        val includeRegex = Regex("""-I\s*"?([^"\s,\]]+)"?""")
        includeRegex.findAll(json).forEach { match ->
            val path = match.groupValues[1]
            if (path.isNotEmpty() && !path.startsWith("-")) {
                paths.add(resolvePath(path))
            }
        }

        val systemRegex = Regex("""-isystem\s*"?([^"\s,\]]+)"?""")
        systemRegex.findAll(json).forEach { match ->
            val path = match.groupValues[1]
            if (path.isNotEmpty() && !path.startsWith("-")) {
                paths.add(resolvePath(path))
            }
        }
    }
    
    /**
     * 解析路径（处理相对路径）
     */
    private fun resolvePath(path: String): String {
        return if (File(path).isAbsolute) {
            path
        } else {
            File(projectRoot, path).absolutePath
        }
    }

    private fun resolvePath(baseDir: File, path: String): String {
        return if (File(path).isAbsolute) {
            path
        } else {
            File(baseDir, path).absolutePath
        }
    }

    private fun selectCompileEntry(jsonArray: JSONArray): org.json.JSONObject? {
        if (jsonArray.length() <= 0) return null

        val currentPath = runCatching { currentFile.canonicalPath }.getOrDefault(currentFile.absolutePath)
        val currentNorm = normalizePath(currentPath)

        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.optJSONObject(i) ?: continue
            val file = entry.optString("file").takeIf { it.isNotBlank() } ?: continue
            if (normalizePath(file) == currentNorm) return entry
        }

        return jsonArray.optJSONObject(0)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
    
    /**
     * 在系统头文件目录中查找（rootfs）
     */
    private fun findInSystemIncludes(headerPath: String): File? {
        // 获取 rootfs 路径
        val rootfsDir = getRootfsDir() ?: return null
        
        for (includeDir in SYSTEM_INCLUDE_DIRS) {
            val headerFile = File(File(rootfsDir, includeDir), headerPath)
            if (headerFile.exists()) {
                return headerFile.canonicalFile
            }
        }
        
        // 对于 C++ 标准库头文件（如 iostream），可能没有 .h 后缀
        // 尝试在 c++/v1 目录中直接查找
        if (!headerPath.contains(".")) {
            val cxxDir = File(rootfsDir, "usr/include/c++/v1")
            val headerFile = File(cxxDir, headerPath)
            if (headerFile.exists()) {
                return headerFile.canonicalFile
            }
        }
        
        return null
    }
    
    /**
     * 获取 rootfs 目录
     */
    private fun getRootfsDir(): File? {
        val configured = Prefs.rootfsPath.trim()
        if (configured.isNotEmpty()) {
            val rootfs = File(configured)
            if (rootfs.exists() && File(rootfs, "usr/include").exists()) {
                return rootfs
            }
        }

        Timber.tag(TAG).w("No rootfs found with system includes")
        return null
    }
    
    /**
     * 在项目目录中递归搜索头文件
     * 注意：这是一个较慢的操作，仅作为最后手段
     */
    private fun searchInProject(headerPath: String): File? {
        val fileName = File(headerPath).name
        val projectDir = File(projectRoot)
        
        // 限制搜索深度和文件数量
        var searchCount = 0
        
        fun searchRecursive(dir: File, depth: Int): File? {
            if (depth > MAX_SEARCH_DEPTH || searchCount > MAX_SEARCH_COUNT) return null
            
            val files = dir.listFiles() ?: return null
            
            for (file in files) {
                searchCount++
                if (searchCount > MAX_SEARCH_COUNT) return null
                
                if (file.isFile && file.name == fileName) {
                    // 验证路径是否匹配（处理 subdir/header.h 的情况）
                    if (headerPath.contains("/")) {
                        if (file.absolutePath.endsWith(headerPath.replace("/", File.separator))) {
                            return file
                        }
                    } else {
                        return file
                    }
                } else if (file.isDirectory && !file.name.startsWith(".") && 
                           file.name != "build" && file.name != "node_modules") {
                    val found = searchRecursive(file, depth + 1)
                    if (found != null) return found
                }
            }
            return null
        }
        
        return searchRecursive(projectDir, 0)
    }
    
    /**
     * 清除缓存（当项目结构变化时调用）
     */
    fun clearCache() {
        cachedIncludePaths = null
    }
}
