package com.wuxianggujun.tinaide.core.compile.artifact

import kotlinx.serialization.Serializable

/**
 * 产物种类。
 *
 * 替代旧 `CompileProjectUseCase.BuildArtifactKind`,语义等价但显式声明 OBJECT,
 * 供未来增量链接场景使用。
 */
@Serializable
enum class ArtifactKind {
    EXECUTABLE,
    SHARED_LIBRARY,
    STATIC_LIBRARY,
    OBJECT,
    UNKNOWN,
}
