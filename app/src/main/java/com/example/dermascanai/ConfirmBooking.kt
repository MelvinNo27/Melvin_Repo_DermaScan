package com.example.dermascanai

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dermascanai.databinding.ActivityConfirmBookingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfirmBooking : AppCompatActivity() {
    private lateinit var binding: ActivityConfirmBookingBinding
    private lateinit var database: DatabaseReference
    private lateinit var firebase: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private val emojiList = listOf(
        "😊", "😂", "😍", "😢", "👍", "👋", "🙏", "🤝", "✌️", "👎",
        "❤️", "🤔", "🙄", "😎", "😡", "🤗", "👏", "🖐️", "✋", "🫶"
    )

    private var bookingId: String = ""
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var selectedService: String = ""
    private var patientEmail: String = ""
    private var clinicName: String = ""
    private var timestampMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        auth = FirebaseAuth.getInstance()

        val emojiIcon = findViewById<ImageView>(R.id.emojiIcon)

        // Get values from intent
        selectedDate = intent.getStringExtra("selectedDate") ?: ""
        selectedTime = intent.getStringExtra("selectedTime") ?: "Any available time"
        selectedService = intent.getStringExtra("selectedService") ?: ""
        clinicName = intent.getStringExtra("clinicName") ?: ""
        timestampMillis = intent.getLongExtra("timestampMillis", System.currentTimeMillis())
        bookingId = intent.getStringExtra("bookingId") ?: System.currentTimeMillis().toString()

        // Get current user email and set patient email
        val currentUserEmail = auth.currentUser?.email
        patientEmail = intent.getStringExtra("patientEmail") ?: ""
        if (patientEmail.isEmpty()) {
            patientEmail = currentUserEmail ?: ""
            Log.d("ConfirmBooking", "Using current user email: $patientEmail")
        }

        binding.messageEditText.hint = "Message from $patientEmail"

        if (clinicName.isEmpty()) {
            Toast.makeText(this, "Clinic name not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        emojiIcon.setOnClickListener {
            showEmojiPopup(it)
        }

        fetchUserData(clinicName)

        binding.backBTN.setOnClickListener {
            finish()
        }

        // Set selected date only (removed time display)
        binding.date.text = selectedDate

        // Add service display - make sure you have a TextView for this in your layout
        try {
            binding.serviceText.text = selectedService
        } catch (e: Exception) {
            Log.e("ConfirmBooking", "Error setting service text: ${e.message}")
        }

        binding.confirm.setOnClickListener {
            if (patientEmail.isEmpty()) {
                Toast.makeText(this, "You must be logged in to book an appointment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate that message is not empty
            val messageText = binding.messageEditText.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if user already has a booking before proceeding
            checkExistingBooking()
        }


    }

    private fun fetchUserData(clinicNameParam: String) {
        database = firebase.getReference("clinicInfo")
        val query = database.orderByChild("name").equalTo(clinicNameParam)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (childSnapshot in snapshot.children) {
                        val dermaInfo = childSnapshot.getValue(ClinicInfo::class.java)
                        if (dermaInfo != null) {
                            binding.ClinicName.text = dermaInfo.name
                            clinicName = dermaInfo.name ?: ""

                            dermaInfo.logoImage?.let {
                                if (it.isNotEmpty()) {
                                    try {
                                        val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                        binding.profPic.setImageBitmap(bitmap)
                                        binding.time.text = dermaInfo.clinicOpenTime +" - "+ dermaInfo.clinicCloseTime + " : " + dermaInfo.clinicOpenDay +" - "+ dermaInfo.clinicCloseDay
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@ConfirmBooking, "No matching clinic found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfirmBooking, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Check if the user already has an existing booking
     * Only allow one active booking per user (pending, confirmed status)
     */
    private fun checkExistingBooking() {
        val userBookingsRef = firebase.getReference("userBookings").child(patientEmail.replace(".", ","))

        userBookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasActiveBooking = false

                // Check all user's bookings
                for (bookingSnapshot in snapshot.children) {
                    val bookingData = bookingSnapshot.value as? HashMap<String, Any>
                    val status = bookingData?.get("status") as? String

                    // Consider booking as active if it's pending or confirmed
                    if (status == "pending" || status == "confirmed") {
                        hasActiveBooking = true
                        break
                    }
                }

                if (hasActiveBooking) {
                    Toast.makeText(
                        this@ConfirmBooking,
                        "You already have an active booking. Please complete or cancel your existing booking before making a new one.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // User doesn't have an active booking, proceed with new booking
                    saveBookingToFirebase()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfirmBooking, "Error checking existing bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("ConfirmBooking", "Error checking existing bookings", error.toException())
            }
        })
    }

    private fun saveBookingToFirebase() {
        val messageText = binding.messageEditText.text.toString().trim()

        // Validate message is not empty
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Get reference to bookings
        val bookingsRef = firebase.getReference("bookings")

        // Create a booking object
        val booking = HashMap<String, Any>()
        booking["bookingId"] = bookingId
        booking["patientEmail"] = patientEmail
        booking["clinicName"] = clinicName
        booking["date"] = selectedDate
        booking["service"] = selectedService
        booking["message"] = messageText  // Ensure message is saved
        booking["status"] = "pending" // pending, confirmed, cancelled, completed
        booking["timestampMillis"] = timestampMillis
        booking["createdAt"] = System.currentTimeMillis()

        Log.d("ConfirmBooking", "Saving booking with message: $messageText")

        // Save in Firebase
        bookingsRef.child(bookingId).setValue(booking)
            .addOnSuccessListener {
                Toast.makeText(this, "Booking confirmed successfully!", Toast.LENGTH_SHORT).show()

                val userBookingsRef = firebase.getReference("userBookings").child(patientEmail.replace(".", ","))
                userBookingsRef.child(bookingId).setValue(booking)

                // Use clinic name for doctor bookings reference instead of email
                val doctorBookingsRef = firebase.getReference("clinicBookings").child(clinicName.replace(" ", "_").replace(".", ","))
                doctorBookingsRef.child(bookingId).setValue(booking)

                val intent = Intent(this, UserPage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving booking: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ConfirmBooking", "Error saving booking", e)
            }
    }

    private fun showEmojiPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.emoji_popup, null)
        val gridView = popupView.findViewById<GridView>(R.id.emojiGrid)
        val popupWindow = PopupWindow(popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emojiList)
        gridView.adapter = adapter

        val messageEditText = findViewById<EditText>(R.id.messageEditText)

        gridView.setOnItemClickListener { _, _, position, _ ->
            val emoji = emojiList[position]
            messageEditText.append(emoji)
            popupWindow.dismiss()
        }

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(getDrawable(android.R.color.transparent))
        popupWindow.showAsDropDown(anchor, 0, -anchor.height * 4)
    }
}