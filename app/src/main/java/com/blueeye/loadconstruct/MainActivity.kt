package com.blueeye.loadconstruct


import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), DialogInterface.OnClickListener, TextToSpeech.OnInitListener {

    //region 기본 설정
    val TAG: String = "로그"
    //GPS
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null // 현재 위치를 가져오기 위한 변수
    lateinit var mLastLocation: Location // 위치 값을 가지고 있는 객체
    internal lateinit var mLocationRequest: LocationRequest // 위치 정보 요청의 매개변수를 저장하는
    private val REQUEST_PERMISSION_LOCATION = 10
    //STT/TTS
    private var tts: TextToSpeech? = null
    private var speechRecognizer1: SpeechRecognizer? = null
    private var speechRecognizer2: SpeechRecognizer? = null
    private val speechRecognizerIntent1 = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private val speechRecognizerIntent2 = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    //카메라
    private var photoPath = ""
    private var videoPath = ""
    private var mWebViewImageUpload: ValueCallback<Array<Uri>>? = null
    //endregion

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main)

        //region WebView
        val webView: WebView = findViewById(R.id.webView)
        var settings = webView.settings
        settings.javaScriptEnabled = true //자바스크립트 허용
        settings.domStorageEnabled = true //로컬 저장 허용
        settings.setSupportMultipleWindows(true) //새창 허용
        settings.loadWithOverviewMode = true //메타태그 허용
        settings.javaScriptCanOpenWindowsAutomatically = true //자바스크립트 새창 허용
        settings.useWideViewPort = true //화면 사이즈 맟춤 허용
        settings.setSupportZoom(false) //화면 줌 허용 여부
        settings.builtInZoomControls = false //화면 확대 축소 허용 여부
        settings.allowFileAccess = true

        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.apply {

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            //오레오보다 같거나 크면
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                webView.loadUrl("https://green-t.theblueeye.com/")
            }else{
                webView.loadUrl("http://green-t.theblueeye.com/")
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    val newWebView = WebView(this@MainActivity)
                    val webSettings = newWebView.settings
                    webSettings.javaScriptEnabled = true
                    val dialog = Dialog(this@MainActivity)
                    dialog.setContentView(newWebView)
                    dialog.show()
                    newWebView.webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) {
                            dialog.dismiss()
                        }
                    }
                    (resultMsg.obj as WebView.WebViewTransport).webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    onJsAlert(message!!, result!!)
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    onJsConfirm(message!!, result!!)
                    return true
                }

                override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                    try{
                        mWebViewImageUpload = filePathCallback!!
                        val intentArray: Array<Intent?>

                        var takePictureIntent : Intent?
                        takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if(takePictureIntent.resolveActivity(packageManager) != null){
                            var photoFile : File?

                            photoFile = createImageFile()
                            takePictureIntent.putExtra("PhotoPath",photoPath)

                            if(photoFile != null){
                                photoPath = "file:${photoFile.absolutePath}"
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                            }
                            else takePictureIntent = null
                        }

                        var takeVideoIntent : Intent?
                        takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        if(takeVideoIntent.resolveActivity(packageManager) != null){
                            var videoFile : File?

                            videoFile = createVideoFile()
                            takeVideoIntent.putExtra("VideoPath",videoPath)

                            if(videoFile != null){
                                videoPath = "file:${videoFile!!.absolutePath}"
                                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(videoFile))
                                takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                                // takeVideoIntent.putExtra( MediaStore.EXTRA_SIZE_LIMIT, 15000000L ); //about 14Mb
                                takeVideoIntent.putExtra(
                                    MediaStore.EXTRA_DURATION_LIMIT,
                                    15
                                ) //15 sec
                            }
                            else takeVideoIntent = null
                        }

                        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                        contentSelectionIntent.type = "*/*"
                        contentSelectionIntent.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf("image/*", "video/*", "*/*")
                        )


                        if(photoPath != ""){
                            intentArray = takePictureIntent?.let { arrayOf(it) } ?: arrayOfNulls(0)
                        }else{
                            intentArray = takeVideoIntent?.let { arrayOf(it) } ?: arrayOfNulls(0)
                        }

                        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Choose file")
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                        launcher.launch(chooserIntent)
                    }
                    catch (e : Exception){ }
                    return true
                }

            }

        }
        //endregion

        //region GPS
        //interval 설정
        mLocationRequest =  LocationRequest.create().apply {
            interval =  10000 //10초
            fastestInterval = 5000 //5초
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime = 10000 //10초

        }

        //확인 후 연결
        if (checkPermissionForLocation(this)) {
            gpsAuthAllow()
        }
        //endregion

        //region TTS/STT
        // tts에 TextToSpeech 값 넣어줌
        tts = TextToSpeech(this, this)

        // stt Recognizer 생성
        speechRecognizerIntent1.apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName); // 여분의 키
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); // 언어 설정
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        }
        speechRecognizerIntent2.apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName); // 여분의 키
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); // 언어 설정
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        }
        speechRecognizer1 = SpeechRecognizer.createSpeechRecognizer(this).apply {// 새 SpeechRecognizer 를 만드는 팩토리 메서드
            setRecognitionListener(recognitionListener1()) // 리스너 설정
            //startListening(speechRecognizerIntent)
        }
        speechRecognizer2 = SpeechRecognizer.createSpeechRecognizer(this).apply {// 새 SpeechRecognizer 를 만드는 팩토리 메서드
            setRecognitionListener(recognitionListener2()) // 리스너 설정
        }

//        //STT interval 설정
//        val timer = object: CountDownTimer(60000, 20000) {
//            override fun onTick(millisUntilFinished: Long) {
//
//                CoroutineScope(Dispatchers.Main).launch{
//                    speechRecognizer?.apply { startListening(speechRecognizerIntent) }
//                }
//            }
//            override fun onFinish() {
//                // do something
//            }
//        }
//        timer.start()
        //endregion

        //region PUSH Notification
        /** DynamicLink 수신확인 */
        initDynamicLink()
        //endregion
    }

    private fun sayGreenT(msg: String){
        when(msg){
            "명령어" ->{
                startTTS( "네 안녕하세요?")
            }
            "ok" ->{
                webView.post(Runnable { //웹뷰로 전송
                    webView.loadUrl("javascript:setSubmit()")
                })

            }
            "no" ->{
                webView.post(Runnable { //웹뷰로 전송
                    webView.loadUrl("javascript:setCancle()")
                })


            }
            else ->{
                startTTS( "아니예요.")
            }
        }

    }

    //region 푸시 알림
    /** DynamicLink */

    private fun initDynamicLink() {
        val dynamicLinkData = intent.extras
        if (dynamicLinkData != null) {
            var dataStr = "DynamicLink 수신받은 값\n"
            for (key in dynamicLinkData.keySet()) {
                dataStr += "key: $key / value: ${dynamicLinkData.getString(key)}\n"
            }

        }
    }
    fun receiveToken(){
        /** FCM설정, Token값 가져오기 */
        MyFirebaseMessagingService().getFirebaseToken()
    }

    //endregion

    //region 카메라/사진 불러오기
    fun createImageFile(): File? {
        @SuppressLint("SimpleDateFormat")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    fun createVideoFile(): File? {
        @SuppressLint("SimpleDateFormat")
        var file_name: String? = SimpleDateFormat("yyyy_mm_ss").format(Date())
        val new_name = "file_" + file_name + "_"
        val sd_directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(new_name, ".mp4", sd_directory)
    }

    val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == RESULT_OK) {
            val intent = result.data

            if(intent == null){ //바로 사진을 찍어서 올리는 경우
                var results: Array<Uri>? = null
                if(photoPath != ""){
                    results = arrayOf(Uri.parse(photoPath))
                }else{
                    results = arrayOf(Uri.parse(videoPath))
                }

                mWebViewImageUpload!!.onReceiveValue(results!!)
            }
            else{ //사진 앱을 통해 사진을 가져온 경우
                val results = intent!!.data!!
                mWebViewImageUpload!!.onReceiveValue(arrayOf(results!!))
            }
        }
        else{ //취소 한 경우 초기화
            mWebViewImageUpload!!.onReceiveValue(null)
            mWebViewImageUpload = null
        }
    }


    //endregion

    //region TTS/STT
    // RecognitionListener 사용한 예제
    private fun runSTT1() {
        CoroutineScope(Dispatchers.Main).launch{
            speechRecognizer1?.apply { startListening(speechRecognizerIntent1) }
        }
        Log.d(TAG, "runSTT1()")
    }

    private fun runSTT2() {
        CoroutineScope(Dispatchers.Main).launch{
            speechRecognizer2?.apply { startListening(speechRecognizerIntent2) }
        }
        Log.d(TAG, "runSTT2()")
    }

    //제보
    private fun recognitionListener1() = object : RecognitionListener {
        // 말하기 시작할 준비가되면 호출
        override fun onReadyForSpeech(params: Bundle?) = Toast.makeText(this@MainActivity, "음성인식 시작", Toast.LENGTH_SHORT).show()
        // 말하기 시작했을 때 호출
        override fun onBeginningOfSpeech(){}
        // 입력받는 소리의 크기를 알려줌
        override fun onRmsChanged(rmsdB: Float) {}
        // 말을 시작하고 인식이 된 단어를 buffer에 담음
        override fun onBufferReceived(buffer: ByteArray?) {}
        // 부분 인식 결과를 사용할 수 있을 때 호출
        override fun onPartialResults(partialResults: Bundle?) {
            webView.post(Runnable { webView.loadUrl("javascript:setDoing()") })
        }
        // 향후 이벤트를 추가하기 위해 예약
        override fun onEvent(eventType: Int, params: Bundle?) {   }
        //음성이 끝났을 때 호출
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech()")
        }
        // 네트워크 또는 인식 오류가 발생했을 때 호출
        override fun onError(error: Int) {

            //  webView.post(Runnable { webView.loadUrl("javascript:setError('음성을 재인식합니다.','1')") })

        }
        // 인식 결과가 준비되면 호출
        override fun onResults(results: Bundle) {
            Log.d(TAG, "onResults()")
            if(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0].isNotEmpty()){
                val matches = results!!.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                if (matches!!.size != 0) {
                    webView.post(Runnable { //웹뷰로 전송
                        webView.loadUrl("javascript:setText('${matches.toString()}')")
                    })

                }else{
                    setStt()
                }
            }else{
                Log.d(TAG, "onResults() fail")
            }
        }
    }

    //답변
    private fun recognitionListener2() = object : RecognitionListener {
        // 말하기 시작할 준비가되면 호출
        override fun onReadyForSpeech(params: Bundle?) = Toast.makeText(this@MainActivity, "음성인식 시작", Toast.LENGTH_SHORT).show()
        // 말하기 시작했을 때 호출
        override fun onBeginningOfSpeech(){}
        // 입력받는 소리의 크기를 알려줌
        override fun onRmsChanged(rmsdB: Float) {}
        // 말을 시작하고 인식이 된 단어를 buffer에 담음
        override fun onBufferReceived(buffer: ByteArray?) {}
        // 부분 인식 결과를 사용할 수 있을 때 호출
        override fun onPartialResults(partialResults: Bundle?) {}
        // 향후 이벤트를 추가하기 위해 예약
        override fun onEvent(eventType: Int, params: Bundle?) {}
        //음성이 끝났을 때 호출
        override fun onEndOfSpeech() {}
        // 네트워크 또는 인식 오류가 발생했을 때 호출
        override fun onError(error: Int) {
            webView.post(Runnable { webView.loadUrl("javascript:setError('음성을 재인식합니다.','2')") })

        }
        // 인식 결과가 준비되면 호출
        override fun onResults(results: Bundle) {
            Log.d(TAG, "onResults()")
            if(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0].isNotEmpty()){
                val matches = results!!.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                if (matches!!.size != 0) {
                    when(matches.toString()){
                        "[네]","[예]","[응]","[어]","[그래]","[등록해]","[등록]" ->{
                            sayGreenT("ok")
                        }
                        "[아니요]","[아니오]","[아니]" ->{
                            sayGreenT("no")
                        }
                        else ->{
                            Log.d(TAG, "onResults() fail")
                        }
                    }
                }
            }else{
                Log.d(TAG, "onResults() fail")
            }
        }
    }
    fun setStt(){

        runSTT1() //제보시
    }


    fun setAnswer(){

        runSTT2() //답변시
    }

    // TTS 예제
    private fun startTTS(msg: String) {
        tts!!.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    // TextToSpeech override 함수
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // set US English as language for tts
            val result = tts!!.setLanguage(Locale.KOREA)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                //doSomething
            } else {
                //doSomething
            }
        } else {
            //doSomething
        }
    }

    override fun onDestroy() {

        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }

        if (speechRecognizer1 != null) {
            speechRecognizer1!!.stopListening()
        }

        if (speechRecognizer2 != null) {
            speechRecognizer2!!.stopListening()
        }

        super.onDestroy()
    }
    //endregion

    //region GPS
    //내 위치를 가져오는 코드
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient //자동으로 gps값을 받아온다.
    lateinit var locationCallback: LocationCallback //gps응답 값을 가져온다.
    //lateinit: 나중에 초기화 해주겠다는 의미

    //현재 위치 찾기
    private fun gpsAuthAllow() {
        //Log.d(TAG, "gpsAuthAllow()")

        //FusedLocationProviderClient의 인스턴스를 생성.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Log.d(TAG, "gpsAuthAllow() 두 위치 권한중 하나라도 없는 경우 ")
            return
        }
        //Log.d(TAG, "gpsAuthAllow() 위치 권한이 하나라도 존재하는 경우")
        // 기기의 위치에 관한 정기 업데이트를 요청하는 메서드 실행
        // 지정한 루퍼 스레드(Looper.myLooper())에서 콜백(mLocationCallback)으로 위치 업데이트를 요청합니다.
        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    // 시스템으로 부터 위치 정보를 콜백으로 받음
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // Log.d(TAG, "onLocationResult()")
            // 시스템에서 받은 location 정보를 onLocationChanged()에 전달
            val lastLocation = locationResult.lastLocation
            if(lastLocation != null){
                //  Log.d(TAG, "onLocationResult() true")
                onLocationChanged(lastLocation)
            }else{
                // Log.d(TAG, "onLocationResult() false")
            }
        }
    }

    // 시스템으로 부터 받은 위치정보를 화면에 갱신해주는 메소드
    fun onLocationChanged(location: Location){
        //Log.d(TAG, "onLocationChanged()")
        val webView: WebView = findViewById(R.id.webView)
        mLastLocation =location
       // Log.d("위치: ", "${location.latitude}, ${location.longitude}")
        webView.post(Runnable { webView.loadUrl("javascript:setGps("+ location.latitude + ", " + location.longitude +")") })

    }
    //endregion

    //region 권한 설정
    // 위치 권한이 있는지 확인하는 메서드
    private fun checkPermissionForLocation(context: Context): Boolean {
        val REQUEST_CODE = 1
        //Log.d(TAG, "checkPermissionForLocation()")
        // Android 6.0 Marshmallow 이상에서는 위치 권한에 추가 런타임 권한이 필요
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            ) {
                //Log.d(TAG, "checkPermissionForLocation() 권한 상태 : O")
                true
            } else {
                // 권한이 없으므로 권한 요청 알림 보내기
                val permissions: Array<String> = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                //Log.d(TAG, "checkPermissionForLocation() 권한 상태 : X")
                ActivityCompat.requestPermissions(this, permissions, 0)
                gpsAuthAllow()
                false
            }



        } else {
            true
        }
        // 오디오 permission 확인
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    // 사용자에게 권한 요청 후 결과에 대한 처리 로직

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //Log.d(TAG, "onRequestPermissionsResult()")
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Log.d(TAG, "onRequestPermissionsResult() _ 권한 허용 클릭")
                gpsAuthAllow()
                // View Button 활성화 상태 변경
            } else {
                //Log.d(TAG, "onRequestPermissionsResult() _ 권한 허용 거부")
                Toast.makeText(this, "권한이 없어 해당 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //endregion

    //region 기본 액션 설정
    fun onJsConfirm(message : String, result : JsResult) : Unit {
        var builder = AlertDialog.Builder(this)
        builder.setTitle("알 림")
        builder.setMessage(message)

        // 버튼 클릭 이벤트
        var listener = DialogInterface.OnClickListener { _, clickEvent ->
            when (clickEvent) {
                DialogInterface.BUTTON_POSITIVE ->{
                    result!!.confirm()
                }

                DialogInterface.BUTTON_NEUTRAL -> {
                    result!!.cancel()
                }
            }
        }
        builder.setPositiveButton(android.R.string.ok, listener)
        builder.setNeutralButton(android.R.string.cancel, listener)
        builder.show()
    }

    fun onJsAlert(message : String, result : JsResult) : Unit{
        var builder = AlertDialog.Builder(this)
        builder.setTitle("알 림")
        builder.setMessage(message)

        // 버튼 클릭 이벤트
        var listener = DialogInterface.OnClickListener { _, clickEvent ->
            when (clickEvent) {
                DialogInterface.BUTTON_POSITIVE ->{
                    result!!.confirm()
                }
            }
        }
        builder.setPositiveButton(android.R.string.ok, listener)
        builder.show()
    }
    private var doubleClicked = false
    private var clicked = false

    override fun onBackPressed() {
//        super.onBackPressed()

        if (doubleClicked == true){
            finish()
        }
        doubleClicked = true
        Toast.makeText(this, "뒤로 버튼을 한번 더 누르면 종료합니다.", Toast.LENGTH_SHORT).show()

        Handler().postDelayed(Runnable {
            doubleClicked = false
        },1000)
    }

    override fun onClick(p0: DialogInterface?, p1: Int) {
        val pid = android.os.Process.myPid()

        when(p1){
            -1 -> {
                android.os.Process.killProcess(pid)
                finish()
            }

            0 -> {
                //취소
            }
        }
    }
    //endregion

    //region WebView 통신
    /** Instantiate the interface and set the context  */
    inner class WebAppInterface(private val mContext: Context) {

        //web -> android TTS 구동
        @JavascriptInterface
        fun setTTS(msg: String){
            Log.d(TAG, msg)
            // 딜레이를 1초 주기
            tts!!.playSilence(1000, TextToSpeech.QUEUE_ADD, null);
            tts!!.speak(msg, TextToSpeech.QUEUE_ADD, null, "");
            val toast = Toast.makeText(baseContext, msg, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
        }

        @JavascriptInterface
        fun setGuide(msg: String, sub: String, idx: Int, lat: String, lng: String, addr: String){
            val toast = Toast.makeText(baseContext, msg, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();

            tts!!.playSilence(1000, TextToSpeech.QUEUE_ADD, null);
            tts!!.speak(msg, TextToSpeech.QUEUE_ADD, null, "");

            Handler(Looper.getMainLooper()).postDelayed({
                webView.post(Runnable { //웹뷰로 전송
                    webView.loadUrl("javascript:setQuest('$sub',$idx,'$lat','$lng', '$addr')");
                });

                var ttsMsg = "";
                when(sub){
                    "기상" -> {
                        ttsMsg = sub + "으로 통제되고 있습니까?";
                    }
                    else ->{
                        ttsMsg = sub + "가 진행중입니까?";
                    }
                }
                // 딜레이를 1초 주기
                tts!!.playSilence(1000, TextToSpeech.QUEUE_ADD, null);
                tts!!.speak(ttsMsg, TextToSpeech.QUEUE_ADD, null, "");
            }, 3000)

        }//

        @JavascriptInterface
        fun setGps(){
            gpsAuthAllow()
        }

        @JavascriptInterface
        fun setGps2(){
            Log.d("위치: ", "setGps2")
            gpsAuthAllow()
        }

        @JavascriptInterface
        fun stopGps(){
            //mLocationRequest = null;
        }

        @JavascriptInterface
        fun setStt(){

            runSTT1() //제보시
        }

        @JavascriptInterface
        fun setAnswer(){
            Handler(Looper.getMainLooper()).postDelayed({
                runSTT2() //답변시
            }, 3000)
        }

        //디바이스별 토큰 받아오기
        @JavascriptInterface
        fun setToken(){
            FirebaseMessaging.getInstance().token.addOnSuccessListener {
                Log.d(TAG, "token=${it}")
                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.d(TAG, "Fetching FCM registration token failed ${task.exception}")
                        return@OnCompleteListener
                    }
                    var deviceToken = task.result
                    Log.d(TAG, "deviceToken $deviceToken")
                    webView.post(Runnable { //웹뷰로 전송
                        webView.loadUrl("javascript:setToken('${deviceToken.toString()}')")
                    })
                })
            }
        }
        //stt 중지
        @JavascriptInterface
        fun stopStt(){

            if (tts != null) {
                tts!!.stop()
                tts!!.shutdown()
            }

            if (speechRecognizer1 != null) {
                speechRecognizer1!!.destroy();
                speechRecognizer1!!.cancel();
                speechRecognizer1!!.stopListening()
                speechRecognizer1 = null;
            }

            if (speechRecognizer2 != null) {
                speechRecognizer2!!.destroy();
                speechRecognizer2!!.cancel();
                speechRecognizer2!!.stopListening()
                speechRecognizer2 = null;
            }
        }
    }
    //endregion


}