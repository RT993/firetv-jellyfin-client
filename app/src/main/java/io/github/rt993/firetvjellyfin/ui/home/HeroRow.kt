package io.github.rt993.firetvjellyfin.ui.home

import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Row
import org.jellyfin.sdk.model.api.BaseItemDto

/** A row of items shown one at a time as a big paginated banner, via [HeroBannerView]. */
class HeroRow(header: HeaderItem, val items: List<BaseItemDto>) : Row(header)
