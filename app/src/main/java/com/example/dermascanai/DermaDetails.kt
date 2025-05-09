package com.example.dermascanai

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dermascanai.databinding.ActivityDermaDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DermaDetails : AppCompatActivity() {
    private lateinit var binding: ActivityDermaDetailsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var mAuth: FirebaseAuth
    private lateinit var dermaRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDermaDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        mAuth = FirebaseAuth.getInstance()

        val userEmail = intent.getStringExtra("userEmail")
        if (userEmail == null) {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.backBTN.setOnClickListener {
            finish()
        }



        fetchUserData(userEmail)

        binding.appointmentBtn.setOnClickListener {
            val intent = Intent(this, Booking::class.java)
            intent.putExtra("doctorEmail", userEmail)
            startActivity(intent)
        }
        binding.rateMe.setOnClickListener {
            userEmail?.let { showRatingDialog(it) }
        }
    }

    private fun fetchUserData(userEmail: String) {
        dermaRef = database.getReference("dermaInfo")
        val query = dermaRef.orderByChild("email").equalTo(userEmail)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (childSnapshot in snapshot.children) {
                        val dermaInfo = childSnapshot.getValue(DermaInfo::class.java)
                        val address = "${dermaInfo?.barangay}, ${dermaInfo?.city}, ${dermaInfo?.province}"
                        if (dermaInfo != null) {
                            binding.textView19.text = dermaInfo.name
                            binding.textView28.text = address
                            binding.bio.text = dermaInfo.bio

                            if (dermaInfo.status.equals("verified", ignoreCase = true)) {
                                binding.textView20.visibility = View.VISIBLE
                                binding.phone.visibility = View.VISIBLE
                                binding.fbAccnt.visibility = View.VISIBLE
                                binding.imageView4.visibility = View.VISIBLE
                                binding.gmail.visibility = View.VISIBLE
                                binding.rateMe.visibility = View.VISIBLE
                                binding.appointmentBtn.visibility = View.VISIBLE
                                binding.textView28.visibility = View.VISIBLE
                                binding.text.visibility = View.GONE
                            } else {
                                binding.textView20.visibility = View.GONE
                                binding.phone.visibility = View.GONE
                                binding.fbAccnt.visibility = View.GONE
                                binding.imageView4.visibility = View.GONE
                                binding.gmail.visibility = View.GONE
                                binding.rateMe.visibility = View.GONE
                                binding.appointmentBtn.visibility = View.GONE
                                binding.textView28.visibility = View.VISIBLE
                                binding.text.visibility = View.VISIBLE
                            }

                            dermaInfo.profileImage?.let {
                                if (it.isNotEmpty()) {
                                    try {
                                        val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                        binding.profile.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@DermaDetails, "No matching derma found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DermaDetails, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRatingDialog(doctorEmail: String) {
        val dialogBinding = com.example.dermascanai.databinding.DialogRateDoctorBinding.inflate(layoutInflater)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rate Doctor")
            .setView(dialogBinding.root)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val submitBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            submitBtn.setOnClickListener {
                val rating = dialogBinding.ratingBar.rating.toInt()
                val feedback = dialogBinding.feedbackInput.text.toString().trim()

                if (rating == 0) {
                    Toast.makeText(this, "Please select a rating.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val userId = mAuth.currentUser?.uid ?: return@setOnClickListener
                val userRef = database.getReference("userInfo").child(userId)

                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val userName = snapshot.child("name").getValue(String::class.java) ?: "Anonymous"

                        val ratingData = mapOf(
                            "rating" to rating,
                            "feedback" to feedback,
                            "userId" to userId,
                            "userName" to userName,
                            "timestamp" to System.currentTimeMillis()
                        )

                        val safeDoctorKey = doctorEmail.replace(".", "_")
                        val doctorRatingsRef = database.getReference("ratings").child(safeDoctorKey)

                        doctorRatingsRef.push().setValue(ratingData).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this@DermaDetails, "Rating submitted!", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this@DermaDetails, "Failed to submit rating", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@DermaDetails, "Error fetching user name", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        dialog.show()
    }


}
