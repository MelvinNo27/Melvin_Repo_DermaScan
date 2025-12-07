    package com.example.dermascanai

    import android.app.AlertDialog
    import android.content.Intent
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import android.media.MediaPlayer
    import android.os.Bundle
    import android.util.Base64
    import android.view.View
    import android.view.ViewGroup
    import android.widget.ImageView
    import android.widget.PopupWindow
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.view.GravityCompat
    import androidx.recyclerview.widget.LinearLayoutManager
    import com.example.dermascanai.DermaPage
    import com.example.dermascanai.databinding.ActivityBlogBinding
    import com.example.dermascanai.databinding.DialogAddBlogBinding
    import com.example.dermascanai.databinding.LayoutNotificationPopupBinding
    import com.google.android.material.bottomsheet.BottomSheetDialog
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.database.*
    import java.io.ByteArrayOutputStream

    class BlogActivity : AppCompatActivity() {

        private lateinit var binding: ActivityBlogBinding
        private lateinit var blogAdapter: BlogAdapter
        private val blogList = mutableListOf<BlogPost>()

        private lateinit var notificationBinding: LayoutNotificationPopupBinding // Use ViewBinding here
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

        private var currentClinicName: String = ""
        private var currentClinicAddress: String = ""
        private var currentClinicContact: String = ""

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
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
            val notifications = mutableListOf<Notification>()


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
                            if (!it.isRead) {
                                hasUnread = true
                            }
                        }
                    }
                    notificationList.sortByDescending { it.timestamp }

                    notificationAdapter.notifyDataSetChanged()

                    binding.notificationDot.visibility = if (hasUnread) View.VISIBLE else View.GONE

                    if (hasUnread) {
                        binding.notificationDot.visibility = View.VISIBLE
                    } else {
                        binding.notificationDot.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BlogActivity, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
            })


            binding.menuIcon.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.END)
            }

            closeDrawerBtn.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
            }


            binding.notif.setOnClickListener {
                popupWindow.showAsDropDown(binding.notif, -100, 20)
                binding.notificationDot.visibility = View.GONE

                userNotificationsRef.get().addOnSuccessListener { snapshot ->
                    for (notifSnapshot in snapshot.children) {
                        notifSnapshot.ref.child("isRead").setValue(true)
                    }
                }
            }


            // When user clicks the 'Write Something...' area
            binding.post.setOnClickListener {
                showBottomSheetDialog()
            }

            binding.derma.setOnClickListener {
                onBackPressed()
            }

            navView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {

                    R.id.nav_terms -> {
                        val intent = Intent(this, TermsConditions::class.java)
                        startActivity(intent)
                    }
                    R.id.privacy -> {
                        val intent = Intent(this, PrivacyPolicy::class.java)
                        startActivity(intent)
                    }
                    R.id.nav_logout -> {
                        logoutUser()
                    }
                }
                drawerLayout.closeDrawers()
                true
            }

        }

        private fun showBottomSheetDialog() {
            val dialog = BottomSheetDialog(this)
            val bottomSheetBinding = DialogAddBlogBinding.inflate(layoutInflater)
            dialog.setContentView(bottomSheetBinding.root)



            // Choose Image
//            bottomSheetBinding.btnChooseImage.setOnClickListener {
//                val intent = Intent(Intent.ACTION_PICK)
//                intent.type = "image/*"
//                startActivityForResult(intent, IMAGE_PICK_CODE)
//                tempImageBinding = bottomSheetBinding
//            }

            // Post Blog
            bottomSheetBinding.btnPostBlog.setOnClickListener {
                val content = bottomSheetBinding.etBlogContent.text.toString().trim()

                if (content.isEmpty()) {
                    Toast.makeText(this, "Please write something", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val postId = blogRef.push().key!!
                val timestamp = System.currentTimeMillis()
                val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener

                val blogPost = BlogPost(
                    postId = postId,
                    userId = currentUserId,
                    fullName = currentFullName,
                    profilePicBase64 = currentImageBase64,
                    content = content,
                    postImageBase64 = selectedImageBase64,
                    timestamp = timestamp,
                    likeCount = 0,
                    commentCount = 0,
                    clinicName = currentClinicName,
                    clinicAddress = currentClinicAddress,
                    clinicContact = currentClinicContact
                )

                // 1️⃣ Save to main blogPosts node
                blogRef.child(postId).setValue(blogPost)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Blog posted!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show()
                    }

                val dbRef = FirebaseDatabase.getInstance(
                    "https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/"
                ).reference

                // 2️⃣ Check if current user is a regular user
                dbRef.child("userInfo").child(currentUserId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Current user is a regular user → save blog under userInfo
                                dbRef.child("userInfo").child(currentUserId).child("blogPosts").child(postId)
                                    .setValue(blogPost)
                            } else {
                                // Current user is a clinic → save blog under clinicInfo
                                dbRef.child("clinicInfo").child(currentUserId).child("blogPosts").child(postId)
                                    .setValue(blogPost)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(this@BlogActivity, "Failed to save blog in profile", Toast.LENGTH_SHORT).show()
                        }
                    })
            }


            dialog.show()
        }


        private fun loadCurrentUserInfo() {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                // 🔹 First, try fetching UserInfo
                userRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // ✅ User found
                            val user = snapshot.getValue(UserInfo::class.java)
                            if (user != null) {
                                currentFullName = user.name ?: ""
                                currentImageBase64 = user.profileImage ?: ""
                                loadProfileImage(currentImageBase64)
                            }
                        } else {
                            // ❌ Not a User → check Clinic
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

        // 🔹 Helper to decode and display profile image
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
            val hiddenRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("hiddenPosts")
                .child(currentUserId)

            hiddenRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(hiddenSnap: DataSnapshot) {
                    val hiddenIds = hiddenSnap.children.map { it.key!! }.toSet()

                    blogRef.orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            blogList.clear()
                            for (postSnapshot in snapshot.children) {
                                val blog = postSnapshot.getValue(BlogPost::class.java)
                                if (blog != null && !hiddenIds.contains(blog.postId)) {
                                    blogList.add(blog)
                                }
                            }
                            blogList.reverse() // newest first
                            blogAdapter.notifyDataSetChanged()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(this@BlogActivity, "Failed to fetch blog posts", Toast.LENGTH_SHORT).show()
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }


        private val IMAGE_PICK_CODE = 1000
        private var tempImageBinding: DialogAddBlogBinding? = null
        private var selectedImageBase64: String? = null

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK && data != null) {
                val uri = data.data
                val inputStream = contentResolver.openInputStream(uri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val imageBytes = outputStream.toByteArray()
                val encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                tempImageBinding?.ivBlogImage?.apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE
                }

                selectedImageBase64 = encodedImage
            }
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

//                        Toast.makeText(
//                            this@BlogActivity, // or your correct context
//                            notification.message,
//                            Toast.LENGTH_LONG
//                        ).show()
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
