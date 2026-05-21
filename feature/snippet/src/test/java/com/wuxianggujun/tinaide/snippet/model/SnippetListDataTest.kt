package com.wuxianggujun.tinaide.snippet.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SnippetListDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun snippetListData_shouldDeserializePaginationAndNestedSummaries() {
        val data = json.decodeFromString<SnippetListData>(
            """
            {
              "snippets": [
                {
                  "id": "s1",
                  "title": "Hello",
                  "description": "Sample",
                  "language": "kotlin",
                  "code_preview": "println(\"hi\")",
                  "author": {
                    "id": "u1",
                    "display_name": "Ada"
                  },
                  "category": "basics",
                  "tags": ["kotlin", "io"],
                  "status": "published",
                  "copy_count": 4,
                  "rating_avg": 4.25,
                  "rating_count": 8,
                  "created_at": "2026-01-01T00:00:00Z"
                }
              ],
              "pagination": {
                "page": 2,
                "limit": 20,
                "total": 41,
                "total_pages": 3
              }
            }
            """.trimIndent()
        )

        assertThat(data.pagination.page).isEqualTo(2)
        assertThat(data.pagination.limit).isEqualTo(20)
        assertThat(data.pagination.total).isEqualTo(41L)
        assertThat(data.pagination.totalPages).isEqualTo(3)

        val summary = data.snippets.single()
        assertThat(summary.tags).containsExactly("kotlin", "io").inOrder()
        assertThat(summary.copyCount).isEqualTo(4L)
        assertThat(summary.ratingAvg).isEqualTo(4.25)
        assertThat(summary.ratingCount).isEqualTo(8)
    }

    @Test
    fun pagination_shouldKeepValueSemantics() {
        assertThat(Pagination(page = 1, limit = 10, total = 0, totalPages = 0))
            .isEqualTo(Pagination(page = 1, limit = 10, total = 0, totalPages = 0))
    }
}
