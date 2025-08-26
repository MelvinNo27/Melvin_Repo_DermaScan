package com.example.dermascanai

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.Properties
import android.os.CountDownTimer
import com.example.dermascanai.databinding.ActivityClinicOtpauthBinding


class ClinicOTPAuth : AppCompatActivity() {
    private lateinit var binding: ActivityClinicOtpauthBinding
    private lateinit var mAuth: FirebaseAuth
    private val db by lazy {
        FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }

    private var clinicKey: String? = null
    private var otp: String? = null
    private var plainPassword: String? = null
    private var clinicInfo: ClinicInfo? = null

    private val SMTP_USER = "rp0887955@gmail.com"     // <-- your Gmail
    private val SMTP_PASS = "whknsnwbhxubpqkm"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClinicOtpauthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        setupOtpInputs()

        // ✅ Get values passed from DermaRegister
        clinicKey = intent.getStringExtra("CLINIC_KEY")
        otp = intent.getStringExtra("OTP")
        plainPassword = intent.getStringExtra("PLAIN_PASSWORD")

        if (clinicKey == null || otp == null || plainPassword == null) {
            Toast.makeText(this, "Missing registration data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ✅ Load ClinicInfo from Firebase
        db.getReference("pendingClinics").child(clinicKey!!).get()
            .addOnSuccessListener {
                clinicInfo = it.getValue(ClinicInfo::class.java)
                if (clinicInfo == null) {
                    Toast.makeText(this, "Failed to load clinic info", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                finish()
            }

        // ✅ Verify OTP button
        binding.verifyBtn.setOnClickListener {
            val enteredOtp = binding.otp1.text.toString().trim() +
                    binding.otp2.text.toString().trim() +
                    binding.otp3.text.toString().trim() +
                    binding.otp4.text.toString().trim() +
                    binding.otp5.text.toString().trim() +
                    binding.otp6.text.toString().trim()

            if (enteredOtp == otp) {
                registerClinicAccount()
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }

        binding.resendOtpBtn.setOnClickListener {
            resendOtp()
        }
    }

    private fun registerClinicAccount() {
        val clinic = clinicInfo ?: return

        mAuth.createUserWithEmailAndPassword(clinic.email.orEmpty(), plainPassword!!)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val uid = mAuth.currentUser?.uid ?: return@addOnCompleteListener

                    // ✅ Move clinic from pendingClinics → clinics
                    db.getReference("clinicInfo").child(uid).setValue(clinic)
                        .addOnSuccessListener {
                            db.getReference("pendingClinics").child(clinicKey!!).removeValue()

                            Toast.makeText(this, "Clinic registered successfully!", Toast.LENGTH_SHORT).show()
                            FirebaseAuth.getInstance().signOut()
                            startActivity(Intent(this, Login::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to save clinic: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Auth failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupOtpInputs() {
        val otpFields = listOf(
            binding.otp1, binding.otp2, binding.otp3,
            binding.otp4, binding.otp5, binding.otp6
        )

        for (i in otpFields.indices) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        otpFields[i + 1].requestFocus() // ✅ Move to next
                    } else if (s?.isEmpty() == true && i > 0) {
                        otpFields[i - 1].requestFocus() // ✅ Back on delete
                    }
                }
            })
        }
    }

    private fun resendOtp() {
        val newOtp = (100000..999999).random().toString()
        otp = newOtp // replace old OTP

        val email = clinicInfo?.email ?: return
        val subject = "Your DermaScan OTP Code"
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
                    <span style="font-size: 32px; font-weight: bold; color: #1976d2; letter-spacing: 5px;">$newOtp</span>
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
                return PasswordAuthentication(SMTP_USER, SMTP_PASS) // replace with your credentials
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
                    Toast.makeText(this, "New OTP sent ✅", Toast.LENGTH_SHORT).show()
                    startOtpCooldown()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to resend OTP: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startOtpCooldown() {
        binding.resendOtpBtn.isEnabled = false

        object : CountDownTimer(30 * 1000, 1000) { // 30 seconds
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.resendOtpBtn.text = String.format("Resend in %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.resendOtpBtn.isEnabled = true
                binding.resendOtpBtn.text = "Resend OTP"
            }
        }.start()
    }

}
