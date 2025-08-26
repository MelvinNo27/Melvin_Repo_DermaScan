package com.example.dermascanai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.dermascanai.databinding.ActivityMainPageBinding
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor
import android.media.ExifInterface
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.dermascanai.ml.AIv5
import org.json.JSONArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import showWarningDialog
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainPage : AppCompatActivity() {
    private lateinit var binding: ActivityMainPageBinding
    private lateinit var firebase: FirebaseAuth
    private lateinit var database: FirebaseDatabase
//    private var interpreter: Interpreter? = null

    private val PERMISSION_REQUEST_CODE = 1001
    private val PICK_IMAGE_REQUEST = 1002
    private val CAMERA_REQUEST = 1003

    private var imageUri: Uri? = null

    private lateinit var model: AIv5
    private lateinit var conditionLabels: List<String>

    private var selectedImageBase64: String? = null
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            selectedImageBase64 = encodeImageToBase64(bitmap)
            selectedImageView?.setImageBitmap(bitmap)
        }
    }

    private var selectedImageView: ImageView? = null

    private lateinit var databaseA: DatabaseReference
    private lateinit var adapter: ClinicAdapter
    private val clinicList = mutableListOf<ClinicInfo>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebase = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")

        databaseA = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("clinicInfo")


        loadClinicsFromFirebase()
        checkPermissions()

        try {
            model = AIv5.newInstance(this)
        } catch (e: Exception) {
            Log.e("MainPage", "Model load failed", e)
            Toast.makeText(this, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

//        try {
//            val tfliteModel = org.tensorflow.lite.support.common.FileUtil.loadMappedFile(
//                this,
//                "dermascan21_float16.tflite"
//            )
//
//            val options = Interpreter.Options().apply {
//                setNumThreads(2) // try 2 threads to reduce memory pressure
//                setUseNNAPI(true) // delegate to NNAPI if available
//            }
//
//            interpreter = Interpreter(tfliteModel, options)
//
//        } catch (e: Exception) {
//            Log.e("MainPage", "Model load failed", e)
//            Toast.makeText(this, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
//        }


        try {
            conditionLabels = loadConditionLabels()
        } catch (e: IOException) {
            Log.e("MainPage", "Error loading labels", e)
            Toast.makeText(this, "Failed to load labels: ${e.message}", Toast.LENGTH_LONG).show()
            conditionLabels = emptyList()
        }

        showWarningDialog(this)

        adapter = ClinicAdapter(this, clinicList) { clickedClinic ->
            val intent = Intent(this, ClinicDetails::class.java)
            intent.putExtra("email", clickedClinic.email)  // Pass just the email string
            startActivity(intent)
        }


        binding.nerbyClinic.layoutManager = LinearLayoutManager(this)
        binding.nerbyClinic.adapter = adapter

        binding.scanButton.setOnClickListener {
            showImagePickerDialog()
        }

        binding.backBTN.setOnClickListener {
            finish()
        }
        val userId = firebase.currentUser?.uid ?: return
        val roleRef = database.getReference("clinicInfo").child(userId).child("role")

        roleRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val role = snapshot.getValue(String::class.java)
                if (role == "derma") {
                    binding.reportScan.visibility = View.VISIBLE
                } else {
                    binding.reportScan.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Failed to load user role", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadClinicsFromFirebase() {
        databaseA.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                clinicList.clear()
                for (clinicSnapshot in snapshot.children) {
                    val map = clinicSnapshot.value as? Map<String, Any> ?: continue

                    // Safely convert clinicPhone to String regardless of stored type
                    val phoneValue = map["clinicPhone"]
                    val clinicPhone = when (phoneValue) {
                        is String -> phoneValue
                        is Long -> phoneValue.toString()
                        is Int -> phoneValue.toString()
                        else -> null
                    }

                    // Manually construct ClinicInfo
                    val clinic = ClinicInfo(
                        clinicName = map["clinicName"] as? String,
                        email = map["email"] as? String,
                        clinicPhone = clinicPhone,
                        profileImage = map["profileImage"] as? String,
                        address = map["address"] as? String,
                        // Add other fields as needed, safely casted
                    )

                    clinicList.add(clinic)


                }

                adapter.notifyDataSetChanged()


            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Failed to load clinics: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
        adapter = ClinicAdapter(this, clinicList) { clickedClinic ->
            val intent = Intent(this, ClinicDetails::class.java)
            intent.putExtra("email", clickedClinic)
            startActivity(intent)
        }
    }



    private fun loadModelFile(): MappedByteBuffer {
        assets.openFd("dermascan21_float16.tflite").use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Choose from Gallery", "Take a Photo")
        android.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> getSkinImageFromGallery()
                    1 -> takePhoto()
                }
            }
            .show()
    }

    private fun getSkinImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            imageUri = FileProvider.getUriForFile(this, "com.example.dermascanai.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(intent, CAMERA_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            val bitmap: Bitmap = when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    imageUri = data?.data
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }
                CAMERA_REQUEST -> BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri!!))
                else -> return
            }

            CoroutineScope(Dispatchers.Main).launch {
                showProgress()

                val rotatedBitmap = rotateImageIfNeeded(bitmap, imageUri)
                val result = predict(rotatedBitmap)
                val diseaseName = result.substringBeforeLast(" (").trim()


                binding.reportScan.setOnClickListener {
                    showReportDialog()
                }

                hideProgress()
//                val result = predictionResult
                binding.detailBtn.visibility = View.VISIBLE
                binding.skinImageView.setImageBitmap(rotatedBitmap)
                binding.resultTextView.text = "You might have $result"
                binding.remedyTextView.text = getRemedy(diseaseName)

                binding.detailBtn.setOnClickListener {
                    // Get only the disease name (before the "(" )
                    val baos = ByteArrayOutputStream()
                    rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val byteArray = baos.toByteArray()
                    val imageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
                    val diseaseName = result.substringBeforeLast(" (").trim()

                    val intent = Intent(this@MainPage, DiseaseDetails::class.java)
                    intent.putExtra("condition", diseaseName)   // only "Acne"
                    intent.putExtra("image", imageBase64) // still sending image
                    startActivity(intent)
                }

                binding.saveScanButton.visibility = View.VISIBLE
                binding.nerbyClinic.visibility = View.VISIBLE
                binding.textClinic.visibility = View.VISIBLE

                binding.saveScanButton.setOnClickListener {

                    val condition = result
                    val remedy = getRemedy(result)

                    saveScanResultToFirebase(condition, remedy, bitmap)
                }



            }
        }
    }

    private fun showReportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Report Reminder")
        builder.setMessage("Please make a screenshot and upload it as part of your report.")
        builder.setPositiveButton("Proceed") { dialog, _ ->
            dialog.dismiss()
            // Proceed to your report logic here
            openReportScreen() // Optional: call another function or activity
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }



    private fun openReportScreen() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Send Feedback to Admin")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val input = EditText(this).apply {
            hint = "Write your message or review here"
        }

        selectedImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                500
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        val selectImageButton = Button(this).apply {
            text = "Select Screenshot Image"
            setOnClickListener {
                imagePickerLauncher.launch("image/*")
                selectedImageView?.visibility = View.VISIBLE
            }
        }

        layout.addView(input)
        layout.addView(selectImageButton)
        layout.addView(selectedImageView)

        builder.setView(layout)

        builder.setPositiveButton("Send") { dialog, _ ->
            val message = input.text.toString()
            if (message.isBlank()) {
                Toast.makeText(this, "Please enter a message.", Toast.LENGTH_SHORT).show()
            } else {
                reportScan(message, selectedImageBase64)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }


    private fun reportScan(userMessage: String, imageBase64: String?) {
        val userId = firebase.currentUser?.uid ?: return
        val userNameRef = database.getReference("clinicInfo").child(userId).child("name")

        userNameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.getValue(String::class.java) ?: "Unknown User"

                val report = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "message" to userMessage,
                    "imageBase64" to imageBase64,
                    "timestamp" to System.currentTimeMillis()
                )

                val reportsRef = database.getReference("scanReports")
                val newReportKey = reportsRef.push().key ?: return

                reportsRef.child(newReportKey).setValue(report)
                    .addOnSuccessListener {
                        Toast.makeText(this@MainPage, "Report sent successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@MainPage, "Failed to send report.", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainPage, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }



    private fun rotateImageIfNeeded(bitmap: Bitmap, uri: Uri?): Bitmap {
        val inputStream = contentResolver.openInputStream(uri!!)
        val exif = ExifInterface(inputStream!!)
        val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        val inputStream = assets.open("labels.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = org.json.JSONObject(json)

        for (i in 0 until jsonObject.length()) {
            labels.add(jsonObject.getString(i.toString()))
        }
        return labels
    }

//    private val conditionLabels: List<String> by lazy { loadLabels() }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        try {
//            interpreter?.close()
//            interpreter = null
//        } catch (e: Exception) {
//            Log.e("MainPage", "Error closing interpreter", e)
//        }
//    }


    private fun predict(bitmap: Bitmap): String {
        // Resize to 300x300
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)

        // Convert bitmap to normalized ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(4 * 1 * 300 * 300 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until 300) {
            for (x in 0 until 300) {
                val pixel = resizedBitmap.getPixel(x, y)

                // Normalize to [0,1] just like ImageDataGenerator(rescale=1./255)
                val r = (pixel shr 16 and 0xFF) / 255.0f
                val g = (pixel shr 8 and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }

        // Prepare Tensor input
        val input = TensorBuffer.createFixedSize(intArrayOf(1, 300, 300, 3), DataType.FLOAT32)
        input.loadBuffer(byteBuffer)

        // Run inference
        val outputs = model.process(input)
        val outputTensor = outputs.outputFeature0AsTensorBuffer
        val resultArray = outputTensor.floatArray

        // Find the highest confidence class
        val maxIndex = resultArray.indices.maxByOrNull { resultArray[it] } ?: -1
        val confidence = if (maxIndex != -1) resultArray[maxIndex] * 100 else 0f

        return if (maxIndex != -1 && maxIndex < conditionLabels.size) {
            // Show like: "You might have Acne (82.45%)"
            "${conditionLabels[maxIndex]} (%.2f%%)".format(confidence)
        } else {
            "Unknown condition"
        }
    }


    private fun loadConditionLabels(): List<String> {
        val inputStream = assets.open("labels.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)

        val labels = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            labels.add(jsonArray.getString(i))
        }
        return labels
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 300 * 300 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(300 * 300)
        bitmap.getPixels(intValues, 0, 300, 0, 0, 300, 300)

        var pixelIndex = 0
        for (i in 0 until 300) {
            for (j in 0 until 300) {
                val pixel = intValues[pixelIndex++]
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
                byteBuffer.putFloat((pixel and 0xFF) / 255f)          // B
            }
        }
        return byteBuffer
    }


    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val result = Array(1) { Array(300) { Array(300) { FloatArray(3) } } }  // <-- updated
        for (i in 0 until 300) {
            for (j in 0 until 300) {
                val pixel = bitmap.getPixel(i, j)
                result[0][i][j][0] = Color.red(pixel) / 255.0f
                result[0][i][j][1] = Color.green(pixel) / 255.0f
                result[0][i][j][2] = Color.blue(pixel) / 255.0f
            }
        }
        return result
    }


    private fun getConditionLabel(index: Int): String {
        val conditionLabels = listOf(
            "Acne",
            "Actinic Keratosis (AK)",
            "Basal Cell Carcinoma (BCC)",
            "Chickenpox (Varicella)",
            "Eczema  or Atopic Dermatitis",
            "Melanocytic Nevi (Moles)",
            "Melanoma",
            "Monkeypox",
            "Nail Fungus (Onychomycosis)",
            "Normal Skin",
            "Psoriasis",
            "Rosacea",
            "Seborrheic Keratosis",
            "Tinea or Ringworm",
            "Warts (Verruca, Viral Infection)"
        )
        return conditionLabels.getOrElse(index) { "Unknown" }
    }


    private fun getRemedy(condition: String): String {
        return when (condition) {

            "Acne" -> "Cleanse your face twice daily with a mild cleanser and apply over-the-counter benzoyl peroxide or salicylic acid products to reduce inflammation and bacteria."
            "Actinic Keratosis (AK)" -> "Apply sunscreen regularly, avoid excessive sun exposure, and see a dermatologist for possible cryotherapy or prescription treatments."
            "Basal Cell Carcinoma (BCC)" -> "Seek immediate medical attention. BCC is a form of skin cancer and requires professional treatment, such as surgery or topical medications."
            "Chickenpox (Varicella)" -> "Use calamine lotion or oatmeal baths to relieve itching. Keep nails trimmed to prevent scratching and secondary infections."
            "Eczema  or Atopic Dermatitis" -> "Keep skin moisturized with fragrance-free creams or ointments. Apply cool compresses to relieve itching and avoid known irritants."
            "Melanocytic Nevi (Moles)" -> "Most moles are harmless, but monitor for changes in size, shape, or color. Consult a dermatologist if you notice abnormalities."
            "Melanoma" -> "Seek immediate medical attention. Melanoma is a serious form of skin cancer and cannot be treated with home remedies."
            "Monkeypox" -> "Isolate yourself, keep rashes clean and dry, and take pain relievers or fever reducers if needed. Consult a healthcare provider for monitoring."
            "Nail Fungus (Onychomycosis)" -> "Apply antifungal creams or medicated nail solutions. Keep nails dry and trimmed; oral medication may be required for persistent cases."
            "Normal Skin" -> "Your skin is healthy. Maintain good hydration, a balanced diet, and regular skincare with sunscreen to keep it that way!"
            "Psoriasis" -> "Apply aloe vera gel or a moisturizer with coal tar or salicylic acid. Short daily baths with oatmeal or Epsom salt may soothe itching."
            "Rosacea" -> "Avoid triggers such as spicy foods, alcohol, and extreme temperatures. Use gentle skincare products and consult a dermatologist for medication if severe."
            "Seborrheic Keratosis" -> "These are generally harmless. Moisturizers and gentle exfoliation may help irritation. For removal, consult a dermatologist."
            "Tinea or Ringworm" -> "Apply an over-the-counter antifungal cream (like clotrimazole or terbinafine) twice daily. Keep the affected area clean and dry."
            "Warts (Verruca, Viral Infection)" -> "Use salicylic acid treatments or over-the-counter cryotherapy. Avoid picking to prevent spreading the virus."
            else -> "No specific remedy found. Consult a dermatologist for diagnosis and treatment."
        }

    }

    private fun saveScanResultToFirebase(condition: String, remedy: String, bitmap: Bitmap) {
        val databaseReference = FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val imageBase64 = encodeImageToBase64(bitmap)
        val timestamp = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val scanId = SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())

        val scanResult = ScanResult(condition, remedy, imageBase64, timestamp)

        databaseReference.child("scanResults").child(userId).child(scanId).setValue(scanResult)
            .addOnSuccessListener {
                Toast.makeText(this, "Scan result saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)!!
    }

    // ✅ Cleanup model
    override fun onDestroy() {
        super.onDestroy()
        try {
            model.close()
        } catch (e: Exception) {
            Log.e("MainPage", "Error closing model", e)
        }
    }


    private fun showProgress() {
        binding.loadingProgressBar.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.loadingProgressBar.visibility = View.GONE
    }

}