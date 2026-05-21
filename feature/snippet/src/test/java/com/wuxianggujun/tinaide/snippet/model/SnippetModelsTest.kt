package com.wuxianggujun.tinaide.snippet.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SnippetModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun snippetSummary_shouldDeserializeSnakeCaseFieldsAndDefaults() {
        val summary = json.decodeFromString<SnippetSummary>(
            """
            {
              "id": "s1",
              "title": "Loop",
              "language": "kotlin",
              "code_preview": "repeat(3) {}",
              "author": { "id": "u1", "display_name": "Ada" },
              "favorite_count": 7,
              "is_featured": true,
              "created_at": "2026-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

        assertThat(summary.codePreview).isEqualTo("repeat(3) {}")
        assertThat(summary.author.displayName).isEqualTo("Ada")
        assertThat(summary.favoriteCount).isEqualTo(7L)
        assertThat(summary.copyCount).isEqualTo(0L)
        assertThat(summary.isFeatured).isTrue()
        assertThat(summary.tags).isEmpty()
    }

    @Test
    fun snippetDetail_shouldDeserializeUserSpecificFields() {
        val detail = json.decodeFromString<SnippetDetail>(
            """
            {
              "id": "s1",
              "title": "Loop",
              "code_content": "repeat(3) {}",
              "language": "kotlin",
              "file_extension": "kt",
              "author": { "id": "u1", "display_name": "Ada", "avatar_url": "https://example.test/a.png" },
              "view_count": 10,
              "copy_count": 2,
              "favorite_count": 3,
              "rating_avg": 4.5,
              "rating_count": 6,
              "created_at": "2026-01-01T00:00:00Z",
              "updated_at": "2026-01-02T00:00:00Z",
              "is_favorited": true,
              "my_rating": 5
            }
            """.trimIndent()
        )

        assertThat(detail.codeContent).isEqualTo("repeat(3) {}")
        assertThat(detail.fileExtension).isEqualTo("kt")
        assertThat(detail.author.avatarUrl).isEqualTo("https://example.test/a.png")
        assertThat(detail.isFavorited).isTrue()
        assertThat(detail.myRating).isEqualTo(5)
    }
}
