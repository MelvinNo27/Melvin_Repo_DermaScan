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
        appointmentAdapter = AppointmentAdapter(filteredAppointmentList) { appointment ->
            showCancelConfirmationDialog(appointment)
        }
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
                    it.status.lowercase() == "confirmed" || it.status.lowercase() == "completed"
                })
                if (filteredAppointmentList.isEmpty()) {
                    binding.emptyStateApprovedLayout.visibility = View.VISIBLE
                }
            }
            "completed" -> {  // New dedicated completed filter
                filteredAppointmentList.addAll(appointmentList.filter {
                    it.status.lowercase() == "completed"
                })
                if (filteredAppointmentList.isEmpty()) {
                    // You may want to add a dedicated empty state for completed
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

        userBookingsRef.keepSynced(true) // Keep this data synced locally
        userBookingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                appointmentList.clear()
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) {
                        try {
                            // Create appointment from booking data
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
                                doctorName = clinicName, // Map clinicName to doctorName for display
                                date = date,
                                time = time, // Now we're using time from the database
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

        // Firebase references
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

        // Update all booking paths together
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

                // Only restore slot if it was a confirmed booking
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
    private val AUTO_REFRESH_INTERVAL = 30000L // 30 seconds

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
        private val onCancelClicked: (Appointment) -> Unit
    ) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {
        inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val statusHeader: LinearLayout = itemView.findViewById(R.id.statusHeader)
            val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
            val appointmentStatus: TextView = itemView.findViewById(R.id.textStatus)
            val bookingId: TextView? = itemView.findViewById(R.id.textBookingId)
            val doctorName: TextView = itemView.findViewById(R.id.textDoctorName)
            val appointmentDate: TextView = itemView.findViewById(R.id.textAppointmentDate)
            val appointmentTime: TextView? = itemView.findViewById(R.id.textAppointmentTime)
            val serviceText: TextView? = itemView.findViewById(R.id.textService)
            val serviceContainer: LinearLayout? = itemView.findViewById(R.id.serviceContainer)
            val messageContainer: LinearLayout? = itemView.findViewById(R.id.messageContainer)
            val messageText: TextView = itemView.findViewById(R.id.textMessage)
            val cancelButtonContainer: FrameLayout = itemView.findViewById(R.id.cancelButtonContainer)
            val cancelButton: Button = itemView.findViewById(R.id.btnCancelAppointment)
            val cancelReasonContainer: View? = itemView.findViewById(R.id.cancelReasonContainer)
            val cancelReasonText: TextView? = itemView.findViewById(R.id.textCancelReason)
            val actionButtonsContainer: LinearLayout? = itemView.findViewById(R.id.actionButtonsContainer)
            val bookingTimestamp: TextView? = itemView.findViewById(R.id.textBookingTimestamp)
            val timeRemaining: TextView? = itemView.findViewById(R.id.textTimeRemaining)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
            return AppointmentViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
            val appointment = appointments[position]
            holder.bookingId?.let {
                it.text = "#${appointment.bookingId.takeLast(5)}"
            }

            holder.doctorName.text = appointment.doctorName
            holder.appointmentDate.text = appointment.date
            holder.appointmentTime?.let { timeView ->
                if (appointment.time.isNotEmpty()) {
                    timeView.visibility = View.VISIBLE
                    timeView.text = appointment.time
                } else {
                    timeView.visibility = View.GONE
                }
            }
            holder.serviceText?.let { serviceView ->
                if (appointment.service.isNotEmpty()) {
                    holder.serviceContainer?.visibility = View.VISIBLE
                    serviceView.visibility = View.VISIBLE
                    serviceView.text = appointment.service
                } else {
                    holder.serviceContainer?.visibility = View.GONE
                    serviceView.visibility = View.GONE
                }
            }
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
            if (appointment.message.isNotEmpty()) {
                holder.messageContainer?.visibility = View.VISIBLE
                holder.messageText.visibility = View.VISIBLE
                holder.messageText.text = appointment.message
            } else {
                holder.messageContainer?.visibility = View.GONE
                holder.messageText.visibility = View.GONE
            }
            holder.actionButtonsContainer?.visibility = if (appointment.status.lowercase() == "pending"
                || appointment.status.lowercase() == "confirmed") View.VISIBLE else View.GONE
            if (appointment.status.lowercase() == "pending" || appointment.status.lowercase() == "confirmed") {
                holder.cancelButtonContainer.visibility = View.VISIBLE
                holder.cancelButton.setOnClickListener {
                    onCancelClicked(appointment)
                }
            } else {
                holder.cancelButtonContainer.visibility = View.GONE
            }
            if (appointment.status.lowercase() == "cancelled" && appointment.cancellationReason?.isNotEmpty() == true) {
                holder.cancelReasonContainer?.visibility = View.VISIBLE
                holder.cancelReasonText?.text = appointment.cancellationReason
            } else {
                holder.cancelReasonContainer?.visibility = View.GONE
            }

            holder.bookingTimestamp?.let { timestampView ->
                val timestamp = if (appointment.createdAt > 0) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                    "Booked on ${dateFormat.format(Date(appointment.createdAt))}"
                } else {
                    "Recently booked"
                }
                timestampView.text = timestamp
            }

            holder.timeRemaining?.let { timeRemainingView ->
                val daysLeft = getDaysLeft(appointment.date)

                timeRemainingView.visibility = View.VISIBLE
                timeRemainingView.text = when {
                    daysLeft > 1 -> "$daysLeft days left"
                    daysLeft == 1L -> "Tomorrow"
                    daysLeft == 0L -> "Tomorrow"
                    daysLeft < 0 -> "Expired"
                    else -> ""
                }
            }


        }
        override fun getItemCount(): Int = appointments.size

        private fun getDaysLeft(dateString: String): Long {
            return try {
                val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
                val appointmentDate = sdf.parse(dateString)

                val calAppointment = Calendar.getInstance().apply {
                    time = appointmentDate!!
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val calToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val diff = calAppointment.timeInMillis - calToday.timeInMillis
                diff / (1000 * 60 * 60 * 24)

            } catch (e: Exception) {
                e.printStackTrace()
                -999L
            }
        }

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
                                // Update status to declined
                                val bookingId = bookingSnapshot.child("bookingId").getValue(String::class.java) ?: continue
                                val updates = mapOf<String, Any>(
                                    "status" to "declined",
                                    "autoDeclinedTimestamp" to System.currentTimeMillis()
                                )

                                // Update all relevant paths
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