package io.github.rt993.firetvjellyfin.ui.home

import android.util.TypedValue
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto

private const val CARD_WIDTH_DP = 180
private const val CARD_HEIGHT_DP = 270

/** Renders a single [BaseItemDto] as a poster card inside a leanback row. */
class CardPresenter(private val repository: JellyfinRepository) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val baseItem = item as? BaseItemDto ?: return
        val imageView = cardView.mainImageView ?: return

        cardView.titleText = baseItem.name
        cardView.contentText = baseItem.productionYear?.toString().orEmpty()

        val imageUrl = repository.buildImageUrl(baseItem.id)
        Glide.with(cardView.context)
            .load(imageUrl)
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_placeholder)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImageView?.let { Glide.with(cardView.context).clear(it) }
        cardView.mainImage = null
    }

    private fun ImageCardView.setMainImageDimensions(context: android.content.Context, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        setMainImageDimensions(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), context.resources.displayMetrics).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), context.resources.displayMetrics).toInt(),
        )
    }
}
