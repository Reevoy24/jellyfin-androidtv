package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrCardPresenter
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrSearchResult
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class SearchFragmentDelegate(
	private val context: Context,
	private val backgroundService: BackgroundService,
	private val itemLauncher: ItemLauncher,
) {
	val rowsAdapter = MutableObjectAdapter<Row>(CustomListRowPresenter())

	/** Invoked when a Jellyseerr (request) result card is clicked. */
	var onJellyseerrItemClicked: ((JellyseerrSearchResult) -> Unit)? = null

	fun showResults(
		searchResultGroups: Collection<SearchResultGroup>,
		jellyseerrResults: List<JellyseerrSearchResult> = emptyList(),
	) {
		rowsAdapter.clear()
		val adapters = mutableListOf<ItemRowAdapter>()
		for ((labelRes, baseItems) in searchResultGroups) {
			val adapter = ItemRowAdapter(
				context,
				baseItems.toList(),
				CardPresenter(),
				rowsAdapter,
				QueryType.Search
			).apply {
				setRow(ListRow(HeaderItem(context.getString(labelRes)), this))
			}
			adapters.add(adapter)
		}
		for (adapter in adapters) adapter.Retrieve()

		if (jellyseerrResults.isNotEmpty()) {
			val jellyseerrAdapter = ArrayObjectAdapter(JellyseerrCardPresenter())
			jellyseerrResults.forEach(jellyseerrAdapter::add)
			rowsAdapter.add(
				ListRow(
					HeaderItem(context.getString(R.string.jellyseerr_request_row)),
					jellyseerrAdapter,
				)
			)
		}
	}

	val onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
		when (item) {
			is JellyseerrSearchResult -> onJellyseerrItemClicked?.invoke(item)
			is BaseRowItem -> {
				row as ListRow
				val adapter = row.adapter as ItemRowAdapter
				itemLauncher.launch(item, adapter, context)
			}
		}
	}

	val onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
		val baseItem = (item as? BaseRowItem)?.baseItem
		if (baseItem != null) {
			backgroundService.setBackground(baseItem)
		} else {
			backgroundService.clearBackgrounds()
		}
	}
}
