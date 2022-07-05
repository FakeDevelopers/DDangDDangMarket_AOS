package com.fakedevelopers.bidderbidder.ui.product_registration.album_list

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.fakedevelopers.bidderbidder.R
import com.fakedevelopers.bidderbidder.databinding.RecyclerAlbumPagerBinding

class AlbumPagerAdapter(
    private val sendErrorToast: () -> Unit,
    private val setSelectedImageList: (String) -> Unit
) : ListAdapter<String, AlbumPagerAdapter.ViewHolder>(diffUtil) {

    inner class ViewHolder(
        private val binding: RecyclerAlbumPagerBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {
        private var isErrorImage = false
        fun bind(item: String) {
            Glide.with(context)
                .load(item)
                .placeholder(R.drawable.the_cat)
                .error(R.drawable.error_cat)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        isErrorImage = true
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        isErrorImage = false
                        return false
                    }
                })
                .into(binding.imageviewAlbumPager)
            binding.layoutAlbumPager.setOnClickListener {
                if (!isErrorImage) {
                    setSelectedImageList(item)
                } else {
                    sendErrorToast()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            RecyclerAlbumPagerBinding.bind(
                LayoutInflater.from(parent.context).inflate(R.layout.recycler_album_pager, parent, false)
            ),
            parent.context
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }
}
