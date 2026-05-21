package com.wuxianggujun.tinaide.search

class JsonSearchEngine(
    private val searchPaths: (String) -> List<String>
) : SearchEngine {

    override fun search(query: String, options: SearchOptions): List<SearchResult> {
        if (query.isEmpty()) return emptyList()
        return searchPaths(query).map { JsonSearchResult(it) }
    }
}

