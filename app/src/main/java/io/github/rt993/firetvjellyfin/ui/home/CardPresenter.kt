package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto

private const val CARD_WIDTH_DP = 180
private const val CARD_HEIGHT_DP = 270
private const val CARD_CORNER_RADIUS_DP = 14
private const val FOCUS_RING_WIDTH_DP = 3
private const val FOCUS_SCALE = 1.08f
private const val FOCUS_ANIM_MS = 150L

/** Renders a single [BaseItemDto] as a poster card inside a leanback row. */
class CardPresenter(private val repository: JellyfinRepository) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
            infoAreaBackground = ContextCompat.getDrawable(context, R.color.card_default)
            clipRoundedCorners(context)

            val focusRing = createFocusRingDrawable(context)
            onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                view.foreground = if (hasFocus) focusRing else null
                view.animate()
                    .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                    .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                    .setDuration(FOCUS_ANIM_MS)
                    .start()
            }
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

    private fun ImageCardView.setMainImageDimensions(context: Context, widthDp: Int, heightDp: Int) {
        setMainImageDimensions(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), context.resources.displayMetrics).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), context.resources.displayMetrics).toInt(),
        )
    }

    private fun ImageCardView.clipRoundedCorners(context: Context) {
        val radiusPx = dpToPx(context, CARD_CORNER_RADIUS_DP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true
    }

    /** Crisp amber ring shown around the card while it has D-pad focus. */
    private fun createFocusRingDrawable(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(context, CARD_CORNER_RADIUS_DP)
        setStroke(dpToPx(context, FOCUS_RING_WIDTH_DP).toInt(), ContextCompat.getColor(context, R.color.focus_ring_amber))
        setColor(Color.TRANSPARENT)
    }

    private fun dpToPx(context: Context, dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)
}
