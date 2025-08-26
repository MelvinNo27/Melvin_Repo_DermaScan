package com.example.dermascanai

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dermascanai.databinding.ItemReviewBinding
import com.google.android.libraries.places.api.model.Review
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Date

class ReviewsAdapter : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    private val items = mutableListOf<RatingModel>()

    fun submitList(newList: List<RatingModel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    inner class ReviewViewHolder(val binding: ItemReviewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = items[position]

        holder.binding.reviewMessage.text = review.message
        holder.binding.reviewRating.text = "⭐ ${review.rating}"
        holder.binding.reviewDate.text = Date(review.timestamp).toString()
        holder.binding.userName.text = review.userName

        // Profile image
        if (!review.reviewerPhoto.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(Base64.decode(review.reviewerPhoto, Base64.DEFAULT))
                .into(holder.binding.userImage)
        } else {
            holder.binding.userImage.setImageResource(R.drawable.ic_profile)
        }
    }

    override fun getItemCount() = items.size
}
