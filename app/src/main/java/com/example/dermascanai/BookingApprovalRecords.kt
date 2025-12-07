package com.example.dermascanai

import android.os.Bundle
import android.view.View
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dermascanai.databinding.ActivityBookingRecordsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class BookingApprovalRecords : AppCompatActivity() {
    private lateinit var binding: ActivityBookingRecordsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: BookingApprovalAdapter
    private val appointmentList = mutableListOf<BookingData>()
    private var clinicName: String = ""
    private var currentUserEmail: String = ""
    private var selectedDate: Calendar = Calendar.getInstance()
    private lateinit var clinicId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
        auth = FirebaseAuth.getInstance()

        currentUserEmail = auth.currentUser?.email ?: ""
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchClinicInfo()

        setupRecyclerView()

        binding.backBTN.setOnClickListener {
            finish()
        }
        clinicId = auth.currentUser?.uid ?: ""

        val chipStatusMap = mapOf(
            binding.pendingFilterChip to "pending",
            binding.approvedFilterChip to "confirmed",
            binding.completedFilterChip to "completed",
            binding.declinedFilterChip to "declined",
            binding.cancelledFilterChip to "cancelled",
            binding.allFilterChip to null
        )

        chipStatusMap.forEach { (chip, status) ->
            chip.setOnClickListener {
                chipStatusMap.keys.forEach { it.isChecked = false }
                chip.isChecked = true
                loadAppointments(status)
            }
        }

        updateDateDisplay()

        binding.prevDateBtn.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1)
            updateDateDisplay()
            refreshCurrentView()
        }

        binding.nextDateBtn.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1)
            updateDateDisplay()
            refreshCurrentView()
        }
    }
    


    private fun updateDateDisplay() {
        val sdf = java.text.SimpleDateFormat("MMM dd - EEE", Locale.getDefault())
        binding.currentDateText.text = sdf.format(selectedDate.time)
    }


    private fun fetchClinicInfo() {
        val clinicInfoRef = database.getReference("clinicInfo")

        clinicInfoRef.orderByChild("email").equalTo(currentUserEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (childSnapshot in snapshot.children) {
                            val clinicInfo = childSnapshot.getValue(ClinicInfo::class.java)
                            val clinicId = childSnapshot.key ?: ""

                            if (clinicInfo != null) {
                                clinicName = clinicInfo.clinicName ?: ""
                                Log.d("BookingApprovalRecords", "Found clinicId: $clinicId, clinicName: $clinicName")

                                val openApproved = intent.getBooleanExtra("openApprovedTab", false)
                                if (openApproved) {
                                    binding.approvedFilterChip.performClick()
                                } else {
                                    binding.pendingFilterChip.performClick()
                                }
                                return
                            }
                        }
                    } else {
                        Log.w("BookingApprovalRecords", "No clinic found for user: $currentUserEmail")
                        Toast.makeText(this@BookingApprovalRecords, "Could not find clinic information", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BookingApprovalRecords", "Database error: ${error.message}")
                }
            })
    }


    private fun setupRecyclerView() {
        adapter = BookingApprovalAdapter(
            appointmentList,
            onApprove = { booking -> updateBookingStatus(booking, "confirmed") },
            onDecline = { booking -> showDeclineReasonDialog(booking) },
            onCancel = { booking -> showCancellationReasonDialog(booking) },
            onDone = { booking -> markBookingAsCompleted(booking) }
        )
        binding.bookingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookingRecyclerView.adapter = adapter
    }


    private fun loadAppointments(status: String? = null) {
        if (clinicId.isEmpty()) return

        binding.progressBar.visibility = View.VISIBLE
        appointmentList.clear()

        val bookingsRef = database.getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        val query = status?.let { bookingsRef.orderByChild("status").equalTo(it) } ?: bookingsRef

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) {
                        val booking = bookingSnapshot.getValue(BookingData::class.java)
                        booking?.let {
                            appointmentList.add(it)
                            Log.d("BookingApprovalRecords", "Loaded booking: ${it.bookingId}, Status: ${it.status}")
                        }
                    }
                    appointmentList.sortByDescending { it.timestampMillis }
                }

                Log.d("BookingApprovalRecords", "Total appointments loaded: ${appointmentList.size}")
                adapter.notifyDataSetChanged()
                updateViewVisibility()
                binding.progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookingApprovalRecords, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
        })
    }


    private fun updateViewVisibility() {
        if (appointmentList.isEmpty()) {
            when {
                binding.pendingFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.declinedFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.VISIBLE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.approvedFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.VISIBLE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                binding.cancelledFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.VISIBLE
                }
                binding.completedFilterChip.isChecked -> {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
                else -> {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.emptyStateDeclinedLayout.visibility = View.GONE
                    binding.emptyStateApprovedLayout.visibility = View.GONE
                    binding.emptyStateCancelledLayout.visibility = View.GONE
                }
            }
            binding.bookingRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.emptyStateDeclinedLayout.visibility = View.GONE
            binding.emptyStateApprovedLayout.visibility = View.GONE
            binding.emptyStateCancelledLayout.visibility = View.GONE
            binding.bookingRecyclerView.visibility = View.VISIBLE
        }
    }


    private fun markBookingAsCompleted(booking: BookingData) {
        AlertDialog.Builder(this)
            .setTitle("Mark as Completed")
            .setMessage("Are you sure this appointment has been completed?")
            .setPositiveButton("Yes, Complete") { _, _ ->
                // First mark as completed
                updateBookingStatus(booking, "completed")

                // Then save to reports
                saveBookingToReports(booking)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun updateBookingStatus(booking: BookingData, newStatus: String) {
        if (clinicId.isEmpty()) {
            Toast.makeText(this, "Error: Clinic ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = booking.userId ?: return
        val bookingId = booking.bookingId ?: return

        binding.progressBar.visibility = View.VISIBLE

        val updates = HashMap<String, Any?>()

        updates["/bookings/$bookingId/status"] = newStatus
        updates["/clinicInfo/$clinicId/bookings/$bookingId/status"] = newStatus
        updates["/userInfo/$userId/bookings/$bookingId/status"] = newStatus

        // Add completion timestamp if marking as completed
        if (newStatus == "completed") {
            val completedTimestamp = System.currentTimeMillis()
            updates["/bookings/$bookingId/completedTimestamp"] = completedTimestamp
            updates["/clinicInfo/$clinicId/bookings/$bookingId/completedTimestamp"] = completedTimestamp
            updates["/userInfo/$userId/bookings/$bookingId/completedTimestamp"] = completedTimestamp
        }

        booking.declineReason?.takeIf { it.isNotEmpty() }?.let {
            updates["/bookings/$bookingId/declineReason"] = it
            updates["/clinicInfo/$clinicId/bookings/$bookingId/declineReason"] = it
            updates["/userInfo/$userId/bookings/$bookingId/declineReason"] = it
        }

        booking.cancellationReason?.takeIf { it.isNotEmpty() }?.let {
            updates["/bookings/$bookingId/cancellationReason"] = it
            updates["/clinicInfo/$clinicId/bookings/$bookingId/cancellationReason"] = it
            updates["/userInfo/$userId/bookings/$bookingId/cancellationReason"] = it
        }

        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                val statusMessage = when (newStatus) {
                    "confirmed" -> "approved"
                    "cancelled" -> "cancelled"
                    "completed" -> "completed"
                    else -> "declined"
                }

                if (newStatus == "confirmed") {
                    if (booking.date != null && booking.time != null) {
                        deductSlotOnApproval(clinicId, booking.date!!, booking.time!!)
                    }
                }

                Toast.makeText(this, "Appointment $statusMessage", Toast.LENGTH_SHORT).show()

                Log.d("BookingApprovalRecords", "Booking $bookingId status updated to: $newStatus")

                sendBookingNotification(
                    toUserId = userId,
                    fromUserId = clinicId,
                    status = statusMessage,
                    message = when (newStatus) {
                        "confirmed" -> "Your appointment has been approved"
                        "cancelled" -> "Your appointment has been cancelled"
                        "completed" -> "Your appointment has been completed"
                        else -> "Your appointment has been declined"
                    }
                )

                refreshCurrentView()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("BookingApprovalRecords", "Error updating booking status", e)
                binding.progressBar.visibility = View.GONE
            }
    }


    private fun deductSlotOnApproval(clinicId: String, date: String, time: String) {
        val slotRef = database.reference
            .child("clinicInfo")
            .child(clinicId)
            .child("schedule")
            .child(date)
            .child(time)

        slotRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                var slots = currentData.getValue(Int::class.java) ?: 5

                if (slots > 0) {
                    slots -= 1
                    currentData.value = slots
                }

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                Log.d("Slots", "Slot deducted after approval. Remaining: ${currentData?.value}")
            }
        })
    }

    private fun refreshCurrentView() {
        when {
            binding.pendingFilterChip.isChecked -> loadAppointments("pending")
            binding.approvedFilterChip.isChecked -> loadAppointments("confirmed")
            binding.completedFilterChip.isChecked -> loadAppointments("completed")
            binding.declinedFilterChip.isChecked -> loadAppointments("declined")
            binding.cancelledFilterChip.isChecked -> loadAppointments("cancelled")
            else -> loadAppointments()
        }
    }


    private fun showDeclineReasonDialog(booking: BookingData) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Decline Appointment")
        builder.setMessage("Please provide a reason for declining (optional):")

        val input = androidx.appcompat.widget.AppCompatEditText(this)
        builder.setView(input)

        builder.setPositiveButton("Decline") { _, _ ->
            val reason = input.text.toString().trim()
            if (reason.isNotEmpty()) {
                booking.declineReason = reason
            }
            updateBookingStatus(booking, "declined")
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }


    private fun showCancellationReasonDialog(booking: BookingData) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Cancel Appointment")
        builder.setMessage("Please provide a reason for cancellation:")

        val input = androidx.appcompat.widget.AppCompatEditText(this)
        builder.setView(input)

        builder.setPositiveButton("Cancel Appointment") { _, _ ->
            val reason = input.text.toString().trim()
            if (reason.isNotEmpty()) {
                booking.cancellationReason = reason
                updateBookingStatus(booking, "cancelled")
            } else {
                Toast.makeText(this, "Please provide a cancellation reason", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Back") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }


    private fun sendBookingNotification(
        toUserId: String,
        fromUserId: String,
        status: String,
        message: String
    ) {
        val database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
        val notificationId = database.child("notifications").child(toUserId).push().key ?: return
        val timestamp = System.currentTimeMillis()

        val notification = Notification(
            notificationId = notificationId,
            postId = "",
            fromUserId = fromUserId,
            toUserId = toUserId,
            type = "booking",
            message = message,
            timestamp = timestamp,
            isRead = false,
            status = status
        )

        database.child("notifications").child(toUserId).child(notificationId).setValue(notification)
    }

    private fun autoDeclineExpiredPendingBookings() {
        if (clinicId.isEmpty()) return

        val bookingsRef = database.getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookingSnapshot in snapshot.children) {
                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: "pending"
                    if (status.lowercase() == "pending") {
                        val dateStr = bookingSnapshot.child("date").getValue(String::class.java) ?: continue
                        val timeStr = bookingSnapshot.child("time").getValue(String::class.java) ?: "00:00"

                        try {
                            val dateTimeFormat = java.text.SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault())
                            val appointmentDateTime = dateTimeFormat.parse("$dateStr $timeStr") ?: continue

                            if (appointmentDateTime.before(Date())) {
                                val bookingId = bookingSnapshot.child("bookingId").getValue(String::class.java) ?: continue
                                val userId = bookingSnapshot.child("userId").getValue(String::class.java) ?: continue

                                val updates = mapOf<String, Any>(
                                    "status" to "declined",
                                    "autoDeclinedTimestamp" to System.currentTimeMillis()
                                )

                                val tasks = listOf(
                                    bookingsRef.child(bookingId).updateChildren(updates),
                                    database.reference.child("userInfo")
                                        .child(userId)
                                        .child("bookings")
                                        .child(bookingId)
                                        .updateChildren(updates)
                                )

                                com.google.android.gms.tasks.Tasks.whenAllSuccess<Void>(tasks)
                                    .addOnSuccessListener {
                                        Log.d("BookingApprovalRecords", "Pending booking $bookingId auto-declined (expired).")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("BookingApprovalRecords", "Failed to auto-decline booking $bookingId: ${e.message}")
                                    }
                            }
                        } catch (e: Exception) {
                            Log.e("BookingApprovalRecords", "Error parsing date/time for auto-decline: ${e.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BookingApprovalRecords", "Error checking expired bookings: ${error.message}")
            }
        })
    }

    private fun autoCancelMissedConfirmedBookings() {
        if (clinicId.isEmpty()) return

        val bookingsRef = database.getReference("clinicInfo")
            .child(clinicId)
            .child("bookings")

        bookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookingSnapshot in snapshot.children) {

                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: ""
                    if (status != "confirmed") continue

                    val bookingId = bookingSnapshot.child("bookingId").getValue(String::class.java) ?: continue
                    val userId = bookingSnapshot.child("userId").getValue(String::class.java) ?: continue
                    val dateStr = bookingSnapshot.child("date").getValue(String::class.java) ?: continue
                    val timeStr = bookingSnapshot.child("time").getValue(String::class.java) ?: "00:00"

                    try {
                        val dateTimeFormat = java.text.SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault())
                        val appointmentDateTime = dateTimeFormat.parse("$dateStr $timeStr") ?: continue

                        if (appointmentDateTime.before(Date())) {

                            val updates = mapOf<String, Any>(
                                "status" to "cancelled",
                                "cancellationReason" to "Patient did not come to the clinic",
                                "autoCancelledTimestamp" to System.currentTimeMillis()
                            )

                            bookingsRef.child(bookingId).updateChildren(updates)

                            database.reference.child("userInfo")
                                .child(userId)
                                .child("bookings")
                                .child(bookingId)
                                .updateChildren(updates)

                            Log.d("AutoCancel", "Confirmed booking $bookingId auto-cancelled because patient did not come.")
                        }

                    } catch (e: Exception) {
                        Log.e("AutoCancel", "Error parsing date for cancellation: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AutoCancel", "Error checking confirmed bookings: ${error.message}")
            }
        })
    }


    override fun onResume() {
        super.onResume()
        autoDeclineExpiredPendingBookings()
        autoCancelMissedConfirmedBookings()
        refreshCurrentView()
    }

    private fun saveBookingToReports(booking: BookingData) {
        val usersRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("userInfo")

        usersRef.orderByChild("email").equalTo(booking.patientEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var patientName = "Unknown"

                    for (userSnap in snapshot.children) {
                        patientName = userSnap.child("name").getValue(String::class.java) ?: "Unknown"
                    }

                    val reportRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
                        .child("reports")
                        .push()

                    val reportData = mapOf(
                        "reportId" to reportRef.key,
                        "bookingId" to booking.bookingId,
                        "patientName" to patientName,
                        "patientEmail" to booking.patientEmail,
                        "userId" to booking.userId,
                        "clinicName" to booking.clinicName,
                        "doctorEmail" to booking.doctorEmail,
                        "service" to booking.service,
                        "bookedDate" to booking.date,
                        "bookedTime" to booking.time,
                        "message" to booking.message,
                        "status" to "completed",
                        "createdAt" to booking.createdAt,
                        "completedAt" to System.currentTimeMillis()
                    )

                    reportRef.setValue(reportData)
                        .addOnSuccessListener {
                            Log.d("BookingApprovalRecords", "Report saved successfully for booking: ${booking.bookingId}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("BookingApprovalRecords", "Error saving report: ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BookingApprovalRecords", "Failed fetching user name: ${error.message}")
                }
            })
    }
}