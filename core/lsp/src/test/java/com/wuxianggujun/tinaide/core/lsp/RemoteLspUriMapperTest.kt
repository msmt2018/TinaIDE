package com.wuxianggujun.tinaide.core.lsp

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RemoteLspUriMapperTest {

    @Test
    fun `hasMapping requires both client and server roots`() {
        val mapper = RemoteLspUriMapper()

        assertThat(mapper.hasMapping()).isFalse()

        mapper.setClientRootUri("file:///client/project")
        assertThat(mapper.hasMapping()).isFalse()

        mapper.setServerRootUri("file:///server/workspace")
        assertThat(mapper.hasMapping()).isTrue()
    }

    @Test
    fun `rewriteClientToServer rewrites uri and rootUri fields`() {
        val mapper = RemoteLspUriMapper().apply {
            setClientRootUri("file:///client/project")
            setServerRootUri("file:///server/workspace")
        }

        val rewritten = mapper.rewriteClientToServer(
            """
            {
              "params": {
                "rootUri": "file:///client/project",
                "textDocument": {
                  "uri": "file:///client/project/src/main.cpp"
                },
                "label": "file:///client/project/src/main.cpp"
              }
            }
            """.trimIndent()
        )
        val params = JSONObject(rewritten).getJSONObject("params")

        assertThat(params.getString("rootUri"))
            .isEqualTo("file:///server/workspace")
        assertThat(params.getJSONObject("textDocument").getString("uri"))
            .isEqualTo("file:///server/workspace/src/main.cpp")
        assertThat(params.getString("label"))
            .isEqualTo("file:///client/project/src/main.cpp")
    }

    @Test
    fun `rewriteServerToClient rewrites file uri values inside arrays`() {
        val mapper = RemoteLspUriMapper().apply {
            setClientRootUri("file:///client/project")
            setServerRootUri("file:///server/workspace")
        }

        val rewritten = mapper.rewriteServerToClient(
            """
            {
              "result": {
                "workspaceFolders": [
                  "file:///server/workspace",
                  "file:///server/workspace/lib"
                ],
                "uris": [
                  "file:///server/workspace/src/main.cpp",
                  "not-a-file-uri"
                ]
              }
            }
            """.trimIndent()
        )
        val result = JSONObject(rewritten).getJSONObject("result")
        val workspaceFolders = result.getJSONArray("workspaceFolders")
        val uris = result.getJSONArray("uris")

        assertThat(workspaceFolders.getString(0))
            .isEqualTo("file:///client/project")
        assertThat(workspaceFolders.getString(1))
            .isEqualTo("file:///client/project/lib")
        assertThat(uris.getString(0))
            .isEqualTo("file:///client/project/src/main.cpp")
        assertThat(uris.getString(1))
            .isEqualTo("not-a-file-uri")
    }

    @Test
    fun `rewrite keeps original text when no mapping exists or json is invalid`() {
        val mapper = RemoteLspUriMapper()
        val jsonText = """{"uri":"file:///client/project/src/main.cpp"}"""

        assertThat(mapper.rewriteClientToServer(jsonText))
            .isEqualTo(jsonText)

        mapper.setClientRootUri("file:///client/project")
        mapper.setServerRootUri("file:///server/workspace")

        val invalidJson = "{\"uri\":\"file:///client/project/src/main.cpp\""
        assertThat(mapper.rewriteClientToServer(invalidJson))
            .isEqualTo(invalidJson)
    }
}
