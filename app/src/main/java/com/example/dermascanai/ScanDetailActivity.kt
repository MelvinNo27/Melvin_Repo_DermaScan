package com.example.dermascanai

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dermascanai.databinding.ActivityScanDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ScanDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanDetailBinding
    private lateinit var databaseRef: DatabaseReference
    private lateinit var reportRef: DatabaseReference
    private lateinit var userRef: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    private var userId: String? = null
    private var scanId: String? = null
    private var currentImageBase64: String? = null
    private var currentUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        val db = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        userRef = db.getReference("clinicInfo").child(currentUser.uid)
        reportRef = db.getReference("scanReports")

        userId = intent.getStringExtra("userId")
        scanId = intent.getStringExtra("scanId")

        if (userId.isNullOrEmpty() || scanId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid scan data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        databaseRef = db.getReference("scanResults").child(userId!!).child(scanId!!)

        fetchScanDetails()

        // hide button initially
        binding.button.visibility = android.view.View.GONE

        // Check if the current user is a derma
        userRef.get().addOnSuccessListener { snapshot ->
            val role = snapshot.child("role").getValue(String::class.java)
            val clinicName = snapshot.child("name").getValue(String::class.java)
            currentUserName = clinicName

            if (role == "derma") {
                binding.button.visibility = android.view.View.VISIBLE
                checkIfAlreadyReported()
            }
        }

        binding.button.setOnClickListener {
            showReportDialog()
        }
    }

    private fun fetchScanDetails() {
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val condition = snapshot.child("condition").getValue(String::class.java) ?: "Unknown"
                    val remedy = snapshot.child("remedy").getValue(String::class.java) ?: "Not available"
                    val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: "Unknown"
                    val imageBase64 = snapshot.child("imageBase64").getValue(String::class.java)

                    binding.textViewConditionDetail.text = "Condition: $condition"
                    binding.textViewRemedyDetail.text = "Remedy: $remedy"
                    binding.textViewTimestampDetail.text = "Timestamp: $timestamp"

                    currentImageBase64 = imageBase64

                    imageBase64?.let {
                        try {
                            val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            binding.imageViewDetail.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.imageViewDetail.setImageResource(R.drawable.ic_scan)
                        }
                    } ?: run {
                        binding.imageViewDetail.setImageResource(R.drawable.ic_scan)
                    }
                } else {
                    Toast.makeText(this@ScanDetailActivity, "Scan data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ScanDetailActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showReportDialog() {
        val editText = EditText(this)
        editText.hint = "Write your report or comment..."
        editText.setLines(4)
        editText.isSingleLine = false

        AlertDialog.Builder(this)
            .setTitle("Submit Report")
            .setView(editText)
            .setPositiveButton("Submit") { dialog, _ ->
                val message = editText.text.toString().trim()
                if (message.isEmpty()) {
                    Toast.makeText(this, "Please write a message", Toast.LENGTH_SHORT).show()
                } else {
                    saveReportToFirebase(message)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun saveReportToFirebase(message: String) {
        val currentUser = mAuth.currentUser ?: return

        // Include full scan result in the report
        val scanData = mapOf(
            "condition" to binding.textViewConditionDetail.text.toString().replace("Condition: ", ""),
            "remedy" to binding.textViewRemedyDetail.text.toString().replace("Remedy: ", ""),
            "imageBase64" to currentImageBase64,
            "timestamp" to binding.textViewTimestampDetail.text.toString().replace("Timestamp: ", "")
        )

        val reportData = mapOf(
            "scanResult" to scanData,
            "message" to message,
            "userId" to currentUser.uid,
            "userName" to (currentUserName ?: "Unknown Derma"),
            "reportTimestamp" to System.currentTimeMillis()
        )

        val newReportKey = reportRef.push().key ?: return

        reportRef.child(newReportKey).setValue(reportData)
            .addOnSuccessListener {
                binding.button.isEnabled = false
                binding.button.text = "Reported"
                Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun checkIfAlreadyReported() {
        val currentUser = mAuth.currentUser ?: return
        reportRef.orderByChild("userId").equalTo(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var alreadyReported = false
                    for (child in snapshot.children) {
                        val image = child.child("imageBase64").getValue(String::class.java)
                        if (image == currentImageBase64) {
                            alreadyReported = true
                            break
                        }
                    }

                    if (alreadyReported) {
                        binding.button.isEnabled = false
                        binding.button.text = "Reported"
                    } else {
                        binding.button.isEnabled = true
                        binding.button.text = "Report this Scan Results?"
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
