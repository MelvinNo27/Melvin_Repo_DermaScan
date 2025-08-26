package com.example.dermascanai

import android.R.attr.password
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Html
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dermascanai.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.mindrot.jbcrypt.BCrypt
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage


class Register : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDatabase: DatabaseReference
    private lateinit var dDatabase: DatabaseReference
    private lateinit var userRole: String


    private var selectedImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val REQUEST_CAMERA_PERMISSION = 100

    private val SMTP_USER = "rp0887955@gmail.com"     // <-- your Gmail
    private val SMTP_PASS = "whknsnwbhxubpqkm "

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("userInfo")
        dDatabase = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("dermaInfo")


        binding.submit.isEnabled = false

        binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
            binding.submit.isEnabled = isChecked
            binding.submit.setBackgroundColor(
                if (isChecked)
                    ContextCompat.getColor(this, R.color.Vivid_Violet) // Replace with your active color
                else
                    ContextCompat.getColor(this, android.R.color.darker_gray) // Grey when disabled
            )
        }


        binding.namelayout.hint = Html.fromHtml(getString(R.string.full_name), Html.FROM_HTML_MODE_LEGACY)
        binding.emailLayout.hint = Html.fromHtml(getString(R.string.email), Html.FROM_HTML_MODE_LEGACY)
        binding.adminPass.hint = Html.fromHtml(getString(R.string.password), Html.FROM_HTML_MODE_LEGACY)
        binding.confirmLayout.hint = Html.fromHtml(getString(R.string.confirm_password), Html.FROM_HTML_MODE_LEGACY)

        userRole = intent.getStringExtra("USER_ROLE") ?: "user"

        binding.navTerms.setOnClickListener {
            val intent = Intent(this, TermsConditions::class.java)
            startActivity(intent)
        }



        binding.uploadBtn.setOnClickListener {
            showImagePickerDialog()
        }

        binding.submit.setOnClickListener {
            registerUser()
        }

        binding.backBTN.setOnClickListener {
            finish()
        }
    }
    private fun generateOTP(): String {
        return (100000..999999).random().toString()
    }


    private fun sendOtpEmail(email: String, otp: String) {
        Thread {
            try {
                val subject = "🔐 Verify Your DermaScan Account"

                // Replace with your Firebase Storage or web-hosted logo URL
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

                sendHtmlMail(email, subject, htmlMessage)

                runOnUiThread {
                    Toast.makeText(this, "OTP sent to $email", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to send OTP: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    fun sendHtmlMail(to: String, subject: String, htmlBody: String) {
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

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(SMTP_USER, "DermaScan AI")) // Gmail + display name
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            this.subject = subject
            setContent(htmlBody, "text/html; charset=utf-8")
        }

        Transport.send(message)
    }




    private fun registerUser() {
        val fullName = binding.name.text.toString().trim()
        val email = binding.email.text.toString().trim()
        val password = binding.password.text.toString().trim()
        val confirmPassword = binding.confirm.text.toString().trim()

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
            return
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            return
        }

        if (confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please confirm your password", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        val base64Image = if (selectedBitmap != null) {
            encodeImageToBase64(selectedBitmap!!)
        } else {
            encodeDefaultProfileToBase64()
        }

        val hashedPassword = hashPassword(password)
        val newUser = UserInfo(fullName, email, hashedPassword, userRole, base64Image)

//        mAuth.createUserWithEmailAndPassword(email, password)
//            .addOnCompleteListener { authTask ->
//                if (authTask.isSuccessful) {
//                    val newUserId = mAuth.currentUser?.uid
//                    if (newUserId != null) {
//                        if(userRole == "user") {
//                            mDatabase.child(newUserId).setValue(newUser)
//                                .addOnCompleteListener { dbTask ->
//                                    if (dbTask.isSuccessful) {
//                                        Toast.makeText(
//                                            this,
//                                            "Registration successful",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                        FirebaseAuth.getInstance().signOut()
//                                        toLogin()
//                                    } else {
//                                        Toast.makeText(
//                                            this,
//                                            "Failed to save user data",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                    }
//                                }
//                        }else{
//                            dDatabase.child(newUserId).setValue(newUser)
//                                .addOnCompleteListener { dbTask ->
//                                    if (dbTask.isSuccessful) {
//                                        Toast.makeText(
//                                            this,
//                                            "Registration successful",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                        FirebaseAuth.getInstance().signOut()
//                                        toLogin()
//                                    } else {
//                                        Toast.makeText(
//                                            this,
//                                            "Failed to save user data",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                    }
//                                }
//                        }
//                    }
//                } else {
//                    Toast.makeText(this, "Auth failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
//                }
//            }

        val otp = generateOTP()
        sendOtpEmail(email, otp)

        // go to OTP screen
        val intent = Intent(this, OTPAuth::class.java)
        intent.putExtra("FULL_NAME", fullName)
        intent.putExtra("EMAIL", email)
        intent.putExtra("PASSWORD", password)
        intent.putExtra("ROLE", userRole)
        intent.putExtra("IMAGE", base64Image)
        intent.putExtra("OTP", otp)
        startActivity(intent)
    }


    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun toLogin() {
        val intent = Intent(this, Login::class.java)
        startActivity(intent)
        finish()
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Profile Image")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        requestCameraPermission()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "TempImage", null)
        return if (path != null) Uri.parse(path) else null
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            openCameraIntent()
        }
    }

    private fun openCameraIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let {
                        selectedImageUri = it
                        selectedBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                        binding.profPic.setImageURI(it)
                    }
                }

                REQUEST_IMAGE_CAPTURE -> {
                    val photo = data?.extras?.get("data") as? Bitmap
                    if (photo != null) {
                        selectedBitmap = photo
                        selectedImageUri = getImageUriFromBitmap(photo)
                        binding.profPic.setImageBitmap(photo)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraIntent()
            } else {
                Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeDefaultProfileToBase64(): String {
        val defaultBitmap = BitmapFactory.decodeResource(resources, R.drawable.default_profile)
        val outputStream = ByteArrayOutputStream()
        defaultBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

}
