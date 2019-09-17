package skunkworks.qrcodescanner

import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Reader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.theta360.pluginlibrary.activity.PluginActivity
import com.theta360.pluginlibrary.callback.KeyCallback
import com.theta360.pluginlibrary.receiver.KeyReceiver
import com.theta360.pluginlibrary.values.LedColor
import com.theta360.pluginlibrary.values.LedTarget

class MainActivity : PluginActivity(), Camera.PreviewCallback {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val WIDTH = 1920
        private const val HEIGHT = 960
    }

    private val mReader: Reader = QRCodeReader()

    private var mTexture: SurfaceTexture? = null

    private var mCamera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "Uncaught Exception", e)
        }
    }

    override fun onResume() {
        super.onResume()

        notificationCameraClose()

        mTexture = SurfaceTexture(10)

        mCamera = Camera.open().apply {
            parameters = parameters.apply {
                setPreviewSize(WIDTH, HEIGHT)
                set("RIC_SHOOTING_MODE", "RicMoviePreview1920")
                set("RIC_PROC_STITCHING", "RicStaticStitching")
                previewFrameRate = 5
                previewFormat = ImageFormat.YV12
            }
            setPreviewTexture(mTexture)
        }

        setKeyCallback(object : KeyCallback {
            override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent?) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    start()
                }
            }

            override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent?) {
            }

            override fun onKeyLongPress(keyCode: Int, keyEvent: KeyEvent?) {
            }
        })

        start()
    }

    override fun onPause() {
        super.onPause()

        setKeyCallback(null)

        mCamera?.apply {
            stopPreview()
            setPreviewCallback(null)
            release()
        }
        mCamera = null

        mTexture?.release()
        mTexture = null

        notificationCameraOpen()
    }

    private fun showOledTextMiddle(text: String) {
        sendBroadcast(Intent("com.theta360.plugin.ACTION_OLED_TEXT_SHOW").apply {
            putExtra("text-middle", text)
        })
    }

    private fun start() {
        mCamera?.apply {
            setPreviewCallback(this@MainActivity)
            startPreview()

            notificationLedHide(LedTarget.LED3) // for V
            showOledTextMiddle("Scanning") // for Z1
            notificationAudioMovStart()
        }
    }

    private fun stop() {
        mCamera?.apply {
            stopPreview()
            notificationAudioMovStop()
        }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        // Trim Bitmap
        val width = WIDTH / 5
        val height = HEIGHT / 3
        val left = width * 2
        val top = height * 1
        val src = PlanarYUVLuminanceSource(data, WIDTH, HEIGHT, left, top, width, height, false)
        val bmp = BinaryBitmap(HybridBinarizer(src))

        // Decode
        val result: Result
        try {
            result = mReader.decode(bmp)
        } catch (e: Exception) {
            Log.d(TAG, "Not Found")
            return
        }
        Log.d(TAG, "Found ${result.text}")

        stop()

        // Show Result
        when (result.text) { // for V
            "RED" -> notificationLed3Show(LedColor.RED)
            "GREEN" -> notificationLed3Show(LedColor.GREEN)
            "BLUE" -> notificationLed3Show(LedColor.BLUE)
        }
        showOledTextMiddle(result.text) // for Z1
    }
}
