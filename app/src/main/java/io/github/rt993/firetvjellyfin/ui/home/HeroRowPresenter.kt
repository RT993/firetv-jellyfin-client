package io.github.rt993.firetvjellyfin.ui.home

import android.util.TypedValue
import android.view.ViewGroup
import androidx.leanback.widget.RowPresenter
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

private const val HERO_HEIGHT_DP = 340

/** Binds a [HeroRow] to a [HeroBannerView]. */
class HeroRowPresenter(
    private val repository: JellyfinRepository,
    private val userId: UUID,
    private val onPlayClicked: (BaseItemDto) -> Unit,
    private val onInfoClicked: (BaseItemDto) -> Unit,
) : RowPresenter() {

    override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            HERO_HEIGHT_DP.toFloat(),
            parent.context.resources.displayMetrics,
        ).toInt()
        val heroView = HeroBannerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        }
        return ViewHolder(heroView)
    }

    override fun onBindRowViewHolder(viewHolder: ViewHolder, item: Any) {
        super.onBindRowViewHolder(viewHolder, item)
        val row = item as HeroRow
        val heroView = viewHolder.view as HeroBannerView
        heroView.bind(row.items, repository, userId, onPlayClicked, onInfoClicked)
    }

    override fun onUnbindRowViewHolder(viewHolder: ViewHolder) {
        super.onUnbindRowViewHolder(viewHolder)
        (viewHolder.view as HeroBannerView).unbind()
    }
}
