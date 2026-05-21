package com.wuxianggujun.tinaide.search

interface SearchEngine {
    fun search(query: String, options: SearchOptions = SearchOptions()): List<SearchResult>

    fun replaceAll(query: String, replacement: String, options: SearchOptions = SearchOptions()): Int = 0

    fun supportsReplace(): Boolean = false
}

