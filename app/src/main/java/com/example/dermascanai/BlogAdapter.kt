package com.example.dermascanai

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ItemBlogPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BlogAdapter(
    private val context: Context,
    private val blogList: List<BlogPost>,
    private val currentUserId: String,
    private val currentUserProfilePic: String,
    private var currentUserName: String
) : RecyclerView.Adapter<BlogAdapter.BlogViewHolder>() {

    private val firebaseInstance = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
    private val currentUser = FirebaseAuth.getInstance().currentUser?.uid

    inner class BlogViewHolder(val binding: ItemBlogPostBinding) : RecyclerView.ViewHolder(binding.root)

    init {
        currentUser?.let { uid ->
            firebaseInstance.getReference("userInfo").child(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val user = snapshot.getValue(UserInfo::class.java)
                    if (user != null && user.name != null) {
                        currentUserName = user.name!!
                    }
                }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogViewHolder {
        val binding = ItemBlogPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlogViewHolder, position: Int) {
        val post = blogList[position]
        val blogRef = firebaseInstance.getReference("blogPosts").child(post.postId)

        holder.binding.fullName.text = post.fullName
        holder.binding.textView8.text = post.content

        // Set profile image
        if (!post.profilePicBase64.isNullOrEmpty()) {
            val profileBytes = Base64.decode(post.profilePicBase64, Base64.DEFAULT)
            holder.binding.profilePic.setImageBitmap(BitmapFactory.decodeByteArray(profileBytes, 0, profileBytes.size))
        }

        // Handle image only
        if (!post.imageUrl.isNullOrEmpty()) {
            holder.binding.mediaCard.visibility = android.view.View.VISIBLE
            holder.binding.mediaPreview.visibility = android.view.View.VISIBLE

            val postBytes = Base64.decode(post.imageUrl, Base64.DEFAULT)
            holder.binding.mediaPreview.setImageBitmap(BitmapFactory.decodeByteArray(postBytes, 0, postBytes.size))
        } else {
            holder.binding.mediaCard.visibility = android.view.View.GONE
        }

        // Post options (delete/hide)
        holder.binding.option.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(view.context, view)
            popup.inflate(R.menu.post_options_menu)

            if (post.userId == currentUserId) {
                popup.menu.findItem(R.id.action_delete).isVisible = true
                popup.menu.findItem(R.id.action_hide).isVisible = false
            } else {
                popup.menu.findItem(R.id.action_delete).isVisible = false
                popup.menu.findItem(R.id.action_hide).isVisible = true
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        AlertDialog.Builder(view.context)
                            .setTitle("Delete Post")
                            .setMessage("Are you sure you want to delete this post?")
                            .setPositiveButton("Delete") { _, _ ->
                                firebaseInstance.getReference("blogPosts")
                                    .child(post.postId)
                                    .removeValue()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    R.id.action_hide -> {
                        AlertDialog.Builder(view.context)
                            .setTitle("Hide Post")
                            .setMessage("Are you sure you want to hide this post?")
                            .setPositiveButton("Hide") { _, _ ->
                                firebaseInstance.getReference("hiddenPosts")
                                    .child(currentUserId)
                                    .child(post.postId)
                                    .setValue(true)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Post hidden", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        // Likes and comments
        holder.binding.heartCount.text = post.likeCount.toString()
        holder.binding.commentSection.text = "${post.commentCount}"
        holder.binding.timestamp.text = getTimeAgo(post.timestamp)

        val comments = mutableListOf<Comment>()
        val commentAdapter = AdapterComment(context, comments)

        holder.binding.commentContainer.setOnClickListener {
            val intent = android.content.Intent(context, BlogView::class.java).apply {
                putExtra("postId", post.postId)
                putExtra("fullName", post.fullName)
                putExtra("content", post.content)
            }
            context.startActivity(intent)
        }

        // Likes logic
        blogRef.child("likes").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userLiked = snapshot.hasChild(currentUserId)
                holder.binding.heart.setImageResource(
                    if (userLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        holder.binding.heart.setOnClickListener {
            blogRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val blog = mutableData.getValue(BlogPost::class.java) ?: return Transaction.success(mutableData)
                    if (blog.likes == null) blog.likes = mutableMapOf()

                    val liked = blog.likes.containsKey(currentUserId)
                    if (liked) {
                        blog.likes.remove(currentUserId)
                        blog.likeCount = (blog.likeCount - 1).coerceAtLeast(0)
                    } else {
                        blog.likes[currentUserId] = true
                        blog.likeCount += 1
                    }

                    mutableData.value = blog
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (committed) {
                        val updated = currentData?.getValue(BlogPost::class.java)
                        updated?.let {
                            holder.binding.heart.setImageResource(
                                if (it.likes?.containsKey(currentUserId) == true)
                                    R.drawable.ic_heart_filled
                                else
                                    R.drawable.ic_heart_outline
                            )
                            holder.binding.heartCount.text = it.likeCount.toString()
                        }
                    }
                }
            })
        }

        // Load comments
        firebaseInstance.getReference("comments").child(post.postId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    comments.clear()
                    for (commentSnap in snapshot.children) {
                        val comment = commentSnap.getValue(Comment::class.java)
                        comment?.let { comments.add(it) }
                    }
                    comments.sortByDescending { it.timestamp }
                    commentAdapter.notifyDataSetChanged()
                    holder.binding.commentSection.text = "${comments.size}"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun getTimeAgo(time: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> android.icu.text.SimpleDateFormat("MMM dd").format(java.util.Date(time))
        }
    }

    override fun getItemCount(): Int = blogList.size
}
