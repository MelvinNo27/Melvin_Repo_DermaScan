package com.example.dermascanai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityRatingViewBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class RatingView : AppCompatActivity() {
    private lateinit var binding: ActivityRatingViewBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var ratingsRef: DatabaseReference
    private val feedbackList = mutableListOf<RatingModel>()
    private lateinit var adapter: RatingsAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRatingViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        adapter = RatingsAdapter(feedbackList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val clinicId = intent.getStringExtra("clinicId") ?: return

        ratingsRef = database.getReference("ratings").child(clinicId)


        binding.backBtn.setOnClickListener {
            finish()
        }

        ratingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                feedbackList.clear()
                val usersRef = database.getReference("userInfo")

                val tempList = mutableListOf<RatingModel>()
                var remaining = snapshot.childrenCount

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val message = userSnapshot.child("message").getValue(String::class.java) ?: ""
                    val rating = userSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                    val timestamp = userSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    val ratingModel = RatingModel(rating, message, timestamp, userId)

                    usersRef.child(userId).child("name").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnap: DataSnapshot) {
                            ratingModel.userName = userSnap.getValue(String::class.java) ?: "Anonymous"
                            tempList.add(ratingModel)
                            remaining--

                            if (remaining == 0L) {
                                feedbackList.clear()
                                feedbackList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            remaining--
                            if (remaining == 0L) {
                                feedbackList.clear()
                                feedbackList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@RatingView, "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

    }
}