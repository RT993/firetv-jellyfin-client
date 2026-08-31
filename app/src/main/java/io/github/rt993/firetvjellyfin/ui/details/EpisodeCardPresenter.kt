package io.github.rt993.firetvjellyfin.ui.details

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

private const val CARD_WIDTH_DP = 240
private const val CARD_HEIGHT_DP = 135
private const val CARD_CORNER_RADIUS_DP = 10
private const val FOCUS_SCALE = 1.1f
private const val FOCUS_ELEVATION_DP = 18
private const val FOCUS_IN_ANIM_MS = 220L
private const val FOCUS_OUT_ANIM_MS = 150L
private const val FOCUS_OVERSHOOT_TENSION = 2.5f

/** Renders a single episode as a landscape still-frame card inside a season row. */
class EpisodeCardPresenter(private val repository: JellyfinRepository) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
            infoAreaBackground = ColorDrawable(Color.TRANSPARENT)
            clipRoundedCorners(context)

            val focusElevationPx = dpToPx(context, FOCUS_ELEVATION_DP)
            onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                    .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                    .translationZ(if (hasFocus) focusElevationPx else 0f)
                    .setDuration(if (hasFocus) FOCUS_IN_ANIM_MS else FOCUS_OUT_ANIM_MS)
                    .setInterpolator(if (hasFocus) OvershootInterpolator(FOCUS_OVERSHOOT_TENSION) else DecelerateInterpolator())
                    .start()
            }
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val episode = item as? BaseItemDto ?: return
        val imageView = cardView.mainImageView ?: return

        val number = episode.indexNumber?.let { "$it. " }.orEmpty()
        cardView.titleText = "$number${episode.name}"
        cardView.contentText = formatRuntimeTicks(episode.runTimeTicks).orEmpty()

        val imageUrl = repository.buildImageUrl(episode.id, imageType = ImageType.PRIMARY, maxWidth = 400)
        Glide.with(cardView.context)
            .load(imageUrl)
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_placeholder)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // Same rationale as CardPresenter: RecyclerView can unbind views during the host
        // Activity's own teardown, after which Glide.with() throws.
        runCatching {
            cardView.mainImageView?.let { Glide.with(cardView.context).clear(it) }
        }
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

    private fun dpToPx(context: Context, dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)
}
