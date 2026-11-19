package com.naturalai.camerapro.processing

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGlPreviewRenderer(
    private val onSurfaceReady: (Surface) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var focusPeakingEnabled: Boolean = false
    @Volatile var zebraEnabled: Boolean = false
    @Volatile var vignetteCorrection: Float = 0f
    @Volatile var redGainTrim: Float = 1f
    @Volatile var greenGainTrim: Float = 1f
    @Volatile var blueGainTrim: Float = 1f
    @Volatile var gridModeIndex: Int = 1 // 0=off, 1=thirds, 2=phi, 3=crosshair
    @Volatile var sensorOrientationDeg: Int = 90

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var programId = 0
    private val transformMatrix = FloatArray(16)

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )).apply { position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )

        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setDefaultBufferSize(1920, 1080)
            setOnFrameAvailableListener { requestRender() }
        }
        previewSurface = Surface(surfaceTexture).also { onSurfaceReady(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(transformMatrix)

        GLES30.glClearColor(0.02f, 0.02f, 0.02f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(programId, "uTexMatrix"),
            1, false, transformMatrix, 0
        )
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(programId, "uFocusPeaking"),
            if (focusPeakingEnabled) 1 else 0
        )
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(programId, "uZebra"),
            if (zebraEnabled) 1 else 0
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(programId, "uVignette"),
            vignetteCorrection
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(programId, "uRgbTrim"),
            redGainTrim, greenGainTrim, blueGainTrim
        )
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(programId, "uGridMode"),
            gridModeIndex
        )

        val posLoc = GLES30.glGetAttribLocation(programId, "aPos")
        val uvLoc = GLES30.glGetAttribLocation(programId, "aUv")
        quadVertices.position(0)
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
        quadVertices.position(2)
        GLES30.glEnableVertexAttribArray(uvLoc)
        GLES30.glVertexAttribPointer(uvLoc, 2, GLES30.GL_FLOAT, false, 16, quadVertices)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        previewSurface?.release()
        surfaceTexture?.release()
    }

    private fun createProgram(vs: String, fs: String): Int {
        val vShader = compileShader(GLES30.GL_VERTEX_SHADER, vs)
        val fShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)
        val prog = GLES30.GL_createProgram()
        GLES30.glAttachShader(prog, vShader)
        GLES30.glAttachShader(prog, fShader)
        GLES30.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, src)
        GLES30.glCompileShader(id)
        return id
    }

    private fun GLES30.GL_createProgram(): Int = GLES30.glCreateProgram()

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uTexMatrix;
            out vec2 vUv;
            out vec2 vScreenUv;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vScreenUv = aUv;
                vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            uniform int uFocusPeaking;
            uniform int uZebra;
            uniform float uVignette;
            uniform vec3 uRgbTrim;
            uniform int uGridMode;
            in vec2 vUv;
            in vec2 vScreenUv;
            out vec4 fragColor;

            float luma(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec3 col = texture(uTex, vUv).rgb;
                col *= uRgbTrim;

                // Radial vignette compensation for Direct Sensor / external optics
                if (uVignette > 0.01) {
                    vec2 d = vScreenUv - vec2(0.5);
                    float r2 = dot(d, d) * 4.0;
                    col *= (1.0 + uVignette * 0.45 * r2);
                }

                // Sobel focus peaking
                if (uFocusPeaking == 1) {
                    vec2 step = vec2(1.0 / 1280.0, 1.0 / 720.0);
                    float tl = luma(texture(uTex, vUv + vec2(-step.x, step.y)).rgb);
                    float tr = luma(texture(uTex, vUv + vec2(step.x, step.y)).rgb);
                    float bl = luma(texture(uTex, vUv + vec2(-step.x, -step.y)).rgb);
                    float br = luma(texture(uTex, vUv + vec2(step.x, -step.y)).rgb);
                    float l = luma(texture(uTex, vUv + vec2(-step.x, 0.0)).rgb);
                    float r = luma(texture(uTex, vUv + vec2(step.x, 0.0)).rgb);
                    float t = luma(texture(uTex, vUv + vec2(0.0, step.y)).rgb);
                    float b = luma(texture(uTex, vUv + vec2(0.0, -step.y)).rgb);
                    float gx = -tl - 2.0*l - bl + tr + 2.0*r + br;
                    float gy = -bl - 2.0*b - br + tl + 2.0*t + tr;
                    float mag = sqrt(gx*gx + gy*gy);
                    if (mag > 0.18) {
                        col = mix(col, vec3(0.02, 0.85, 0.96), 0.85);
                    }
                }

                // Zebra highlight clipping & shadow crush indicator
                if (uZebra == 1) {
                    float y = luma(col);
                    float stripe = mod(gl_FragCoord.x + gl_FragCoord.y, 12.0);
                    if (y > 0.97 && stripe < 5.0) {
                        col = vec3(0.96, 0.25, 0.25);
                    } else if (y < 0.02 && stripe < 4.0) {
                        col = vec3(0.15, 0.45, 0.95);
                    }
                }

                // Grid overlays
                if (uGridMode == 1) {
                    float gx = min(abs(vScreenUv.x - 0.3333), abs(vScreenUv.x - 0.6666));
                    float gy = min(abs(vScreenUv.y - 0.3333), abs(vScreenUv.y - 0.6666));
                    if (gx < 0.0018 || gy < 0.0018) col = mix(col, vec3(1.0), 0.32);
                } else if (uGridMode == 2) {
                    float gx = min(abs(vScreenUv.x - 0.382), abs(vScreenUv.x - 0.618));
                    float gy = min(abs(vScreenUv.y - 0.382), abs(vScreenUv.y - 0.618));
                    if (gx < 0.0018 || gy < 0.0018) col = mix(col, vec3(1.0), 0.32);
                } else if (uGridMode == 3) {
                    float cx = abs(vScreenUv.x - 0.5);
                    float cy = abs(vScreenUv.y - 0.5);
                    if ((cx < 0.0018 && cy < 0.04) || (cy < 0.0018 && cx < 0.04)) {
                        col = mix(col, vec3(0.96, 0.62, 0.04), 0.8);
                    }
                }

                fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
            }
        """
    }
}
