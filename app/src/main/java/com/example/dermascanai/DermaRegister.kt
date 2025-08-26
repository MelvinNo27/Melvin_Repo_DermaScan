package com.example.dermascanai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Html
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.dermascanai.databinding.ActivityDermaRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.mindrot.jbcrypt.BCrypt
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage


class DermaRegister : AppCompatActivity() {
    private lateinit var binding: ActivityDermaRegisterBinding

    private lateinit var mAuth: FirebaseAuth
    private lateinit var dDatabase: DatabaseReference

    // Profile image
    private var selectedProfileBitmap: Bitmap? = null

    // Document images
    private var birBitmap: Bitmap? = null
    private var businessPermitBitmap: Bitmap? = null
    private var validIdBitmap: Bitmap? = null

    private val REQUEST_PROFILE_IMAGE = 1
    private val REQUEST_BIR = 10
    private val REQUEST_BUSINESS_PERMIT = 11
    private val REQUEST_VALID_ID = 12

    private val SMTP_USER = "rp0887955@gmail.com"     // <-- your Gmail
    private val SMTP_PASS = "whknsnwbhxubpqkm"
    private var cameraImageUri: Uri? = null

    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val times = listOf(
        "6:00 AM", "7:00 AM", "8:00 AM", "9:00 AM",
        "10:00 AM", "11:00 AM", "12:00 PM", "1:00 PM",
        "2:00 PM", "3:00 PM", "4:00 PM", "5:00 PM"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDermaRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        dDatabase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo")

        setupClinicOpeningSpinners()
        setupClinicClosingSpinners()
        setupSpinnerListeners()

        binding.uploadBtn.setOnClickListener { showImagePickerDialog(REQUEST_PROFILE_IMAGE) }
        binding.uploadBirBtn.setOnClickListener { showImagePickerDialog(REQUEST_BIR) }
        binding.uploadBusinessPermitBtn.setOnClickListener { showImagePickerDialog(REQUEST_BUSINESS_PERMIT) }
        binding.uploadValidIdBtn.setOnClickListener { showImagePickerDialog(REQUEST_VALID_ID) }

        binding.submit.setOnClickListener {
            if (validateInputs()) {
                submitRegistration()
            }
        }

        binding.backBTN.setOnClickListener { finish() }


        binding.namelayout.hint = Html.fromHtml("Full Name <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY)
        binding.emailLayout.hint = Html.fromHtml("Email <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY)
        binding.phoneLayout.hint = Html.fromHtml("Phone Number <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY)
        binding.passwordLayout.hint = Html.fromHtml("Password <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY)
        binding.confirmLayout.hint = Html.fromHtml("Confirm Password <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY)


    }

    private fun setupClinicOpeningSpinners() {
        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClinicOpenDay.adapter = dayAdapter

        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, times)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClinicOpenTime.adapter = timeAdapter
    }

    private fun setupClinicClosingSpinners() {
        // Initially populate Close Day spinner with all days
        val closeDayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        closeDayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClinicCloseDay.adapter = closeDayAdapter

        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, times)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClinicCloseTime.adapter = timeAdapter
    }

    private fun setupSpinnerListeners() {
        binding.spinnerClinicOpenDay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOpenDay = days[position]
                // Update Close Day spinner options to exclude selected Open Day
                val closeDayOptions = days.filter { it != selectedOpenDay }
                val closeDayAdapter = ArrayAdapter(this@DermaRegister, android.R.layout.simple_spinner_item, closeDayOptions)
                closeDayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerClinicCloseDay.adapter = closeDayAdapter
                // Reset close day selection to first available
                binding.spinnerClinicCloseDay.setSelection(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No action needed here
            }
        }
    }

    private fun showImagePickerDialog(requestCode: Int) {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Image")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCameraIntent(requestCode)
                1 -> openGalleryIntent(requestCode)
            }
        }
        builder.show()
    }

    private fun openGalleryIntent(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        startActivityForResult(intent, requestCode)
    }

    private fun openCameraIntent(requestCode: Int) {
        val photoFile = File.createTempFile("IMG_", ".jpg", cacheDir)
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "com.example.dermascanai.fileprovider",  // same as in manifest
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_PROFILE_IMAGE -> {
                    val bitmap: Bitmap? = when {
                        // Camera capture → we stored Uri in cameraImageUri
                        cameraImageUri != null -> {
                            BitmapFactory.decodeStream(contentResolver.openInputStream(cameraImageUri!!))
                        }

                        // Gallery pick → returns Uri in data.data
                        data?.data != null -> {
                            val uri = data.data
                            BitmapFactory.decodeStream(contentResolver.openInputStream(uri!!))
                        }

                        // Fallback → some camera apps return a small Bitmap in extras
                        else -> {
                            data?.extras?.get("data") as? Bitmap
                        }
                    }

                    bitmap?.let {
                        selectedProfileBitmap = it
                        binding.profPic.setImageBitmap(it)
                    }
                }


                REQUEST_BIR -> {
                    val bitmap = if (cameraImageUri != null && data == null) {
                        // Camera
                        BitmapFactory.decodeStream(contentResolver.openInputStream(cameraImageUri!!))
                    } else {
                        // Gallery
                        handleGalleryResult(data)
                    }
                    bitmap?.let {
                        birBitmap = it
                        binding.birImageView.setImageBitmap(it)
                    }
                }

                REQUEST_BUSINESS_PERMIT -> {
                    val bitmap = if (cameraImageUri != null && data == null) {
                        BitmapFactory.decodeStream(contentResolver.openInputStream(cameraImageUri!!))
                    } else {
                        handleGalleryResult(data)
                    }
                    bitmap?.let {
                        businessPermitBitmap = it
                        binding.businessPermitImageView.setImageBitmap(it)
                    }
                }

                REQUEST_VALID_ID -> {
                    val bitmap = if (cameraImageUri != null && data == null) {
                        BitmapFactory.decodeStream(contentResolver.openInputStream(cameraImageUri!!))
                    } else {
                        handleGalleryResult(data)
                    }
                    bitmap?.let {
                        validIdBitmap = it
                        binding.validIdImageView.setImageBitmap(it)
                    }
                }
            }
        }
    }


    private fun handleGalleryResult(data: Intent?): Bitmap? {
        val uri = data?.data ?: return null
        return MediaStore.Images.Media.getBitmap(contentResolver, uri)
    }


    private fun validateInputs(): Boolean {
        if (binding.name.text.toString().trim().isEmpty() ||
            binding.email.text.toString().trim().isEmpty() ||
            binding.password.text.toString().trim().isEmpty() ||
            binding.confirm.text.toString().trim().isEmpty() ||
            binding.clinicName.text.toString().trim().isEmpty() ||
            binding.clinicAddress.text.toString().trim().isEmpty() ||
            binding.clinicPhone.text.toString().trim().isEmpty()
        ) {
            Toast.makeText(this, "Please complete all required fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (binding.password.text.toString() != binding.confirm.text.toString()) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        if (birBitmap == null || businessPermitBitmap == null || validIdBitmap == null) {
            Toast.makeText(this, "Please upload all required documents", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!binding.checkBox.isChecked) {
            Toast.makeText(this, "Please accept the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
    private fun submitRegistration() {
        val profileImageEncoded = selectedProfileBitmap?.let { encodeImageToBase64(it) } ?: ""
        val birEncoded = encodeImageToBase64(birBitmap!!)
        val businessPermitEncoded = encodeImageToBase64(businessPermitBitmap!!)
        val validIdEncoded = encodeImageToBase64(validIdBitmap!!)

        val plainPassword = binding.password.text.toString().trim()
        val hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt())

        val selectedOpenDay = binding.spinnerClinicOpenDay.selectedItem as? String ?: ""
        val selectedCloseDay = binding.spinnerClinicCloseDay.selectedItem as? String ?: ""
        val selectedOpenTime = binding.spinnerClinicOpenTime.selectedItem as? String ?: ""
        val selectedCloseTime = binding.spinnerClinicCloseTime.selectedItem as? String ?: ""
        val email = binding.email.text.toString().trim()

        val clinicInfo = ClinicInfo(
            name = binding.name.text.toString().trim(),
            email = email,
            role = "derma",
            contact = binding.phone.text.toString().trim(),
            clinicName = binding.clinicName.text.toString().trim(),
            clinicAddress = binding.clinicAddress.text.toString().trim(),
            clinicPhone = binding.clinicPhone.text.toString().trim(),
            clinicOpenDay = selectedOpenDay,
            clinicCloseDay = selectedCloseDay,
            clinicOpenTime = selectedOpenTime,
            clinicCloseTime = selectedCloseTime,
            birImage = birEncoded,
            businessPermitImage = businessPermitEncoded,
            validIdImage = validIdEncoded,
            password = hashedPassword,
            logoImage = profileImageEncoded,
            birDocument = birEncoded,
            permitDocument = businessPermitEncoded,
            address = binding.clinicAddress.text.toString().trim(),
            openingTime = selectedOpenTime,
            closingTime = selectedCloseTime,
            operatingDays = "$selectedOpenDay to $selectedCloseDay",
            tagline = "Welcome to our clinic!",
            about = "Clinic description goes here...",
            status = "pending",
            acceptingPatients = true,
            services = emptyList(),
            dermatologists = emptyList()
        )

        // Generate OTP
        val otp = sendOtpEmail(email)

        // 🔑 Generate unique key for temporary storage
        val pendingRef = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("pendingClinics")
        val clinicKey = pendingRef.push().key ?: email.replace(".", "_")

        // Save clinic info temporarily in Firebase
        pendingRef.child(clinicKey).setValue(clinicInfo)
            .addOnSuccessListener {
                val intent = Intent(this, ClinicOTPAuth::class.java).apply {
                    putExtra("CLINIC_KEY", clinicKey) // ✅ only send key
                    putExtra("OTP", otp)
                    putExtra("PLAIN_PASSWORD", plainPassword)
                }
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun sendOtpEmail(email: String): String {
        val otp = (100000..999999).random().toString()
        val subject = "🔐 Verify Your DermaScan Account"

        val logoUrl = "https://imgur.com/a/BTNNzGi"

        val htmlMessage = """
        <div style="font-family: Arial, sans-serif; padding: 20px; background-color: #f4f6f8; color: #333;">
            <div style="max-width: 500px; margin: auto; background: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">
                <div style="text-align: center; margin-bottom: 20px;">
                    <img src="$logoUrl" alt="DermaScan Logo" style="width: 120px; height: auto;">
                </div>
                <h2 style="text-align: center; color: #1976d2;">DermaScan Verification</h2>
                <p style="font-size: 16px;">Hello 👋,</p>
                <p style="font-size: 16px;">Use the OTP below to verify your account. This code will expire in <b>5 minutes</b>:</p>
                <div style="text-align: center; margin: 20px 0;">
                    <span style="font-size: 32px; font-weight: bold; color: #1976d2; letter-spacing: 5px;">$otp</span>
                </div>
                <p style="font-size: 14px; color: #777;">If you didn’t request this, please ignore this email.</p>
                <hr style="margin: 20px 0;">
                <p style="font-size: 12px; text-align: center; color: #999;">© 2025 DermaScan. All rights reserved.</p>
            </div>
        </div>
    """.trimIndent()

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(SMTP_USER, SMTP_PASS)
            }
        })

        Thread {
            try {
                val mimeMessage = MimeMessage(session).apply {
                    setFrom(InternetAddress(SMTP_USER, "DermaScan AI"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
                    this.subject = subject
                    setContent(htmlMessage, "text/html; charset=utf-8")
                }

                Transport.send(mimeMessage)

                runOnUiThread {
                    Toast.makeText(this, "OTP sent successfully ✅", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to send OTP: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        return otp
    }



    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
    }
}