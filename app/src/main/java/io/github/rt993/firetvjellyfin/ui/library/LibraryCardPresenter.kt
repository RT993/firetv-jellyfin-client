package io.github.rt993.firetvjellyfin.ui.library

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto

private const val CARD_WIDTH_DP = 200
private const val CARD_HEIGHT_DP = 300
private const val CARD_CORNER_RADIUS_DP = 14
private const val FOCUS_SCALE = 1.1f
private const val FOCUS_ELEVATION_DP = 18
private const val FOCUS_IN_ANIM_MS = 220L
private const val FOCUS_OUT_ANIM_MS = 150L
private const val FOCUS_OVERSHOOT_TENSION = 2.5f
private const val PLAY_BADGE_SIZE_DP = 40
private const val PLAY_BADGE_MARGIN_DP = 10
private const val PLAY_BADGE_ICON_PADDING_DP = 10

/**
 * A bigger, more "cinematic" poster card for [LibraryGridFragment] than the compact ones used in
 * Home's shelves ([io.github.rt993.firetvjellyfin.ui.home.CardPresenter]) - deliberately a
 * separate presenter rather than a resize of that one, so the Home rows aren't affected. Adds a
 * decorative play badge in the corner (tapping the card still opens Details, same as everywhere
 * else in the app - this is a visual cue, not a second click target, since a D-pad has no way to
 * aim at a sub-region of a focused card the way a touchscreen tap can).
 */
class LibraryCardPresenter(private val repository: JellyfinRepository) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
            infoAreaBackground = ColorDrawable(Color.TRANSPARENT)
            background = ColorDrawable(Color.TRANSPARENT)
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

            addView(buildPlayBadge(context))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val baseItem = item as? BaseItemDto ?: return
        val imageView = cardView.mainImageView ?: return

        cardView.titleText = baseItem.name
        cardView.contentText = baseItem.productionYear?.toString().orEmpty()

        val imageUrl = repository.buildImageUrl(baseItem.id, maxWidth = dpToPx(cardView.context, CARD_WIDTH_DP).toInt() * 2)
        Glide.with(cardView.context)
            .load(imageUrl)
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_placeholder)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // See CardPresenter.onUnbindViewHolder for why this is wrapped in runCatching.
        runCatching {
            cardView.mainImageView?.let { Glide.with(cardView.context).clear(it) }
        }
        cardView.mainImage = null
    }

    /** A small translucent circle with a play glyph, pinned to the poster's bottom-start corner. */
    private fun buildPlayBadge(context: Context): View {
        val sizePx = dpToPx(context, PLAY_BADGE_SIZE_DP).toInt()
        val marginPx = dpToPx(context, PLAY_BADGE_MARGIN_DP).toInt()
        val iconPaddingPx = dpToPx(context, PLAY_BADGE_ICON_PADDING_DP).toInt()
        return ImageView(context).apply {
            isFocusable = false
            isClickable = false
            background = ContextCompat.getDrawable(context, R.drawable.play_badge_bg)
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_hero_play))
            setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = marginPx
                bottomMargin = marginPx
            }
        }
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
