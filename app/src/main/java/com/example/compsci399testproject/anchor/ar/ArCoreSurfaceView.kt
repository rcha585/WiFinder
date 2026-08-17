package com.example.compsci399testproject.anchor.ar

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArCoreSurfaceView(
    context: Context,
    session: Session,
    depthSupported: Boolean,
    onFrame: (ArFramePacket) -> Unit,
    onError: (String) -> Unit,
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(
            ArRenderer(
                activity = context as Activity,
                session = session,
                depthSupported = depthSupported,
                onFrame = onFrame,
                onError = onError,
            ),
        )
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class ArRenderer(
        private val activity: Activity,
        private val session: Session,
        private val depthSupported: Boolean,
        private val onFrame: (ArFramePacket) -> Unit,
        private val onError: (String) -> Unit,
    ) : Renderer {
        private val backgroundRenderer = BackgroundRenderer()
        private val extractor = ArFrameExtractor()
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
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            try {
                session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
                val frame = session.update()
                backgroundRenderer.draw(frame)
                lastError = null
                onFrame(extractor.extract(session, frame, viewportWidth, viewportHeight, depthSupported))
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
