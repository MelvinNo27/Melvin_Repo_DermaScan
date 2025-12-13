package com.example.dermascanai

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityBlogBinding
import com.example.dermascanai.databinding.DialogWritePostBinding
import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class BlogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlogBinding
    private lateinit var blogAdapter: BlogAdapter
    private val blogList = mutableListOf<BlogPost>()

    private lateinit var notificationBinding: LayoutNotificationPopupBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()

    private val auth = FirebaseAuth.getInstance()
    private val userRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("userInfo")
    private val blogRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("blogPosts")
    private val notificationRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("notifications")
    private val clinicRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("clinicInfo")

    private lateinit var notificationListener: ChildEventListener
    private var currentFullName: String = ""
    private var currentImageBase64: String = ""
    private var notificationListenerStartTime: Long = 0
    private var loadingDialog: AlertDialog? = null

    private var currentClinicName: String = ""
    private var currentClinicAddress: String = ""
    private var currentClinicContact: String = ""

    private enum class FeedMode { TRENDING, NEW }
    private var currentFeedMode = FeedMode.NEW

    // Media selection variables
    private var selectedImageUri: Uri? = null
    private var selectedImageBase64: String? = null
    private var tempDialogBinding: DialogWritePostBinding? = null

    // Activity Result Launcher for images
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImagePreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showLoading()
        binding = ActivityBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadCurrentUserInfo()
        setupRecyclerView()
        fetchBlogPosts()
        listenForNotifications()

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView

        val headerView = navView.getHeaderView(0)
        val closeDrawerBtn = headerView.findViewById<ImageView>(R.id.closeDrawerBtn)

        notificationBinding = LayoutNotificationPopupBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            notificationBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val currentUserId = auth.currentUser?.uid
        val notifRecyclerView = notificationBinding.notificationRecyclerView
        notifRecyclerView.layoutManager = LinearLayoutManager(this)

        notificationAdapter = NotificationAdapter(this, notificationList)
        notifRecyclerView.adapter = notificationAdapter

        val userNotificationsRef = notificationRef.child(currentUserId!!)
        userNotificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notificationList.clear()
                var hasUnread = false
                for (notifSnapshot in snapshot.children) {
                    val notif = notifSnapshot.getValue(Notification::class.java)
                    notif?.let {
                        notificationList.add(it)
                        if (!it.isRead) hasUnread = true
                    }
                }
                notificationList.sortByDescending { it.timestamp }
                notificationAdapter.notifyDataSetChanged()
                binding.notificationDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BlogActivity, "Failed to load notifications", Toast.LENGTH_SHORT).show()
            }
        })

        binding.menuIcon.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }
        closeDrawerBtn.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.END) }

        binding.notif.setOnClickListener {
            popupWindow.showAsDropDown(binding.notif, -100, 20)
            binding.notificationDot.visibility = View.GONE
            userNotificationsRef.get().addOnSuccessListener { snapshot ->
                for (notifSnapshot in snapshot.children) {
                    notifSnapshot.ref.child("isRead").setValue(true)
                }
            }
        }

        binding.post.setOnClickListener { showBottomSheetDialog() }
        binding.addPhotoBtn.setOnClickListener { showBottomSheetDialog() }

        binding.chipTrending.setOnClickListener {
            currentFeedMode = FeedMode.TRENDING
            highlightChip(binding.chipTrending, binding.chipNew)
            fetchBlogPosts()
        }

        binding.chipNew.setOnClickListener {
            currentFeedMode = FeedMode.NEW
            highlightChip(binding.chipNew, binding.chipTrending)
            fetchBlogPosts()
        }

        binding.derma.setOnClickListener { onBackPressed() }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_terms -> startActivity(Intent(this, TermsConditions::class.java))
                R.id.privacy -> startActivity(Intent(this, PrivacyPolicy::class.java))
                R.id.nav_logout -> logoutUser()
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun showLoading() {
        if (loadingDialog?.isShowing == true) return
        val view = layoutInflater.inflate(R.layout.dialog_loading, null)
        loadingDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun highlightChip(selected: View, unselected: View) {
        selected.alpha = 1f
        unselected.alpha = 0.6f
    }

    private fun showBottomSheetDialog() {
        val dialog = BottomSheetDialog(this)
        val bottomSheetBinding = DialogWritePostBinding.inflate(layoutInflater)
        dialog.setContentView(bottomSheetBinding.root)

        tempDialogBinding = bottomSheetBinding

        // Reset selection
        selectedImageUri = null
        selectedImageBase64 = null

        // Add Image Button
        bottomSheetBinding.btnAddImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Remove Media Button
        bottomSheetBinding.btnRemoveMedia.setOnClickListener {
            clearMediaPreview(bottomSheetBinding)
        }

        // Post Blog Button
        bottomSheetBinding.btnPostBlog.setOnClickListener {
            val content = bottomSheetBinding.etBlogContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "Please write something", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading()
            val postId = blogRef.push().key!!
            val timestamp = System.currentTimeMillis()
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener

            var mediaBase64: String? = null
            if (selectedImageUri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(selectedImageUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    mediaBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                } catch (e: Exception) {
                    hideLoading()
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val blogPost = BlogPost(
                postId = postId,
                userId = currentUserId,
                fullName = currentFullName,
                profilePicBase64 = currentImageBase64,
                content = content,
                imageUrl = mediaBase64,
                timestamp = timestamp,
                likeCount = 0,
                commentCount = 0,
                clinicName = currentClinicName,
                clinicAddress = currentClinicAddress,
                clinicContact = currentClinicContact
            )

            blogRef.child(postId).setValue(blogPost)
                .addOnSuccessListener {
                    Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    clearMediaPreview(bottomSheetBinding)
                }
                .addOnFailureListener {
                    hideLoading()
                    Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show()
                }

            // Save to profile
            val dbRef = FirebaseDatabase.getInstance().reference
            dbRef.child("userInfo").child(currentUserId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            dbRef.child("userInfo").child(currentUserId).child("blogPosts").child(postId).setValue(blogPost)
                        } else {
                            dbRef.child("clinicInfo").child(currentUserId).child("blogPosts").child(postId).setValue(blogPost)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        dialog.show()
    }

    private fun showImagePreview(uri: Uri) {
        tempDialogBinding?.let { binding ->
            binding.mediaPreviewCard.visibility = View.VISIBLE
            binding.ivBlogImage.visibility = View.VISIBLE
            binding.mediaTypeBadge.visibility = View.GONE

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivBlogImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearMediaPreview(binding: DialogWritePostBinding) {
        selectedImageUri = null
        selectedImageBase64 = null


        binding.mediaPreviewCard.visibility = View.GONE
        binding.ivBlogImage.visibility = View.GONE
        binding.vvBlogVideo.visibility = View.GONE
        binding.videoPlayOverlay.visibility = View.GONE
        binding.mediaTypeBadge.visibility = View.GONE

        binding.vvBlogVideo.stopPlayback()
    }

    private fun loadCurrentUserInfo() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            userRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val user = snapshot.getValue(UserInfo::class.java)
                        if (user != null) {
                            currentFullName = user.name ?: ""
                            currentImageBase64 = user.profileImage ?: ""
                            loadProfileImage(currentImageBase64)
                        }
                    } else {
                        clinicRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(clinicSnapshot: DataSnapshot) {
                                if (clinicSnapshot.exists()) {
                                    val clinic = clinicSnapshot.getValue(ClinicInfo::class.java)
                                    if (clinic != null) {
                                        currentFullName = clinic.clinicName ?: ""
                                        currentImageBase64 = clinic.logoImage ?: ""
                                        loadProfileImage(currentImageBase64)
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Toast.makeText(this@BlogActivity, "Failed to load clinic data", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BlogActivity, "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun loadProfileImage(base64: String) {
        if (base64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.profilePic.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BlogActivity, "Error loading image", Toast.LENGTH_SHORT).show()
                binding.profilePic.setImageResource(R.drawable.ic_profile2)
            }
        } else {
            binding.profilePic.setImageResource(R.drawable.ic_profile2)
        }
    }

    private fun setupRecyclerView() {
        val currentUserName = currentFullName

        blogAdapter = BlogAdapter(
            this,
            blogList,
            FirebaseAuth.getInstance().currentUser?.uid ?: "",
            currentImageBase64,
            currentUserName
        )

        binding.recyclerViewBlog.apply {
            layoutManager = LinearLayoutManager(this@BlogActivity)
            adapter = blogAdapter
        }
    }

    private fun fetchBlogPosts() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val hiddenRef = FirebaseDatabase.getInstance(
            "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("hiddenPosts").child(currentUserId)

        hiddenRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(hiddenSnap: DataSnapshot) {
                val hiddenIds = hiddenSnap.children.mapNotNull { it.key }.toSet()

                blogRef
                    .limitToLast(50) // load safely
                    .addListenerForSingleValueEvent(object : ValueEventListener {

                        override fun onDataChange(snapshot: DataSnapshot) {
                            blogList.clear()

                            val tempList = mutableListOf<BlogPost>()

                            for (postSnap in snapshot.children) {
                                val post = postSnap.getValue(BlogPost::class.java)
                                if (post != null && !hiddenIds.contains(post.postId)) {
                                    tempList.add(post)
                                }
                            }

                            when (currentFeedMode) {
                                FeedMode.NEW -> {
                                    tempList.sortByDescending { it.timestamp }
                                }

                                FeedMode.TRENDING -> {
                                    tempList.sortByDescending {
                                        (it.likeCount * 2) + it.commentCount
                                    }
                                }
                            }

                            blogList.addAll(tempList.take(20))
                            blogAdapter.notifyDataSetChanged()
                            hideLoading()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            hideLoading()
                        }
                    })
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoading()
            }
        })
    }



    private fun sendNotification(toUserId: String, postId: String, type: String, message: String) {
        val notificationId = notificationRef.push().key ?: return
        val fromUserId = auth.currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()

        val notification = Notification(
            notificationId = notificationId,
            postId = postId,
            fromUserId = fromUserId,
            toUserId = toUserId,
            type = type,
            message = message,
            timestamp = timestamp
        )

        notificationRef.child(notificationId).setValue(notification)
    }

    private fun logoutUser() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun listenForNotifications() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notifRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("notifications")
            .child(currentUserId)

        notificationListenerStartTime = System.currentTimeMillis()

        notificationListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notification = snapshot.getValue(Notification::class.java)
                if (notification != null && !notification.isRead && notification.timestamp > notificationListenerStartTime) {
                    playNotificationSound()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        notifRef.addChildEventListener(notificationListener)
    }

    private fun playNotificationSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.ding)
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
        mediaPlayer.start()
    }
}