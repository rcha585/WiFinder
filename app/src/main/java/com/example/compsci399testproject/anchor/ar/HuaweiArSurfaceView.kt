package com.example.compsci399testproject.anchor.ar

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.huawei.hiar.ARSession
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class HuaweiArSurfaceView(
    context: Context,
    session: ARSession,
    onFrame: (ArFramePacket) -> Unit,
    onError: (String) -> Unit,
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(
            HuaweiArRenderer(
                activity = context as Activity,
                session = session,
                onFrame = onFrame,
                onError = onError,
            ),
        )
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class HuaweiArRenderer(
        private val activity: Activity,
        private val session: ARSession,
        private val onFrame: (ArFramePacket) -> Unit,
        private val onError: (String) -> Unit,
    ) : Renderer {
        private val backgroundRenderer = HuaweiBackgroundRenderer()
        private val extractor = HuaweiArFrameExtractor()
        private var viewportWidth = 0
        private var viewportHeight = 0
        private var displayRotation = 0
        private var lastError: String? = null

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            backgroundRenderer.createOnGlThread()
            session.setCameraTextureName(backgroundRenderer.textureId)
        }

        @Suppress("DEPRECATION")
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = width
            viewportHeight = height
            displayRotation = activity.windowManager.defaultDisplay.rotation
            session.setDisplayGeometry(displayRotation, width, height)
            GLES20.glViewport(0, 0, width, height)
        }

        @Suppress("DEPRECATION")
        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            try {
                displayRotation = activity.windowManager.defaultDisplay.rotation
                session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
                session.setCameraTextureName(backgroundRenderer.textureId)
                val frame = session.update()
                backgroundRenderer.draw(frame)
                lastError = null
                onFrame(extractor.extract(session, frame, viewportWidth, viewportHeight))
            } catch (exception: Exception) {
                val message = exception.message ?: exception.javaClass.simpleName
                if (message != lastError) {
                    lastError = message
                    activity.runOnUiThread { onError(message) }
                }
            }
        }
    }
}
