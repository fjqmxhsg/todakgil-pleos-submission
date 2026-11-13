package local.todakgil.probe

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.util.Log
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class ProbeService : Service(), NaviHelperEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var player: MediaPlayer? = null
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var started = false
    private var received = 0
    private var resumeOnGain = false
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private fun record(message: String) {
        val line = "${SystemClock.elapsedRealtime()} $message"
        Log.i("TodakProbe", line)
        File(filesDir, "probe.log").appendText(line + "\n")
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("probe", "Verification", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2, Intent(this, ProbeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this,"probe").setContentTitle("Todakgil verification running").setSmallIcon(android.R.drawable.ic_media_play).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build())
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
            record("AUDIO_FOCUS change=$change")
            when(change) {
                AudioManager.AUDIOFOCUS_GAIN -> if(resumeOnGain) { player?.start(); resumeOnGain = false }
                AudioManager.AUDIOFOCUS_LOSS -> { player?.pause(); resumeOnGain = false }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { resumeOnGain = player?.isPlaying == true; player?.pause() }
            }
        }, handler).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if(started) return START_NOT_STICKY
        started = true
        record("START real_sdk=true simulated_values=false")
        try {
            helper = NaviHelper(this).also { it.initialize(); it.addListener(this) }
            record("NAVI initialize_returned (NOT proof of callbacks)")
        } catch(e: Exception) { record("NAVI_INIT_ERROR ${e.javaClass.simpleName}: ${e.message}") }
        try {
            val file = File(cacheDir,"quiet-tone.wav")
            val rate = 16000
            val samples = rate * 4
            val bytes = ByteBuffer.allocate(44 + samples*2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples*2)
            for(i in 0 until samples) {
                val envelope = minOf(1.0, i/800.0, (samples-1-i)/800.0)
                bytes.putShort((sin(2*Math.PI*330*i/rate)*1200*envelope).toInt().toShort())
            }
            file.writeBytes(bytes.array())
            player = MediaPlayer().apply {
                setAudioAttributes(attrs); setDataSource(file.absolutePath); isLooping = true
                setOnErrorListener { _, what, extra -> record("AUDIO_ERROR what=$what extra=$extra"); true }
                prepare()
            }
            val granted = audio.requestAudioFocus(focus)
            record("AUDIO_FOCUS_REQUEST result=$granted")
            if(granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player?.start()
        } catch(e: Exception) { record("AUDIO_SETUP_ERROR ${e.javaClass.simpleName}: ${e.message}") }
        handler.post(object : Runnable {
            override fun run() {
                try { helper?.getDestinationInfo() } catch(e: Exception) { record("NAVI_REQUEST_ERROR ${e.javaClass.simpleName}: ${e.message}") }
                record("HEARTBEAT callbacks=$received playing=${player?.isPlaying} positionMs=${player?.currentPosition}")
                handler.postDelayed(this,5000)
            }
        })
        handler.postDelayed({ record("AUTO_STOP after_120s"); stopSelf() },120000)
        return START_NOT_STICKY
    }
    override fun onDestinationInfo(info: DestinationInfo) { received++; record("REAL_DESTINATION remainTimeRaw=${info.remainTime} remainDistanceRaw=${info.remainDistance}") }
    override fun onDrivingInfo(info: DrivingInfo) { received++; record("REAL_DRIVING durationRaw=${info.destination.duration} distanceRaw=${info.destination.distance}") }
    override fun onDestinationArrived(info: DestinationArrivedInfo) { record("REAL_ARRIVAL") }
    override fun onRouteCancelled() { record("REAL_ROUTE_CANCELLED") }
    override fun onDestinationChanged() { record("REAL_DESTINATION_CHANGED") }
    override fun onError(error: NaviErrorCode) { record("NAVI_ERROR $error") }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        audio.abandonAudioFocusRequest(focus)
        try { helper?.removeListener(this); helper?.release() } catch(e: Exception) { record("RELEASE_ERROR ${e.javaClass.simpleName}") }
        record("STOP"); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
