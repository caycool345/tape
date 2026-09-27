package com.tapemeasure.ar

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Draws the tap markers and the measuring line between them as simple colored GL primitives. */
class PointLineRenderer {

    private var program = 0
    private var positionAttrib = 0
    private var mvpUniform = 0
    private var colorUniform = 0
    private var pointSizeUniform = 0

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(MAX_POINTS * 3 * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    fun createOnGlThread() {
        program = ShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color")
        pointSizeUniform = GLES20.glGetUniformLocation(program, "u_PointSize")
    }

    /**
     * @param points world-space xyz positions of the placed anchors (0, 1 or 2 of them).
     * @param viewProjectionMatrix the camera's combined view * projection matrix (16 floats).
     */
    fun draw(points: List<FloatArray>, viewProjectionMatrix: FloatArray) {
        if (points.isEmpty()) return

        vertexBuffer.position(0)
        for (p in points) {
            vertexBuffer.put(p[0])
            vertexBuffer.put(p[1])
            vertexBuffer.put(p[2])
        }
        vertexBuffer.position(0)

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, viewProjectionMatrix, 0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glLineWidth(8f)

        if (points.size == 2) {
            GLES20.glUniform4f(colorUniform, 1f, 0.75f, 0.15f, 1f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }

        GLES20.glUniform4f(colorUniform, 1f, 0.25f, 0.5f, 1f)
        GLES20.glUniform1f(pointSizeUniform, 28f)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points.size)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisableVertexAttribArray(positionAttrib)
    }

    companion object {
        private const val FLOAT_SIZE = 4
        private const val MAX_POINTS = 2

        private const val VERTEX_SHADER = """
            uniform mat4 u_ModelViewProjection;
            uniform float u_PointSize;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_ModelViewProjection * a_Position;
                gl_PointSize = u_PointSize;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }
}
