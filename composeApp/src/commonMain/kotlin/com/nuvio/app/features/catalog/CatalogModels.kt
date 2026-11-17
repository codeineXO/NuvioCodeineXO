package com.nuvio.app.features.catalog

import com.nuvio.app.features.home.MetaPreview

data class CatalogUiState(
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val nextSkip: Int? = null,
    val consecutiveDuplicatePages: Int = 0,
    val errorMessage: String? = null,
    val target: CatalogTarget? = null,
    val hideUnreleasedContent: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = nextSkip != null

    internal fun matches(target: CatalogTarget, hideUnreleasedContent: Boolean): Boolean =
        this.target == target && this.hideUnreleasedContent == hideUnreleasedContent
}

data class CatalogScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
