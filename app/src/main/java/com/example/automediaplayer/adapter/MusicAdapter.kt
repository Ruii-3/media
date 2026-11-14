package com.example.automediaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.automediaplayer.R
import com.example.automediaplayer.model.MusicData
import com.google.android.material.card.MaterialCardView

/**
 * 歌曲列表适配器（第 2 周 UI 优化）。
 *
 * 在 ViewHolder 模式上增加：
 * - 当前播放曲目的紫色高亮与自动滚动；
 * - 每首歌独立的收藏按钮回调；
 * - 收藏状态由 Activity 传入，适配器不直接读写 SharedPreferences，保持单一数据源。
 */
class MusicAdapter(
    private var musicList: List<MusicData> = emptyList(),
    private val onItemClick: (MusicData) -> Unit,
    private val onFavoriteClick: (MusicData) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private var favoriteIds: Set<Long> = emptySet()
    private var selectedMusicId: Long? = null
    private var recyclerView: RecyclerView? = null

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_music_item)
        val selectedIndicator: View = itemView.findViewById(R.id.view_selected_indicator)
        val index: TextView = itemView.findViewById(R.id.tvIndex)
        val songTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtist)
        val album: TextView = itemView.findViewById(R.id.tvAlbum)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
        val favorite: ImageButton = itemView.findViewById(R.id.btnFavoriteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]
        val context = holder.itemView.context
        val isSelected = music.id == selectedMusicId

        holder.index.text = String.format("%02d", position + 1)
        holder.songTitle.text = music.title
        holder.artist.text = music.artist
        holder.album.text = music.album
        holder.duration.text = music.getFormattedDuration()

        holder.selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.card.strokeColor = ContextCompat.getColor(
            context,
            if (isSelected) R.color.card_stroke_selected else R.color.card_stroke
        )
        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(
                context,
                if (isSelected) R.color.item_selected_bg else R.color.white
            )
        )

        val favorite = favoriteIds.contains(music.id)
        holder.favorite.setImageResource(
            if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        holder.favorite.contentDescription =
            context.getString(if (favorite) R.string.unfavorite else R.string.favorite)

        holder.itemView.setOnClickListener { onItemClick(music) }
        holder.favorite.setOnClickListener { onFavoriteClick(music) }
    }

    override fun getItemCount(): Int = musicList.size

    /** 全量刷新列表；歌曲数量为个位数到几十首，暂不引入 DiffUtil。 */
    fun updateData(newMusicList: List<MusicData>) {
        musicList = newMusicList
        notifyDataSetChanged()
    }

    fun updateFavorites(ids: Set<Long>) {
        if (favoriteIds == ids) return
        favoriteIds = ids.toSet()
        notifyDataSetChanged()
    }

    /** 高亮当前播放曲目；索引可见时自动滚动到该行。 */
    fun setSelectedMusicId(musicId: Long?) {
        if (selectedMusicId == musicId) return
        selectedMusicId = musicId
        notifyDataSetChanged()
        val index = musicList.indexOfFirst { it.id == musicId }
        if (index >= 0) {
            recyclerView?.post { recyclerView?.smoothScrollToPosition(index) }
        }
    }

    fun attachRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    fun getMusicList(): List<MusicData> = musicList

    fun getItemAt(position: Int): MusicData? = musicList.getOrNull(position)

    fun indexOf(musicId: Long): Int = musicList.indexOfFirst { it.id == musicId }
}
