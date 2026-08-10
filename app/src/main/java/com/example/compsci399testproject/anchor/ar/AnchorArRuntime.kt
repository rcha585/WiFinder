package com.example.compsci399testproject.anchor.ar

import com.google.ar.core.Session as GoogleSession
import com.huawei.hiar.ARSession as HuaweiSession

sealed interface AnchorArRuntime {
    val engineLabel: String
    val depthSupported: Boolean
}

data class GoogleArRuntime(
    val session: GoogleSession,
    override val depthSupported: Boolean,
) : AnchorArRuntime {
    override val engineLabel: String = "Google ARCore"
}

data class HuaweiArRuntime(
    val session: HuaweiSession,
) : AnchorArRuntime {
    override val engineLabel: String = "Huawei AR Engine"
    override val depthSupported: Boolean = false
}
