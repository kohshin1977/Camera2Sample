package com.kohshin.camera2sample



import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
   val PERMISION_CAMERA        = 200
   val PERMISION_WRITE_STORAGE = 1000
   val PERMISION_READ_STORAGE  = 1001

    /**
     * 各レイアウトオブジェクト変数を生成
     */
    private lateinit var shutterButton : ImageButton
    private lateinit var numberPicker  : NumberPicker
    private lateinit var previewView   : TextureView
    private lateinit var imageReader   : ImageReader

    /**
     * 各種変数初期化
     */
    private lateinit var previewRequestBuilder : CaptureRequest.Builder
    private lateinit var previewRequest        : CaptureRequest
    private var backgroundHandler              : Handler?                = null
    private var backgroundThread               : HandlerThread?          = null
    private var cameraDevice                   : CameraDevice?           = null
    private lateinit var captureSession        : CameraCaptureSession


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /**
         * ストレージ読み書きパーミッションの確認
         */
        val writePermission  = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val readPermission  = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        if ( (writePermission != PackageManager.PERMISSION_GRANTED) || (readPermission != PackageManager.PERMISSION_GRANTED) ) {
            requestStoragePermission()
        }

        previewView = findViewById(R.id.mySurfaceView)
        previewView.surfaceTextureListener = surfaceTextureListener
        startBackgroundThread()


        shutterButton = findViewById(R.id.Shutter);

        /**
         * シャッターボタンにイベント生成
         */
        shutterButton.setOnClickListener {
            // フォルダーを使用する場合、あるかを確認
            val appDir = File(Environment.getExternalStorageDirectory(), "Camera2Sample")

            if (!appDir.exists()) {
                // なければ、フォルダーを作る
                appDir.mkdirs()
            }

            try {
                val filename = "picture.jpg"
                var savefile : File? = null

                /**
                 * プレビューの更新を止める
                 */
                captureSession.stopRepeating()
                if (previewView.isAvailable) {

                    savefile = File(appDir, filename)
                    val fos = FileOutputStream(savefile)
                    val bitmap: Bitmap = previewView.bitmap
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()
                }

                if (savefile != null) {
                    Log.d("edulog", "Image Saved On: $savefile")
                    Toast.makeText(this, "Saved: $savefile", Toast.LENGTH_SHORT).show()
                }

            } catch (e: CameraAccessException) {
                Log.d("edulog", "CameraAccessException_Error: $e")
            } catch (e: FileNotFoundException) {
                Log.d("edulog", "FileNotFoundException_Error: $e")
            } catch (e: IOException) {
                Log.d("edulog", "IOException_Error: $e")
            }

            /**
             * プレビューを再開
             */
            captureSession.setRepeatingRequest(previewRequest, null, null)
        }
    }



    /**
     * カメラをバックグラウンドで実行
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * TextureView Listener
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        // TextureViewが有効になった
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
        {
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG,2)
            openCamera()
        }

        // TextureViewのサイズが変わった
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) { }

        // TextureViewが更新された
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) { }

        // TextureViewが破棄された
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean
        {
            return false
        }
    }

    /**
     * カメラ起動処理関数
     */
    private fun openCamera() {
        /**
         * カメラマネジャーの取得
         */
        val manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            /**
             * カメラIDの取得
             */
            val camerId: String = manager.cameraIdList[0]

            /**
             * カメラ起動パーミッションの確認
             */
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }

            /**
             * カメラ起動
             */
            manager.openCamera(camerId, stateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * カメラ利用許可取得ダイアログを表示
     */
    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Check")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISION_CAMERA)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISION_CAMERA)
        }
    }

    /**
     * カメラ状態取得コールバック関数
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        /**
         * カメラ接続完了
         */
        override fun onOpened(cameraDevice: CameraDevice) {
            this@MainActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        /**
         * カメラ切断
         */
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            this@MainActivity.cameraDevice = null
        }

        /**
         * カメラエラー
         */
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }
    }

    /**
     * カメラ画像生成許可取得ダイアログを表示
     */
    private fun createCameraPreviewSession()
    {
        try
        {
            val texture = previewView.surfaceTexture
            texture.setDefaultBufferSize(previewView.width, previewView.height)

            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                object : CameraCaptureSession.StateCallback()
                {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession)
                    {
                        if (cameraDevice == null) return
                        try
                        {
                            captureSession = cameraCaptureSession
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            previewRequest = previewRequestBuilder.build()
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, Handler(backgroundThread?.looper))
                        } catch (e: CameraAccessException) {
                            Log.e("erfs", e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        //Tools.makeToast(baseContext, "Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e("erf", e.toString())
        }
    }

    private fun requestStoragePermission() {
        /**
         * 書き込み権限
         */
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Here")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISION_WRITE_STORAGE)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISION_WRITE_STORAGE)
        }

        /**
         * 読み込み権限
         */
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Here")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISION_READ_STORAGE)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISION_READ_STORAGE)
        }
    }


}
