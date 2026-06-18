package org.jellyfin.androidtv.ui.jellyseerr

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage

/**
 * Leanback presenter that renders a [JellyseerrSearchResult] as a poster card. Images are loaded
 * directly from TMDB (the proxy only returns relative poster paths), so this does not go through
 * the Jellyfin image API.
 */
class JellyseerrCardPresenter : Presenter() {
	private companion object {
		private val CARD_WIDTH = 116.dp
		private val POSTER_HEIGHT = 174.dp
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = ComposeView(parent.context).apply {
			setParentCompositionContext(parent.findViewTreeCompositionContext())
			setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
			setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
			isFocusable = true
			isFocusableInTouchMode = true
		}

		return CardViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		if (viewHolder !is CardViewHolder) return
		if (item !is JellyseerrSearchResult) return

		viewHolder.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		(viewHolder as? CardViewHolder)?.unbind()
	}

	private inner class CardViewHolder(composeView: ComposeView) : ViewHolder(composeView) {
		private val _item = MutableStateFlow<JellyseerrSearchResult?>(null)
		private val _focused = MutableStateFlow(false)

		init {
			composeView.setContent {
				val item by _item.collectAsState()
				val focused by _focused.collectAsState()

				item?.let { JellyseerrCard(it, focused) }
			}

			composeView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
				_focused.value = hasFocus
			}
		}

		fun bind(item: JellyseerrSearchResult) {
			_item.value = item
			_focused.value = view.isFocused
		}

		fun unbind() {
			_item.value = null
			_focused.value = false
		}
	}

	@Composable
	private fun JellyseerrCard(result: JellyseerrSearchResult, focused: Boolean) {
		val shape = RoundedCornerShape(8.dp)

		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.padding(4.dp)
		) {
			Box(
				modifier = Modifier
					.width(CARD_WIDTH)
					.height(POSTER_HEIGHT)
					.clip(shape)
					.background(JellyfinTheme.colorScheme.surface)
					.border(
						width = if (focused) 3.dp else 0.dp,
						color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
						shape = shape,
					)
			) {
				val posterUrl = result.posterUrl
				if (posterUrl != null) {
					AsyncImage(
						url = posterUrl,
						modifier = Modifier.fillMaxSize(),
						scaleType = ImageView.ScaleType.CENTER_CROP,
					)
				} else {
					Image(
						painter = painterResource(R.drawable.ic_clapperboard),
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier
							.align(Alignment.Center)
							.size(40.dp),
					)
				}

				// Media type tag (top-start) so movies and series are distinguishable at a glance.
				Text(
					text = stringResource(
						if (result.isMovie) R.string.jellyseerr_type_movie else R.string.jellyseerr_type_series
					),
					color = JellyfinTheme.colorScheme.onBadge,
					fontSize = 9.sp,
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(4.dp)
						.clip(RoundedCornerShape(4.dp))
						.background(JellyfinTheme.colorScheme.badge)
						.padding(horizontal = 5.dp, vertical = 2.dp),
				)

				val badge = when (result.status) {
					JellyseerrMediaStatus.AVAILABLE -> stringResource(R.string.jellyseerr_status_available)
					JellyseerrMediaStatus.PENDING -> stringResource(R.string.jellyseerr_status_requested)
					JellyseerrMediaStatus.UNKNOWN -> null
				}
				if (badge != null) {
					Text(
						text = badge,
						color = Color.White,
						fontSize = 10.sp,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(4.dp)
							.clip(RoundedCornerShape(4.dp))
							.background(Color(0xCC000000))
							.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
			}

			Text(
				text = result.displayTitle,
				color = JellyfinTheme.colorScheme.onBackground,
				fontSize = 12.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier
					.width(CARD_WIDTH)
					.padding(top = 4.dp),
			)

			result.year?.let { year ->
				Text(
					text = year,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
					fontSize = 10.sp,
					modifier = Modifier.width(CARD_WIDTH),
				)
			}
		}
	}
}
