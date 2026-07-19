package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * Converts a side-by-side or top-bottom stereoscopic frame into a row-interleaved image for
 * passive (polarized) 3D TVs: even output rows show one eye, odd rows the other. The panel's
 * film-pattern retarder polarizes alternating rows permanently, so the polarized glasses separate
 * the eyes WITHOUT the TV being in any 3D mode — which HDMI sources like the Fire TV cannot
 * trigger anyway (no HDMI 1.4 frame-packing support).
 *
 * The shader renders at the given output (panel) resolution and bakes the letter-/pillarbox bars
 * into its output so no later scaling can destroy the line pattern. For the 3D effect to work the
 * TV must display the signal 1:1 (overscan off / "just scan").
 */
@UnstableApi
class StereoInterleaveEffect(
	private val format: StereoFormat,
	private val swapEyes: Boolean,
	private val outputWidth: Int,
	private val outputHeight: Int,
) : GlEffect {
	override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
		StereoInterleaveShaderProgram(format, swapEyes, outputWidth, outputHeight, useHdr)
}

@UnstableApi
private class StereoInterleaveShaderProgram(
	private val format: StereoFormat,
	private val swapEyes: Boolean,
	private val outputWidth: Int,
	private val outputHeight: Int,
	useHdr: Boolean,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents = */ useHdr, /* texturePoolCapacity = */ 1) {
	companion object {
		private const val VERTEX_SHADER = """
			attribute vec4 aFramePosition;
			void main() {
				gl_Position = aFramePosition;
			}
		"""

		// gl_FragCoord has its origin at the BOTTOM-left of the output framebuffer, matching the
		// bottom-up texture orientation media3 uses for intermediate frames, so v maps 1:1.
		private const val FRAGMENT_SHADER = """
			precision mediump float;
			uniform sampler2D uTexSampler;
			uniform vec4 uVideoRect;   // x, y, w, h of the video area in output pixels (origin bottom-left)
			uniform float uTab;        // 1.0 = top-bottom layout, 0.0 = side-by-side
			uniform float uSwap;       // 1.0 = swap eyes
			void main() {
				vec2 v = (gl_FragCoord.xy - uVideoRect.xy) / uVideoRect.zw;
				if (v.x < 0.0 || v.x > 1.0 || v.y < 0.0 || v.y > 1.0) {
					gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
					return;
				}
				float eye = mod(floor(gl_FragCoord.y), 2.0);   // 0 = left eye rows, 1 = right eye rows
				if (uSwap > 0.5) eye = 1.0 - eye;
				vec2 c;
				if (uTab < 0.5) {
					// SBS: left half of the frame = left eye
					c = vec2(v.x * 0.5 + eye * 0.5, v.y);
				} else {
					// TAB: TOP half of the frame = left eye (top = high y in bottom-up coords)
					c = vec2(v.x, v.y * 0.5 + (1.0 - eye) * 0.5);
				}
				gl_FragColor = texture2D(uTexSampler, c);
			}
		"""
	}

	private val glProgram: GlProgram
	private var videoRect = floatArrayOf(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat())

	init {
		try {
			glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
		} catch (e: GlUtil.GlException) {
			throw VideoFrameProcessingException(e)
		}
		glProgram.setBufferAttribute(
			"aFramePosition",
			GlUtil.getNormalizedCoordinateBounds(),
			GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
		)
	}

	override fun configure(inputWidth: Int, inputHeight: Int): Size {
		// Intended display aspect ratio of the (unsqueezed) content
		val aspect = when {
			format.full && !format.tab -> (inputWidth / 2f) / inputHeight
			format.full && format.tab -> inputWidth / (inputHeight / 2f)
			else -> inputWidth.toFloat() / inputHeight
		}
		val outAspect = outputWidth.toFloat() / outputHeight
		var w = outputWidth.toFloat()
		var h = outputHeight.toFloat()
		if (aspect > outAspect) h = w / aspect else w = h * aspect
		videoRect = floatArrayOf((outputWidth - w) / 2f, (outputHeight - h) / 2f, w, h)
		// Output at full panel resolution with the bars baked in, so the final copy to the display
		// surface is 1:1 and the row pattern survives.
		return Size(outputWidth, outputHeight)
	}

	override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
		try {
			glProgram.use()
			glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
			glProgram.setFloatsUniform("uVideoRect", videoRect)
			glProgram.setFloatUniform("uTab", if (format.tab) 1f else 0f)
			glProgram.setFloatUniform("uSwap", if (swapEyes) 1f else 0f)
			glProgram.bindAttributesAndUniforms()
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
		} catch (e: GlUtil.GlException) {
			throw VideoFrameProcessingException(e, presentationTimeUs)
		}
	}

	override fun release() {
		super.release()
		try {
			glProgram.delete()
		} catch (e: GlUtil.GlException) {
			throw VideoFrameProcessingException(e)
		}
	}
}
