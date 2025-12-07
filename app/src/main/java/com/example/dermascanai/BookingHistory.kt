package com.example.dermascanai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dermascanai.databinding.ActivityBookingHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class BookingHistory : AppCompatActivity() {
    private lateinit var binding: ActivityBookingHistoryBinding
    private lateinit var appointmentAdapter: AppointmentAdapter
    private val appointmentList = mutableListOf<Appointment>()
    private val filteredAppointmentList = mutableListOf<Appointment>()
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userBookingsRef: DatabaseReference
    private var userBookingsListener: ValueEventListener? = null
    private var currentFilter = "pending"
    private var isDatabaseInitialized = false
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeFirebase()
        setupRecyclerView()
        setupFilterChips()
        binding.backBtn.setOnClickListener {
            finish()
        }
        swipeRefreshLayout = binding.swipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            Log.d("BookingHistory", "Pull-to-refresh triggered")
            loadAppointments()
        }
        loadAppointments()
        setupAutoRefresh()
        binding.bookAppointmentBtn.setOnClickListener {
            startActivity(Intent(this, DoctorLists::class.java))
        }
    }

    private fun initializeFirebase() {
        if (!isDatabaseInitialized) {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            try {
                database.setPersistenceEnabled(true)
                Log.d("BookingHistory", "Firebase persistence enabled")
            } catch (e: Exception) {
                Log.e("BookingHistory", "Firebase persistence already set: ${e.message}")
            }
            isDatabaseInitialized = true
        }
    }

    private fun setupRecyclerView() {
        appointmentAdapter = AppointmentAdapter(
            appointments = filteredAppointmentList,
            onCardClicked = { appointment ->
                showAppointmentDetailsDialog(appointment)
            },
            onCancelClicked = { appointment ->
                showCancelConfirmationDialog(appointment)
            }
        )
        binding.appointmentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appointmentsRecyclerView.adapter = appointmentAdapter
    }

    private fun setupFilterChips() {
        binding.pendingFilterChip.isChecked = true
        binding.pendingFilterChip.setOnClickListener {
            currentFilter = "pending"
            applyFilter()
        }
        binding.declinedFilterChip.setOnClickListener {
            currentFilter = "declined"
            applyFilter()
        }
        binding.cancelledFilterChip.setOnClickListener {
            currentFilter = "cancelled"
            applyFilter()
        }

        binding.completedFilterChip.setOnClickListener {
            currentFilter = "completed"
            applyFilter()
        }
        binding.approvedFilterChip.setOnClickListener {
            currentFilter = "approved"
            applyFilter()
        }
        binding.allFilterChip.setOnClickListener {
            currentFilter = "all"
            applyFilter()
        }
    }

    private fun showAppointmentDetailsDialog(appointment: Appointment) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_appointment_details, null)

        // Initialize views
        val statusHeader = dialogView.findViewById<LinearLayout>(R.id.statusHeader)
        val statusIcon = dialogView.findViewById<ImageView>(R.id.statusIcon)
        val statusText = dialogView.findViewById<TextView>(R.id.textStatus)
        val bookingIdText = dialogView.findViewById<TextView>(R.id.textBookingId)
        val clinicNameText = dialogView.findViewById<TextView>(R.id.clinicNameTv)
        val dateText = dialogView.findViewById<TextView>(R.id.appointmentDateTv)
        val timeText = dialogView.findViewById<TextView>(R.id.textAppointmentTime)
        val timeContainer = dialogView.findViewById<LinearLayout>(R.id.timeContainer)
        val serviceText = dialogView.findViewById<TextView>(R.id.textService)
        val serviceContainer = dialogView.findViewById<LinearLayout>(R.id.serviceContainer)
        val messageText = dialogView.findViewById<TextView>(R.id.textMessage)
        val messageContainer = dialogView.findViewById<LinearLayout>(R.id.messageContainer)
        //val cancelReasonText = dialogView.findViewById<TextView>(R.id.textCancelReason)
        //val cancelReasonContainer = dialogView.findViewById<LinearLayout>(R.id.cancelReasonContainer)
        val timestampText = dialogView.findViewById<TextView>(R.id.textBookingTimestamp)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        // Set data
        bookingIdText.text = "#${appointment.bookingId.takeLast(5)}"
        clinicNameText.text = appointment.doctorName
        dateText.text = appointment.date

        // Time
        if (appointment.time.isNotEmpty()) {
            timeContainer.visibility = View.VISIBLE
            timeText.text = appointment.time
        } else {
            timeContainer.visibility = View.GONE
        }

        // Service
        if (appointment.service.isNotEmpty()) {
            serviceContainer.visibility = View.VISIBLE
            serviceText.text = appointment.service
        } else {
            serviceContainer.visibility = View.GONE
        }

        // Message
        if (appointment.message.isNotEmpty()) {
            messageContainer.visibility = View.VISIBLE
            messageText.text = appointment.message
        } else {
            messageContainer.visibility = View.GONE
        }

//        // Cancellation reason
//        if (appointment.status.lowercase() == "cancelled" && appointment.cancellationReason?.isNotEmpty() == true) {
//            cancelReasonContainer.visibility = View.VISIBLE
//            cancelReasonText.text = appointment.cancellationReason
//        } else {
//            cancelReasonContainer.visibility = View.GONE
//        }

        // Status
        val status = appointment.status.replaceFirstChar { it.uppercase() }
        statusText.text = status

        val statusIconRes = when (appointment.status.lowercase()) {
            "confirmed" -> android.R.drawable.ic_dialog_info
            "declined" -> android.R.drawable.ic_dialog_alert
            "cancelled" -> android.R.drawable.ic_menu_close_clear_cancel
            "completed" -> android.R.drawable.ic_dialog_info
            "ongoing" -> android.R.drawable.ic_dialog_info
            else -> android.R.drawable.ic_dialog_info
        }
        statusIcon.setImageResource(statusIconRes)

        val (backgroundColor, textColor) = when (appointment.status.lowercase()) {
            "confirmed" -> Pair(R.color.green, android.R.color.white)
            "declined" -> Pair(R.color.red, android.R.color.white)
            "cancelled" -> Pair(R.color.red, android.R.color.white)
            "completed" -> Pair(R.color.blue, android.R.color.white)
            "ongoing" -> Pair(R.color.green, android.R.color.white)
            else -> Pair(R.color.orange, android.R.color.white)
        }
        statusHeader.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        statusText.setTextColor(ContextCompat.getColor(this, textColor))

        // Timestamp
        val timestamp = if (appointment.createdAt > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            "Booked on ${dateFormat.format(Date(appointment.createdAt))}"
        } else {
            "Recently booked"
        }
        timestampText.text = timestamp

        // Cancel button visibility
        if (appointment.status.lowercase() == "pending" || appointment.status.lowercase() == "confirmed") {
            cancelButton.visibility = View.VISIBLE
        } else {
            cancelButton.visibility = View.GONE
        }

        // Create dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Button listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
            showCancelConfirmationDialog(appointment)
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyFilter() {
        filteredAppointmentList.clear()
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptyStateDeclinedLayout.visibility = View.GONE
        binding.emptyStateCancelledLayout.visibility = View.GONE
        binding.emptyStateApprovedLayout.visibility = View.GONE
        when (currentFilter) {
            "pending" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "pending"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                }
            }
            "declined" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "declined"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateDeclinedLayout.visibility = View.VISIBLE
                }
            }
            "cancelled" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "cancelled"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateCancelledLayout.visibility = View.VISIBLE
                }
            }
            "approved" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "confirmed"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateApprovedLayout.visibility = View.VISIBLE
                }
            }
            "completed" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "completed"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                }
            }
            "ongoing" -> {
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "ongoing"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                }
            }
            "all" -> {
                filteredAppointmentList.addAll(appointmentList)

                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                }
            }
        }
        appointmentAdapter.notifyDataSetChanged()
        Log.d("BookingHistory", "Applied filter: $currentFilter, showing ${filteredAppointmentList.size} appointments")
    }

    private fun loadAppointments() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "Please login to view your appointments", Toast.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        hideAllEmptyStates()
        val userEmail = currentUser.email?.replace(".", ",") ?: ""
        Log.d("BookingHistory", "Loading appointments for user: $userEmail")
        if (userBookingsListener != null && ::userBookingsRef.isInitialized) {
            userBookingsRef.removeEventListener(userBookingsListener!!)
        }
        val userId = currentUser.uid
        userBookingsRef = database.getReference("userInfo")
            .child(userId)
            .child("bookings")

        userBookingsRef.keepSynced(true)
        userBookingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) {
                        try {
                            val bookingId = bookingSnapshot.child("bookingId").getValue(String::class.java) ?: ""
                            val patientEmail = bookingSnapshot.child("patientEmail").getValue(String::class.java) ?: ""
                            val clinicName = bookingSnapshot.child("clinicName").getValue(String::class.java) ?: ""
                            val date = bookingSnapshot.child("date").getValue(String::class.java) ?: ""
                            val time = bookingSnapshot.child("time").getValue(String::class.java) ?: ""
                            val service = bookingSnapshot.child("service").getValue(String::class.java) ?: ""
                            val message = bookingSnapshot.child("message").getValue(String::class.java) ?: ""
                            val status = bookingSnapshot.child("status").getValue(String::class.java) ?: "pending"
                            val timestampMillis = bookingSnapshot.child("timestampMillis").getValue(Long::class.java) ?: 0L
                            val createdAt = bookingSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                            val cancellationReason = bookingSnapshot.child("cancellationReason").getValue(String::class.java) ?: ""
                            val appointment = Appointment(
                                bookingId = bookingId,
                                patientEmail = patientEmail,
                                doctorName = clinicName,
                                date = date,
                                time = time,
                                service = service,
                                message = message,
                                status = status,
                                timestampMillis = timestampMillis,
                                createdAt = createdAt,
                                cancellationReason = cancellationReason
                            )
                            appointmentList.add(appointment)
                            Log.d("BookingHistory", "Found appointment: $bookingId with clinic $clinicName, status: $status, message: $message")
                        } catch (e: Exception) {
                            Log.e("BookingHistory", "Error parsing appointment: ${e.message}")
                        }
                    }
                    appointmentList.sortByDescending { it.createdAt }
                } else {
                    Log.d("BookingHistory", "No appointments found for user: $userEmail")
                }
                applyFilter()
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookingHistory, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                Log.e("BookingHistory", "Database error: ${error.message}")
            }
        }
        userBookingsRef.addValueEventListener(userBookingsListener!!)
    }

    private fun hideAllEmptyStates() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptyStateDeclinedLayout.visibility = View.GONE
        binding.emptyStateCancelledLayout.visibility = View.GONE
        binding.emptyStateApprovedLayout.visibility = View.GONE
    }

    private fun showCancelConfirmationDialog(appointment: Appointment) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cancel_appointment, null)
        val reasonEditText = dialogView.findViewById<EditText>(R.id.editTextCancelReason)

        AlertDialog.Builder(this)
            .setTitle("Cancel Appointment")
            .setView(dialogView)
            .setMessage("Are you sure you want to cancel your appointment with ${appointment.doctorName} on ${appointment.date}?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                val cancelReason = reasonEditText.text.toString().trim()
                cancelAppointment(appointment, cancelReason)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelAppointment(appointment: Appointment, cancelReason: String) {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email?.replace(".", ",") ?: ""
        val userId = currentUser.uid

        binding.progressBar.visibility = View.VISIBLE

        val userBookingRef = database.getReference("userInfo")
            .child(userId)
            .child("bookings")
            .child(appointment.bookingId)

        val clinicBookingRef = database.getReference("clinicBookings")
            .child(appointment.doctorName.replace(" ", "_").replace(".", ","))
            .child(appointment.bookingId)

        val mainBookingRef = database.getReference("bookings")
            .child(appointment.bookingId)

        val userBookingByEmailRef = database.getReference("userBookings")
            .child(userEmail)
            .child(appointment.bookingId)

        val updates = hashMapOf<String, Any>(
            "status" to "cancelled",
            "cancellationTimestamp" to System.currentTimeMillis(),
            "cancellationReason" to cancelReason
        )

        val tasks = listOf(
            userBookingRef.updateChildren(updates),
            clinicBookingRef.updateChildren(updates),
            mainBookingRef.updateChildren(updates),
            userBookingByEmailRef.updateChildren(updates)
        )

        com.google.android.gms.tasks.Tasks.whenAllSuccess<Void>(tasks)
            .addOnSuccessListener {
                Log.d("BookingHistory", "All booking nodes updated successfully")
                Toast.makeText(this, "Appointment cancelled successfully", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE

                if (appointment.status.lowercase() == "confirmed") {
                    val scheduleRef = database.reference
                        .child("clinicInfo")
                        .child(appointment.doctorName.replace(" ", "_").replace(".", ","))
                        .child("schedule")
                        .child(appointment.date)
                        .child(appointment.time)

                    scheduleRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                        override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                            val slots = currentData.getValue(Int::class.java) ?: 0
                            currentData.value = slots + 1
                            return com.google.firebase.database.Transaction.success(currentData)
                        }

                        override fun onComplete(
                            error: com.google.firebase.database.DatabaseError?,
                            committed: Boolean,
                            snapshot: com.google.firebase.database.DataSnapshot?
                        ) {
                            if (committed) {
                                Log.d("BookingHistory", "Slot restored successfully for ${appointment.date} ${appointment.time}")
                            } else if (error != null) {
                                Log.e("BookingHistory", "Error restoring slot: ${error.message}")
                                Toast.makeText(this@BookingHistory, "Failed to restore slot", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
            .addOnFailureListener { e ->
                Log.e("BookingHistory", "Error cancelling appointment: ${e.message}")
                Toast.makeText(this, "Failed to cancel appointment: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    private var autoRefreshRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val AUTO_REFRESH_INTERVAL = 30000L

    private fun setupAutoRefresh() {
        autoRefreshRunnable = object : Runnable {
            override fun run() {
                Log.d("BookingHistory", "Auto-refresh triggered")
                updateConnectionStatus()
                autoDeclineExpiredPendingAppointments()
                handler.postDelayed(this, AUTO_REFRESH_INTERVAL)
            }
        }
        handler.postDelayed(autoRefreshRunnable!!, AUTO_REFRESH_INTERVAL)
    }

    private fun updateConnectionStatus() {
        val connectedRef = database.getReference(".info/connected")
        connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    Log.d("BookingHistory", "Connected to Firebase")
                } else {
                    Log.d("BookingHistory", "Disconnected from Firebase")
                    Toast.makeText(this@BookingHistory, "Working offline. Pull to refresh when online.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("BookingHistory", "Connection check error: ${error.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::userBookingsRef.isInitialized && userBookingsListener == null) {
            loadAppointments()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (userBookingsListener != null && ::userBookingsRef.isInitialized) {
            userBookingsRef.removeEventListener(userBookingsListener!!)
            userBookingsListener = null
        }
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
    }

    inner class AppointmentAdapter(
        private val appointments: List<Appointment>,
        private val onCardClicked: (Appointment) -> Unit,
        private val onCancelClicked: (Appointment) -> Unit
    ) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {
        inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardContainer: LinearLayout = itemView.findViewById(R.id.cardContainer)
            val statusHeader: LinearLayout = itemView.findViewById(R.id.statusHeader)
            val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
            val appointmentStatus: TextView = itemView.findViewById(R.id.textStatus)
            val bookingId: TextView = itemView.findViewById(R.id.textBookingId)
            val clinicName: TextView = itemView.findViewById(R.id.clinicNameTv)
            val appointmentDate: TextView = itemView.findViewById(R.id.appointmentDateTv)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
            return AppointmentViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
            val appointment = appointments[position]

            holder.bookingId.text = "#${appointment.bookingId.takeLast(5)}"
            holder.clinicName.text = appointment.doctorName
            holder.appointmentDate.text = appointment.date

            val status = appointment.status.replaceFirstChar { it.uppercase() }
            holder.appointmentStatus.text = status

            val statusIconRes = when (appointment.status.lowercase()) {
                "confirmed" -> android.R.drawable.ic_dialog_info
                "declined" -> android.R.drawable.ic_dialog_alert
                "cancelled" -> android.R.drawable.ic_menu_close_clear_cancel
                "completed" -> android.R.drawable.ic_dialog_info
                "ongoing" -> android.R.drawable.ic_dialog_info
                else -> android.R.drawable.ic_dialog_info
            }
            holder.statusIcon.setImageResource(statusIconRes)

            val (backgroundColor, textColor) = when (appointment.status.lowercase()) {
                "confirmed" -> Pair(R.color.green, android.R.color.white)
                "declined" -> Pair(R.color.red, android.R.color.white)
                "cancelled" -> Pair(R.color.red, android.R.color.white)
                "completed" -> Pair(R.color.blue, android.R.color.white)
                "ongoing" -> Pair(R.color.green, android.R.color.white)
                else -> Pair(R.color.orange, android.R.color.white)
            }
            holder.statusHeader.setBackgroundColor(ContextCompat.getColor(this@BookingHistory, backgroundColor))
            holder.appointmentStatus.setTextColor(ContextCompat.getColor(this@BookingHistory, textColor))

            // Card click listener
            holder.cardContainer.setOnClickListener {
                onCardClicked(appointment)
            }
        }

        override fun getItemCount(): Int = appointments.size
    }

    private fun autoDeclineExpiredPendingAppointments() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        val userBookingsRef = database.getReference("userInfo")
            .child(userId)
            .child("bookings")

        userBookingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookingSnapshot in snapshot.children) {
                    val status = bookingSnapshot.child("status").getValue(String::class.java) ?: "pending"
                    if (status.lowercase() == "pending") {
                        val dateStr = bookingSnapshot.child("date").getValue(String::class.java) ?: continue
                        val timeStr = bookingSnapshot.child("time").getValue(String::class.java) ?: ""

                        try {
                            val dateFormat = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault())
                            val appointmentDateTime = dateFormat.parse("$dateStr $timeStr") ?: continue

                            if (appointmentDateTime.before(Date())) {
                                val bookingId = bookingSnapshot.child("bookingId").getValue(String::class.java) ?: continue
                                val updates = mapOf<String, Any>(
                                    "status" to "declined",
                                    "autoDeclinedTimestamp" to System.currentTimeMillis()
                                )

                                val clinicName = bookingSnapshot.child("clinicName").getValue(String::class.java) ?: ""
                                val userEmail = currentUser.email?.replace(".", ",") ?: ""

                                val tasks = listOf(
                                    userBookingsRef.child(bookingId).updateChildren(updates),
                                    database.getReference("clinicBookings")
                                        .child(clinicName.replace(" ", "_").replace(".", ","))
                                        .child(bookingId)
                                        .updateChildren(updates),
                                    database.getReference("bookings")
                                        .child(bookingId)
                                        .updateChildren(updates),
                                    database.getReference("userBookings")
                                        .child(userEmail)
                                        .child(bookingId)
                                        .updateChildren(updates)
                                )

                                com.google.android.gms.tasks.Tasks.whenAllSuccess<Void>(tasks)
                                    .addOnSuccessListener {
                                        Log.d("BookingHistory", "Pending appointment $bookingId auto-declined because it expired.")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("BookingHistory", "Failed to auto-decline appointment $bookingId: ${e.message}")
                                    }
                            }

                        } catch (e: Exception) {
                            Log.e("BookingHistory", "Error parsing date/time for auto-decline: ${e.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BookingHistory", "Error checking expired appointments: ${error.message}")
            }
        })
    }
}