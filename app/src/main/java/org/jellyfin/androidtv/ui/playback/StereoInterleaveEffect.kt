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
 * Only the VERTICAL resolution must match the panel exactly (one output row per panel row, TV
 * overscan off). The horizontal direction is scaled by the display hardware for free, so the
 * output buffer may be narrower than the panel — [outputWidth] is typically half the display
 * width on 4K panels to halve the GPU load on weak TV-stick GPUs. [displayAspect] is the aspect
 * ratio of the PHYSICAL display area the (possibly anamorphic) buffer gets stretched onto; the
 * letterbox bars are computed against it and baked into the output so no later scaling can
 * destroy the line pattern.
 */
@UnstableApi
class StereoInterleaveEffect(
	private val format: StereoFormat,
	private val swapEyes: Boolean,
	private val outputWidth: Int,
	private val outputHeight: Int,
	private val displayAspect: Float,
) : GlEffect {
	override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
		StereoInterleaveShaderProgram(format, swapEyes, outputWidth, outputHeight, displayAspect)
}

@UnstableApi
private class StereoInterleaveShaderProgram(
	private val format: StereoFormat,
	private val swapEyes: Boolean,
	private val outputWidth: Int,
	private val outputHeight: Int,
	private val displayAspect: Float,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents = */ false, /* texturePoolCapacity = */ 1) {
	companion object {
		private const val VERTEX_SHADER = """
			attribute vec4 aFramePosition;
			void main() {
				gl_Position = aFramePosition;
			}
		"""

		// gl_FragCoord has its origin at the BOTTOM-left of the output framebuffer, matching the
		// bottom-up texture orientation media3 uses for intermediate frames, so v maps 1:1.
		// highp is required: row parity is computed from coordinates up to 2160 and mediump only
		// guarantees ~2^10 relative precision. Branch-free (step/mix) for weak TV-stick GPUs.
		private const val FRAGMENT_SHADER = """
			precision highp float;
			uniform sampler2D uTexSampler;
			uniform vec4 uVideoRect;   // x, y, w, h of the video area in output pixels (origin bottom-left)
			uniform float uTab;        // 1.0 = top-bottom layout, 0.0 = side-by-side
			uniform float uSwap;       // 1.0 = swap eyes
			void main() {
				vec2 v = (gl_FragCoord.xy - uVideoRect.xy) / uVideoRect.zw;
				// 0 = left eye rows, 1 = right eye rows (uSwap inverts)
				float eye = abs(uSwap - mod(floor(gl_FragCoord.y), 2.0));
				// SBS: left half of the frame = left eye; TAB: TOP half (high y) = left eye
				vec2 sbs = vec2(v.x * 0.5 + eye * 0.5, v.y);
				vec2 tab = vec2(v.x, v.y * 0.5 + (1.0 - eye) * 0.5);
				vec2 c = mix(sbs, tab, uTab);
				float inside = step(0.0, v.x) * step(v.x, 1.0) * step(0.0, v.y) * step(v.y, 1.0);
				gl_FragColor = vec4(texture2D(uTexSampler, c).rgb * inside, 1.0);
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
		val videoAspect = when {
			format.full && !format.tab -> (inputWidth / 2f) / inputHeight
			format.full && format.tab -> inputWidth / (inputHeight / 2f)
			else -> inputWidth.toFloat() / inputHeight
		}
		// Fit against the PHYSICAL display shape (the buffer may be anamorphic), then express the
		// resulting rect in buffer pixels.
		var wFraction = 1f
		var hFraction = 1f
		if (videoAspect > displayAspect) hFraction = displayAspect / videoAspect
		else wFraction = videoAspect / displayAspect
		val w = outputWidth * wFraction
		val h = outputHeight * hFraction
		videoRect = floatArrayOf((outputWidth - w) / 2f, (outputHeight - h) / 2f, w, h)
		// Output with the bars baked in, so the final copy to the display surface is 1:1 per row
		// and the row pattern survives.
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
