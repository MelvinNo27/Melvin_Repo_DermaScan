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
import com.google.firebase.database.*

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
    private var patientName: String = ""
    private var clinicName: String = ""
    private var timestampMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app")
        auth = FirebaseAuth.getInstance()

        val emojiIcon = findViewById<ImageView>(R.id.emojiIcon)

        // ✅ Get values from intent (including time)
        selectedDate = intent.getStringExtra("selectedDate") ?: ""
        selectedTime = intent.getStringExtra("selectedTimeSlot") ?: "Not specified"
        selectedService = intent.getStringExtra("selectedService") ?: ""
        clinicName = intent.getStringExtra("clinicName") ?: ""
        timestampMillis = intent.getLongExtra("timestampMillis", System.currentTimeMillis())
        bookingId = intent.getStringExtra("bookingId") ?: System.currentTimeMillis().toString()

        // ✅ Get current user email
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

        emojiIcon.setOnClickListener { showEmojiPopup(it) }

        fetchUserData(clinicName)
        fetchPatientName()

        binding.backBTN.setOnClickListener { finish() }

        // ✅ Display date, time, and service on screen
        binding.date.text = selectedDate
        binding.timeSlot.text = selectedTime
        binding.serviceText.text = selectedService



        // ✅ Confirm button click
        binding.confirm.setOnClickListener {
            if (patientEmail.isEmpty()) {
                Toast.makeText(this, "You must be logged in to book an appointment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val messageText = binding.messageEditText.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkExistingBooking(selectedDate) { canBook ->
                if (canBook) {
                    bookWithSlotCheck()
                }
            }
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
                                        binding.time.text =
                                            "${dermaInfo.clinicOpenTime} - ${dermaInfo.clinicCloseTime} : ${dermaInfo.clinicOpenDay} - ${dermaInfo.clinicCloseDay}"
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

    private fun checkExistingBooking(selectedDate: String, callback: (Boolean) -> Unit) {
        val userBookingsRef = firebase.getReference("userBookings").child(patientEmail.replace(".", ","))

        userBookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasSameDayBooking = false

                for (bookingSnapshot in snapshot.children) {
                    val bookingData = bookingSnapshot.value as? HashMap<String, Any>
                    val status = bookingData?.get("status") as? String
                    val date = bookingData?.get("date") as? String

                    if (date == selectedDate && status != "cancelled") {
                        hasSameDayBooking = true
                        break
                    }
                }

                if (hasSameDayBooking) {
                    Toast.makeText(
                        this@ConfirmBooking,
                        "You already have a booking on this date. Please choose another day or cancel your existing booking.",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false)
                } else {
                    callback(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfirmBooking, "Error checking bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
        })
    }
    private fun getAvailableSlots(
        clinicId: String,
        date: String,
        time: String,
        callback: (Int) -> Unit
    ) {
        val scheduleRef = firebase.reference
            .child("clinicInfo")
            .child(clinicId)
            .child("schedule")
            .child(date)
            .child(time)

        scheduleRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                // If slot value missing → create default = 5
                val totalSlots = snapshot.getValue(Int::class.java) ?: 5

                // Now count booked slots
                val bookingsRef = firebase.reference.child("bookings")

                bookingsRef.orderByChild("clinicId").equalTo(clinicId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(bookingsSnap: DataSnapshot) {
                            var bookedCount = 0

                            for (booking in bookingsSnap.children) {
                                val bDate = booking.child("date").getValue(String::class.java)
                                val bTime = booking.child("time").getValue(String::class.java)
                                val status = booking.child("status").getValue(String::class.java)

                                if (bDate == date && bTime == time && status != "cancelled") {
                                    bookedCount++
                                }
                            }

                            val available = totalSlots - bookedCount
                            callback(available)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            callback(0)
                        }
                    })
            }

            override fun onCancelled(error: DatabaseError) {
                callback(0)
            }
        })
    }


    private fun fetchClinicId(clinicName: String, onResult: (String?) -> Unit) {
        val clinicsRef = firebase.getReference("clinicInfo")

        clinicsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var clinicId: String? = null
                for (clinicSnap in snapshot.children) {
                    val name = clinicSnap.child("name").getValue(String::class.java)
                    if (name == clinicName) {
                        clinicId = clinicSnap.key
                        break
                    }
                }
                onResult(clinicId)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null)
            }
        })
    }
    private fun bookWithSlotCheck() {
        fetchClinicId(clinicName) { clinicId ->
            if (clinicId == null) {
                Toast.makeText(this, "Clinic not found", Toast.LENGTH_SHORT).show()
                return@fetchClinicId
            }

            getAvailableSlots(clinicId, selectedDate, selectedTime) { availableSlots ->

                if (availableSlots <= 0) {
                    Toast.makeText(this, "No available slots at this time.", Toast.LENGTH_SHORT).show()
                    return@getAvailableSlots
                }

                saveBookingToFirebase(clinicId)
            }
        }
    }


    private fun fetchPatientName() {
        val userId = auth.currentUser?.uid ?: return
        val ref = firebase.getReference("userInfo").child(userId)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                patientName = snapshot.child("name").getValue(String::class.java) ?: ""
                Log.d("ConfirmBooking", "Patient name fetched: $patientName")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ConfirmBooking", "Error fetching patient name: ${error.message}")
            }
        })
    }


    // ✅ Modified to include selectedTime
    private fun saveBookingToFirebase(clinicId: String) {
        val messageText = binding.messageEditText.text.toString().trim()

        val bookingId = firebase.reference.push().key ?: System.currentTimeMillis().toString()
        val userId = auth.currentUser?.uid ?: patientEmail.replace(".", ",")

        val booking = HashMap<String, Any>()
        booking["bookingId"] = bookingId
        booking["userId"] = userId
        booking["patientEmail"] = patientEmail
        booking["clinicId"] = clinicId
        booking["patientName"] = patientName   // ✅ Added
        booking["clinicName"] = clinicName
        booking["date"] = selectedDate
        booking["time"] = selectedTime
        booking["service"] = selectedService
        booking["message"] = messageText
        booking["status"] = "pending"
        booking["timestampMillis"] = timestampMillis
        booking["createdAt"] = System.currentTimeMillis()

        // Save to Firebase under multiple paths
        val rootRef = firebase.reference
        val updates = hashMapOf<String, Any>(
            "/bookings/$bookingId" to booking,
            "/userInfo/$userId/bookings/$bookingId" to booking,
            "/clinicInfo/$clinicId/bookings/$bookingId" to booking,
            "/userBookings/${patientEmail.replace(".", ",")}/$bookingId" to booking
        )

        rootRef.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Booking confirmed successfully!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, UserPage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving booking: ${e.message}", Toast.LENGTH_SHORT).show()
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
