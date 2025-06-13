package com.fyp.tomato_guard.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.fyp.tomato_guard.R
import com.fyp.tomato_guard.adapters.ImageSliderAdapter
import com.fyp.tomato_guard.adapters.SeasonalTipAdapter
import com.fyp.tomato_guard.databinding.ActivityMainBinding
import com.fyp.tomato_guard.databinding.BottomRecomendationsBinding
import com.fyp.tomato_guard.databinding.BottomSheetDiseaseDetailsBinding
import com.fyp.tomato_guard.databinding.DialogImageResultBinding
import com.fyp.tomato_guard.models.SeasonalTip
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val IMAGE_PICK_CODE = 1001
    private val REQUEST_CAMERA = 1002
    private var cameraImageUri: Uri? = null
    private var croppedImageUri: Uri? = null

    private var leafModel: Module? = null
    private var diseaseModel: Module? = null
    private lateinit var progressOverlay: View
    private var language: String = "eng"
    private lateinit var viewPager: ViewPager2
    private val imageIds =
        intArrayOf(R.drawable.img0, R.drawable.img1, R.drawable.img2) // Add more images if needed
    private val sliderHandler = Handler(Looper.getMainLooper())
    private val slideRunnable = Runnable {
        val next = (viewPager.currentItem + 1) % imageIds.size
        viewPager.setCurrentItem(next, true)
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private val diseaseLabels = arrayOf("Early Blight", "Healthy", "Late Blight", "Septoria")
    private val leafLabels = arrayOf("Leaf", "Non-Leaf")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        progressOverlay = findViewById(R.id.progress_overlay)
        loadTips()
        loadStrip()
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission granted, you can now pick image
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)

        binding.urduLabel.setOnClickListener {
            binding.urduLabel.setBackgroundResource(R.drawable.toggle_selected)
            binding.urduLabel.setTextColor(Color.WHITE)
            binding.englishLabel.setBackgroundColor(Color.TRANSPARENT)
            binding.englishLabel.setTextColor(Color.BLACK)
            language = "urdu"
            setLanguage()
            loadTips()
            loadStrip()
        }

        binding.englishLabel.setOnClickListener {
            binding.englishLabel.setBackgroundResource(R.drawable.toggle_selected)
            binding.englishLabel.setTextColor(Color.WHITE)
            binding.urduLabel.setBackgroundColor(Color.TRANSPARENT)
            binding.urduLabel.setTextColor(Color.BLACK)
            language = "eng"
            setLanguage()
            loadTips()
            loadStrip()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            loadModels()
        }
        binding.recommendations.setOnClickListener {
            bottomDiseaseDetails()
        }
        viewPager = findViewById(R.id.image_slider)
        val adapter = ImageSliderAdapter(this, imageIds)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sliderHandler.removeCallbacks(slideRunnable)
                sliderHandler.postDelayed(slideRunnable, 2000) // 1-second delay
            }
        })
        binding.classifyDisease.setOnClickListener {
            showImageSourceDialog()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.gallery), getString(R.string.camera))
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.choose_image_source))
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> pickImageFromGallery()
                1 -> takePhotoFromCamera()
            }
        }
        builder.show()
    }

    private fun pickImageFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, IMAGE_PICK_CODE)
    }

    private fun takePhotoFromCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFile = createImageFile()
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            imageFile
        )
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        startActivityForResult(cameraIntent, REQUEST_CAMERA)
    }

    private fun startCropActivity(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1000, 1000)
            .withOptions(getCropOptions())
            .start(this)
    }

    private fun getCropOptions(): UCrop.Options {
        val options = UCrop.Options()
        options.setFreeStyleCropEnabled(true)
        options.setHideBottomControls(false) // Make sure controls are visible
        options.setShowCropGrid(true)
        options.setToolbarTitle("Crop Image") // Set a title
        options.setToolbarColor(ContextCompat.getColor(this, R.color.black))
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.black))
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.black))
        options.setToolbarWidgetColor(ContextCompat.getColor(this, R.color.white))
        return options
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                IMAGE_PICK_CODE -> {
                    val imageUri = data?.data
                    if (imageUri != null) {
                        startCropActivity(imageUri)
                    }
                }
                REQUEST_CAMERA -> {
                    cameraImageUri?.let { startCropActivity(it) }
                }
                UCrop.REQUEST_CROP -> {
                    val resultUri = UCrop.getOutput(data!!)
                    if (resultUri != null) {
                        croppedImageUri = resultUri
                        processCroppedImage()
                    }
                }
                UCrop.RESULT_ERROR -> {
                    val cropError = UCrop.getError(data!!)
                    Toast.makeText(this, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processCroppedImage() {
        lifecycleScope.launch {
            showLoadingState(true)
            val bitmap = withContext(Dispatchers.IO) {
                croppedImageUri?.let { uriToBitmap(it) }
            }

            bitmap?.let {
                processImage(it)
            } ?: run {
                withContext(Dispatchers.Main) {
                    showLoadingState(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to process image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Rest of your existing methods remain the same...
    // (loadStrip, loadTips, setLanguage, loadModels, createImageFile, showLoadingState,
    // uriToBitmap, processImage, classifyLeafOrNot, classifyDisease, showResultDialog,
    // openBottomSheet, assetFilePath, onPause, onResume, bottomDiseaseDetails)

    private fun loadStrip() {
        if(language=="eng"){
            val tickerTextView: TextView = findViewById(R.id.tickerText)
            tickerTextView.text = """
    Tips for Growing Tomato Crops:
    1. Soil Selection: For tomato crops, loamy soil is ideal as it is well-drained.
    2. Seed Preparation: Choose high-quality, disease-free seeds. Soak tomato seeds in water for some time before planting.
    3. Ideal Temperature: The temperature for tomato crops should range between 20-30 degrees Celsius.
    4. Irrigation: Keep the soil moist but avoid over-watering or under-watering.
    5. Fertilizer Use: Use natural fertilizers or compost for tomato crops.
    6. Best Time to Water: It's best to water in the morning.
    7. Plant Care: Prune the tomato plants and provide support for the plants as they grow.
    8. Pest and Disease Control: Tomato crops can be affected by blight, aphids, and whiteflies.
    9. Harvesting Time: Harvest tomatoes when they ripen and change color completely.
    10. Use Resistant Varieties: Plant tomato varieties that are resistant to late blight.
"""
            tickerTextView.post {
                val textWidth = tickerTextView.paint.measureText(tickerTextView.text.toString())
                val screenWidth = resources.displayMetrics.widthPixels
                val animation = TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 1.0f,
                    Animation.RELATIVE_TO_PARENT, -(textWidth / screenWidth + 1.0f),
                    Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f
                )
                animation.duration = 70000
                animation.repeatCount = Animation.INFINITE
                animation.repeatMode = Animation.RESTART
                tickerTextView.startAnimation(animation)
            }
        }
        else{
            val tickerTextView: TextView = findViewById(R.id.tickerText)
            tickerTextView.text = """
    ٹماٹر کی فصل اگانے کے لیے چند اہم تجاویز: 
    1. زمین کا انتخاب: ٹماٹر کی فصل کے لیے اچھی طرح سے جاذب مٹی (loamy soil) بہترین ہوتی ہے۔
    2. بیج کی تیاری: معیاری اور بیماری سے آزاد بیج کا انتخاب کریں۔ ٹماٹر کے بیج کو کچھ وقت کے لیے پانی میں بھگو کر رکھیں۔
    3. مناسب درجہ حرارت: ٹماٹر کی فصل کے لیے درجہ حرارت 20-30 ڈگری سیلسیئس کے درمیان ہونا چاہیے۔
    4. آبپاشی: زمین کو گہرا نم رکھیں مگر پانی کی کمی یا زیادتی سے بچیں۔
    5. کھاد کا استعمال: ٹماٹر کی فصل کے لیے قدرتی کھاد یا کمپوسٹ کا استعمال کریں۔
    6. پانی دینے کا وقت: صبح کے وقت پانی دینا بہتر رہتا ہے۔
    7. پودوں کی دیکھ بھال: ٹماٹر کے پودوں کی مناسب ٹہنیاں بنائیں اور پودوں کو سپورٹ دیں۔
    8. بیماریوں اور کیڑوں کا علاج: ٹماٹر کی فصل میں بلیٹ، افڈس، اور سفید مکھیوں کے حملے کا سامنا ہو سکتا ہے۔
    9. فصل کاٹنے کا وقت: ٹماٹر جب مکمل طور پر رنگ بدل کر پک جائیں تو ان کو توڑیں۔
    10. مضبوط اقسام استعمال کریں: ایسی ٹماٹر کی اقسام لگائیں جو لیٹ بلائٹ کے خلاف مزاحم ہوں۔
"""
            tickerTextView.post {
                val textWidth = tickerTextView.paint.measureText(tickerTextView.text.toString())
                val screenWidth = resources.displayMetrics.widthPixels
                val animation = TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, -(textWidth / screenWidth + 1.0f),
                    Animation.RELATIVE_TO_PARENT, 1.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f
                )
                animation.duration = 70000
                animation.repeatCount = Animation.INFINITE
                animation.repeatMode = Animation.RESTART
                tickerTextView.startAnimation(animation)
            }
        }
    }

    private fun loadTips() {
        if (language == "eng") {
            val tips = listOf(
                SeasonalTip("January", "❄️", "Cold weather – protect young plants"),
                SeasonalTip("February", "🌤️", "Start preparing nursery beds"),
                SeasonalTip("March", "🌱", "Ideal for seed sowing"),
                SeasonalTip("April", "☀️", "Watch for aphids and whiteflies"),
                SeasonalTip("May", "☀️", "High risk of early blight"),
                SeasonalTip("June", "🔥", "Keep soil moist, avoid heat stress"),
                SeasonalTip("July", "🌧️", "Ensure drainage – fungus alert"),
                SeasonalTip("August", "🌦️", "Check for late blight symptoms"),
                SeasonalTip("September", "🍁", "Harvest time for many varieties"),
                SeasonalTip("October", "🍂", "Prepare soil for next season"),
                SeasonalTip("November", "🌬️", "Remove dead plants, compost wisely"),
                SeasonalTip("December", "🧤", "Cover beds, maintain soil health")
            )
            val tipAdapter = SeasonalTipAdapter(tips)
            binding.seasonalRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.seasonalRecyclerView.adapter = tipAdapter
        } else {
            val tips = listOf(
                SeasonalTip("جنوری", "❄️", "سرد موسم – نوجوان پودوں کو بچائیں"),
                SeasonalTip("فروری", "🌤️", "نرسری بیڈ تیار کرنا شروع کریں"),
                SeasonalTip("مارچ", "🌱", "بیج بونے کے لیے بہترین وقت"),
                SeasonalTip("اپریل", "☀️", "افڈز اور سفید مکھیوں پر نظر رکھیں"),
                SeasonalTip("مئی", "☀️", "ابتدائی بلیٹ کا خطرہ زیادہ ہے"),
                SeasonalTip("جون", "🔥", "مٹی کو گیلا رکھیں، گرمی سے بچائیں"),
                SeasonalTip("جولائی", "🌧️", "نکاس کی تصدیق کریں – فنگس کا خطرہ"),
                SeasonalTip("اگست", "🌦️", "لیٹ بلیٹ کی علامات چیک کریں"),
                SeasonalTip("ستمبر", "🍁", "بہت سی اقسام کے لیے فصل کاٹنے کا وقت"),
                SeasonalTip("اکتوبر", "🍂", "اگلے موسم کے لیے مٹی تیار کریں"),
                SeasonalTip("نومبر", "🌬️", "مردہ پودوں کو ہٹا دیں، کمپوسٹ احتیاط سے کریں"),
                SeasonalTip("دسمبر", "🧤", "بیڈ کو ڈھانپیں، مٹی کی صحت برقرار رکھیں")
            )
            val tipAdapter = SeasonalTipAdapter(tips)
            binding.seasonalRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.seasonalRecyclerView.adapter = tipAdapter
        }
    }

    private fun setLanguage() {
        if (language == "eng") {
            binding.diseaseTest.setText("Classify Diseases")
            binding.recomendText.setText("Recommendations")
        } else {
            binding.diseaseTest.setText("بیماری کی پہچان")
            binding.recomendText.setText("سفارشات")
        }
    }

    private suspend fun loadModels() {
        withContext(Dispatchers.IO) {
            leafModel = Module.load(assetFilePath("resnet18_leaf_nonleaf.pt"))
            diseaseModel = Module.load(assetFilePath("resnet18_tomato_disease_scripted.pt"))
        }
    }

    private fun createImageFile(): File {
        val timestamp = System.currentTimeMillis()
        val storageDir = cacheDir
        return File.createTempFile("IMG_${timestamp}_", ".jpg", storageDir)
    }

    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            progressOverlay.visibility = View.VISIBLE
        } else {
            progressOverlay.visibility = View.GONE
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }

    private suspend fun processImage(bitmap: Bitmap) {
        if (leafModel == null || diseaseModel == null) {
            loadModels()
            if (leafModel == null || diseaseModel == null) {
                withContext(Dispatchers.Main) {
                    showLoadingState(false)
                    Toast.makeText(this@MainActivity, "Failed to load models", Toast.LENGTH_SHORT)
                        .show()
                }
                return
            }
        }

        val isLeaf = withContext(Dispatchers.Default) {
            classifyLeafOrNot(bitmap)
        }

        withContext(Dispatchers.Main) {
            showLoadingState(false)
            if (isLeaf.first) {
                lifecycleScope.launch {
                    showLoadingState(true)
                    val diseaseResult = withContext(Dispatchers.Default) {
                        classifyDisease(bitmap)
                    }
                    showLoadingState(false)
                    showResultDialog(bitmap, diseaseResult.first, diseaseResult.second)
                }
            } else {
                showResultDialog(bitmap, "Not a leaf", isLeaf.second)
            }
        }
    }

    private fun classifyLeafOrNot(bitmap: Bitmap): Pair<Boolean, Float> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            floatArrayOf(0.485f, 0.456f, 0.406f),
            floatArrayOf(0.229f, 0.224f, 0.225f)
        )

        val outputTensor = leafModel?.forward(IValue.from(inputTensor))?.toTensor()
        val scores = outputTensor?.dataAsFloatArray

        if (scores != null) {
            val expScores = scores.map { Math.exp(it.toDouble()) }
            val sumExp = expScores.sum()
            val softmax = expScores.map { (it / sumExp).toFloat() }

            val maxIdx = softmax.indices.maxByOrNull { softmax[it] } ?: -1
            val label = leafLabels[maxIdx]
            val confidence = softmax[maxIdx] * 100

            return Pair(label == "Leaf", confidence)
        }

        return Pair(false, 0f)
    }

    private fun classifyDisease(bitmap: Bitmap): Pair<String, Float> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            floatArrayOf(0.485f, 0.456f, 0.406f),
            floatArrayOf(0.229f, 0.224f, 0.225f)
        )

        val outputTensor = diseaseModel?.forward(IValue.from(inputTensor))?.toTensor()
        val scores = outputTensor?.dataAsFloatArray

        if (scores != null) {
            val expScores = scores.map { Math.exp(it.toDouble()) }
            val sumExp = expScores.sum()
            val softmax = expScores.map { (it / sumExp).toFloat() }

            val maxIdx = softmax.indices.maxByOrNull { softmax[it] } ?: -1
            val label = diseaseLabels[maxIdx]
            val confidence = softmax[maxIdx] * 100

            return Pair(label, confidence)
        }

        return Pair("Unknown", 0f)
    }

    private fun showResultDialog(bitmap: Bitmap, predictedClass: String, confidence: Float) {
        val dialogBinding = DialogImageResultBinding.inflate(LayoutInflater.from(this))
        dialogBinding.imageResult.setImageBitmap(bitmap)
        dialogBinding.diseaseName.text = "Result: $predictedClass"
        dialogBinding.accuracyScore.text = "Accuracy: %.2f%%".format(confidence)
        if (language == "urdu") {
            dialogBinding.recommendationsButton.setText("تجاویز ")
        }

        if (predictedClass != "Leaf" && predictedClass != "Non-Leaf" && predictedClass != "Not a leaf") {
            dialogBinding.recommendationsButton.visibility = View.VISIBLE
        } else {
            dialogBinding.recommendationsButton.visibility = View.GONE
        }

        dialogBinding.recommendationsButton.setOnClickListener {
            when (predictedClass) {
                "Late Blight" -> {
                    if (language == "eng") {
                        openBottomSheet("", "", "", "", "", "", "", "", "")
                    } else {
                        openBottomSheet(
                            "مضبوط اقسام استعمال کریں: ایسی ٹماٹر کی اقسام لگائیں جو لیٹ بلائٹ کے خلاف مزاحم ہوں۔",
                            "صفائی کا خیال رکھیں: متاثرہ پودوں کو ہٹا دیں اور باقی ماندہ کو مناسب طریقے سے ٹھکانے لگائیں۔",
                            "احتیاط سے پانی دیں: اوپر سے پانی دینے سے گریز کریں اور ڈرپ آبپاشی کا استعمال کریں۔",
                            "فنجی سائیڈز کا استعمال کریں: پودوں کو بچانے کے لیے تانبا یا کلورو تھالونیل پر مبنی فنجی سائیڈز استعمال کریں۔",
                            "موسم کی نگرانی کریں: موسم کی پیش گوئی پر نظر رکھیں اور ٹھنڈے و نم حالات سے خبردار رہیں۔",
                            "تانبے پر مبنی فنجی سائیڈز (مثلاً کاپر آکسی کلورائیڈ)",
                            "کلورو تھالونیل (مثلاً ڈیکونل)",
                            "مینکوزیب (مثلاً ڈیتھین M-45)",
                            "فنجی سائیڈز کا استعمال موسم کے شروع میں کریں اور ہر 7 سے 10 دن بعد دہرائیں"
                        )
                    }
                }

                "Early Blight" -> {
                    if (language == "eng") {
                        openBottomSheet(
                            "Rotate Crops: Change the planting location every 2–3 years to avoid soil-borne pathogens.",
                            "Remove Infected Leaves: Trim and destroy any infected or yellowing lower leaves.",
                            "Water at Base: Avoid overhead watering; use drip irrigation to reduce leaf wetness.",
                            "Use Resistant Varieties: Choose tomato varieties that are resistant to early blight.",
                            "Apply Mulch: Add straw or plastic mulch to prevent soil splash and reduce infection",
                            "Daconil",
                            "Mancozeb",
                            "Copper-based Fungicides",
                            "Azoxystrobin"
                        )
                    } else {
                        openBottomSheet(
                            "فصلوں کو تبدیل کریں: ہر 2–3 سال بعد پودے لگانے کی جگہ تبدیل کریں تاکہ مٹی میں موجود بیماریوں سے بچا جا سکے۔",
                            "متاثرہ پتوں کو ہٹائیں: پیلے یا بیمار پتوں کو کاٹ کر تلف کریں۔",
                            "نیچے سے پانی دیں: پتے گیلے نہ ہوں، اس کے لیے ڈرِپ آبپاشی کا استعمال کریں۔",
                            "مزاحم اقسام کا انتخاب کریں: ایسی ٹماٹر اقسام لگائیں جو جلدی دھبوں کے خلاف مزاحمت رکھتی ہوں۔",
                            "ملچ کا استعمال کریں: تنکے یا پلاسٹک کی ملچ ڈالیں تاکہ مٹی کے چھینٹے اور بیماری سے بچا جا سکے۔",
                            "ڈاکونیل",
                            "مینکوزیب",
                            "کاپر پر مبنی فنجی سائیڈز",
                            "ایزوکسیسٹرابن"
                        )
                    }
                }

                "Healthy" -> {
                    if (language == "eng") {
                        openBottomSheet(
                            "Choose Healthy, Disease-Resistant Varieties: Start with strong, certified, and blight-resistant seeds or seedlings.",
                            "Ensure Proper Sunlight: Tomatoes need at least 6–8 hours of direct sunlight daily.",
                            "Use Well-Drained, Nutrient-Rich Soil: Mix compost or organic matter to improve soil fertility",
                            "Water Deeply but Less Frequently: Water at the base to encourage deep root growth and avoid leaf diseases.",
                            "Practice Regular Monitoring and Maintenance: Check leaves weekly for pests or disease and act early.",
                            "",
                            "",
                            "",
                            ""
                        )
                    } else {
                        openBottomSheet(
                            "صحتمند اور بیماری سے محفوظ اقسام کا انتخاب کریں: مضبوط اور تصدیق شدہ بیجوں یا پودوں سے آغاز کریں۔",
                            "مناسب دھوپ کو یقینی بنائیں: ٹماٹر کو روزانہ کم از کم 6 سے 8 گھنٹے دھوپ کی ضرورت ہوتی ہے۔",
                            "اچھی نکاسی والی، غذائیت سے بھرپور مٹی استعمال کریں: کمپوسٹ یا نامیاتی مادے شامل کریں۔",
                            "گہرائی سے پانی دیں لیکن کم بار: نیچے سے پانی دیں تاکہ جڑیں مضبوط ہوں اور پتوں کی بیماریاں کم ہوں۔",
                            "باقاعدہ نگرانی اور دیکھ بھال کریں: ہر ہفتے پتے چیک کریں اور فوری کارروائی کریں۔",
                            "",
                            "",
                            "",
                            ""
                        )
                    }
                }

                "Septoria" -> {
                    if (language == "eng") {
                        openBottomSheet(
                            "Remove Lower Leaves: Prune lower leaves, especially if they touch the soil or show signs of spots.",
                            "Avoid Overhead Watering: Use drip irrigation to keep leaves dry and reduce disease spread.",
                            "Use Disease-Free Seeds/Seedlings: Always start with clean, certified seeds or seedlings.",
                            "Rotate Crops: Avoid planting tomatoes or related crops (like potatoes, eggplants) in the same spot every year.",
                            "Improve Air Circulation: Space plants properly and stake them to promote airflow and reduce humidity around leaves.",
                            "Mancozeb",
                            "Neem Oil (for mild cases and organic control)",
                            "Copper-based Fungicides",
                            "Chlorothalonil"
                        )
                    } else {
                        openBottomSheet(
                            "نیچے کے پتے ہٹا دیں: ان پتوں کو کاٹیں جو مٹی کو چھوتے ہیں یا دھبے دکھاتے ہیں۔",
                            "اوپر سے پانی دینے سے گریز کریں: پتوں کو خشک رکھنے کے لیے ڈرِپ آبپاشی استعمال کریں۔",
                            "بیماری سے پاک بیج یا پودے استعمال کریں: ہمیشہ صاف اور تصدیق شدہ پودوں سے آغاز کریں۔",
                            "فصلوں کو تبدیل کریں: ہر سال ایک ہی جگہ پر ٹماٹر یا اس سے متعلقہ فصلیں نہ اگائیں۔",
                            "ہوا کی روانی بہتر بنائیں: پودوں کو فاصلے پر لگائیں تاکہ ہوا کا گزر ہو اور نمی کم ہو۔",
                            "مینکوزیب",
                            "نیم کا تیل (ہلکے کیسز میں اور نامیاتی طریقے سے)",
                            "کاپر پر مبنی فنجی سائیڈز",
                            "کلورتھالونیل"
                        )
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val widthInDp = 300
        val scale = resources.displayMetrics.density
        val widthInPx = (widthInDp * scale + 0.5f).toInt()
        dialog.window?.setLayout(widthInPx, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun openBottomSheet(
        v1: String, v2: String, v3: String, v4: String, v5: String,
        v6: String, v7: String, v8: String, v9: String
    ) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val binding = BottomRecomendationsBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(binding.root)

        if (language == "eng") {
            binding.cr1.visibility = View.VISIBLE
            binding.cr2.visibility = View.VISIBLE
            binding.cr3.visibility = View.VISIBLE
            binding.cr4.visibility = View.VISIBLE
            binding.cr5.visibility = View.VISIBLE
            binding.cr6.visibility = View.VISIBLE
            binding.cr7.visibility = View.VISIBLE
            binding.cr8.visibility = View.VISIBLE
            binding.cr9.visibility = View.VISIBLE
        } else {
            binding.cr1.visibility = View.GONE
            binding.cr2.visibility = View.GONE
            binding.cr3.visibility = View.GONE
            binding.cr4.visibility = View.GONE
            binding.cr5.visibility = View.GONE
            binding.cr6.visibility = View.GONE
            binding.cr7.visibility = View.GONE
            binding.cr8.visibility = View.GONE
            binding.cr9.visibility = View.GONE
        }

        val behavior = BottomSheetBehavior.from(binding.root.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true

        if (v1.isNotEmpty()) {
            binding.tv1.text = v1
            binding.tv2.text = v2
            binding.tv3.text = v3
            binding.tv4.text = v4
            binding.tv5.text = v5
            binding.tv6.text = v6
            binding.tv7.text = v7
            binding.tv8.text = v8
            binding.tv9.text = v9
        }

        if (v6.isEmpty() && v1.isNotEmpty()) {
            binding.laypest.visibility = View.GONE
            binding.tvpest.visibility = View.GONE
        }

        binding.applyRecommendationsButton.setOnClickListener {
            Toast.makeText(this, "Applying recommendations...", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun assetFilePath(assetName: String): String {
        val file = File(filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(slideRunnable)
    }

    override fun onResume() {
        super.onResume()
        sliderHandler.postDelayed(slideRunnable, 2000)
    }

    private fun bottomDiseaseDetails() {
        val sheetDialog = BottomSheetDialog(this)
        val binding = BottomSheetDiseaseDetailsBinding.inflate(layoutInflater)
        sheetDialog.setContentView(binding.root)

        val diseases = arrayOf("Early Blight", "Healthy", "Late Blight", "Septoria")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, diseases)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.diseaseSpinner.adapter = adapter

        binding.symptomsArrow.setOnClickListener {
            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "Tell me the symptoms of $selectedDisease  very clearly in clear manners for this  tomato leaf disease"
                )
            }
            startActivity(intent)
        }

        binding.recommendationsArrow.setOnClickListener {
            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "Tell me the Recommendations and Prevention Tips for tomato leaf disease $selectedDisease  "
                )
            }
            startActivity(intent)
        }

        binding.pesticidesArrow.setOnClickListener {
            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "What are the pesticides for this tomato leaf disease  $selectedDisease "
                )
            }
            startActivity(intent)
        }


        binding.causeArrow.setOnClickListener {
            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "Tell me to the point  causes and spread  for this tomato leaf disease  $selectedDisease "
                )
            }
            startActivity(intent)
        }


        binding.conditionsArrow.setOnClickListener {

            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "Tell me favorable conditions  this tomato leaf disease  $selectedDisease , to the point "
                )
            }
            startActivity(intent)


        }

        binding.varietiesArrow.setOnClickListener {
            val selectedDisease = binding.diseaseSpinner.selectedItem.toString()
            val intent = Intent(this, ActivityBot::class.java).apply {
                putExtra(
                    "propmt",
                    "Tell me  name  resistant varieties  for this tomato leaf disease  $selectedDisease and  then explain each  "
                )
            }
            startActivity(intent)

        }


        sheetDialog.show()
    }


}