package io.github.rt993.firetvjellyfin.ui.home

import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val CARD_CORNER_RADIUS_DP = 14
private const val FOCUS_SCALE = 1.08f
private const val FOCUS_ELEVATION_DP = 18
private const val FOCUS_IN_ANIM_MS = 220L
private const val FOCUS_OUT_ANIM_MS = 150L
private const val FOCUS_OVERSHOOT_TENSION = 2.5f

/**
 * A landscape thumbnail card with a watch-progress bar, for the "Pick up where you left off" row.
 * Movies and episodes are mixed in that row, so unlike [CardPresenter]'s portrait posters (which
 * would put movies and episode stills at two different aspect ratios side by side), every card
 * here is the same 16:9 shape - a movie's own [ImageType.BACKDROP] for movies, and an episode's
 * still (its normal [ImageType.PRIMARY]) for episodes.
 */
class ContinueWatchingPresenter(private val repository: JellyfinRepository) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_continue_watching_card, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        clipRoundedCorners(view.findViewById(R.id.cw_thumb_frame))

        val focusElevationPx = dpToPx(view, FOCUS_ELEVATION_DP)
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                .translationZ(if (hasFocus) focusElevationPx else 0f)
                .setDuration(if (hasFocus) FOCUS_IN_ANIM_MS else FOCUS_OUT_ANIM_MS)
                .setInterpolator(if (hasFocus) OvershootInterpolator(FOCUS_OVERSHOOT_TENSION) else DecelerateInterpolator())
                .start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        val baseItem = item as? BaseItemDto ?: return
        val thumb = view.findViewById<ImageView>(R.id.cw_thumb)
        val title = view.findViewById<TextView>(R.id.cw_title)
        val subtitle = view.findViewById<TextView>(R.id.cw_subtitle)
        val progressFill = view.findViewById<View>(R.id.cw_progress_fill)
        val progressTrack = view.findViewById<View>(R.id.cw_progress_track)

        val isEpisode = baseItem.type == BaseItemKind.EPISODE
        if (isEpisode) {
            title.text = baseItem.seriesName ?: baseItem.name
            val season = baseItem.parentIndexNumber
            val episode = baseItem.indexNumber
            subtitle.text = if (season != null && episode != null) {
                view.context.getString(R.string.continue_watching_episode_format, season, episode)
            } else {
                baseItem.name.orEmpty()
            }
        } else {
            title.text = baseItem.name
            subtitle.text = baseItem.productionYear?.toString().orEmpty()
        }

        val imageType = if (isEpisode) ImageType.PRIMARY else ImageType.BACKDROP
        Glide.with(thumb.context)
            .load(repository.buildImageUrl(baseItem.id, imageType = imageType, maxWidth = 480))
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_placeholder)
            .into(thumb)

        val positionTicks = baseItem.userData?.playbackPositionTicks ?: 0L
        val totalTicks = baseItem.runTimeTicks ?: 0L
        val percent = if (totalTicks > 0) (positionTicks.toFloat() / totalTicks).coerceIn(0f, 1f) else 0f
        progressTrack.post {
            progressFill.layoutParams = progressFill.layoutParams.apply {
                width = (progressTrack.width * percent).toInt()
            }
            progressFill.requestLayout()
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val thumb = viewHolder.view.findViewById<ImageView>(R.id.cw_thumb)
        // See CardPresenter.onUnbindViewHolder - clearing a load for a view being torn down after
        // the Activity is already destroyed throws, so this failure is expected and safe to ignore.
        runCatching { Glide.with(thumb.context).clear(thumb) }
    }

    private fun clipRoundedCorners(view: View) {
        val radiusPx = dpToPx(view, CARD_CORNER_RADIUS_DP)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }

    private fun dpToPx(view: View, dp: Int): Float = dp * view.resources.displayMetrics.density
}
