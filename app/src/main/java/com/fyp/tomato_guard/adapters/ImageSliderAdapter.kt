package com.fyp.tomato_guard.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.fyp.tomato_guard.R  // ✅ Correct app R reference

class ImageSliderAdapter(private val context: Context, private val images: IntArray) :
    RecyclerView.Adapter<ImageSliderAdapter.ViewHolder>() {

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            setBackgroundResource(R.drawable.rounded_image_bg)  // ✅ use your app's drawable
        }
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        (holder.itemView as ImageView).setImageResource(images[position])
    }

    override fun getItemCount(): Int {
        return images.size
    }

    class ViewHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView)
}
