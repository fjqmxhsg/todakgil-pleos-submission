package local.todakgil.probe

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import local.todakgil.probe.engine.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sin

/**
 * 도착 연동 이야기 재생 서비스(시연용 최소 구현).
 *
 * 남은 시간 입력은 두 가지다.
 *  - 실제 내비: NaviHelper 콜백 값만 쓴다.
 *  - 시험 입력: 시작값에서 초 단위로 줄어드는 모의 값. 화면과 로그에 "시험 입력"으로 표시한다.
 *
 * 장면 음원은 가족 녹음 파일 → 기기 합성 음성 → 시험음 순으로 찾는다.
 */
class StoryService : Service(), NaviHelperEventListener {
    companion object {
        const val ACTION_START = "local.todakgil.probe.STORY_START"
        const val ACTION_ADJUST = "local.todakgil.probe.STORY_ADJUST"
        const val ACTION_STOP = "local.todakgil.probe.STORY_STOP"
        const val EXTRA_TEST_REMAIN = "test_remain"   // 초. 없으면 실제 내비
        const val EXTRA_DELTA = "delta"               // 초. 시험 입력에서만 적용
        const val EXTRA_NARRATOR = "narrator"         // scenes/<narrator>/ 폴더 이름
        private const val GAP_MS = 1000L
        private const val ARRIVAL_MARGIN_SEC = 5

        val script = listOf(
            Scene("intro", "도입", SceneRole.INTRO, 9, "아기 곰이 집으로 돌아가는 길이에요. 오늘은 누구를 만나게 될까요? 우리 같이 따라가 봐요."),
            Scene("cloud", "구름을 만나요", SceneRole.REQUIRED, 10, "하늘에 폭신한 구름이 떠 있었어요. 아기 곰이 손을 흔들자, 구름도 천천히 손을 흔들어 주었답니다."),
            Scene("tree", "나무를 만나요 (추가 장면)", SceneRole.OPTIONAL, 9, "길가에 키 큰 나무가 서 있었어요. 나무는 잎사귀를 살랑살랑 흔들며, 잘 가라고 인사했어요."),
            Scene("stream", "시냇물을 만나요 (추가 장면)", SceneRole.OPTIONAL, 9, "졸졸졸 시냇물이 노래를 불렀어요. 아기 곰은 잠깐 멈춰 서서, 그 노래를 가만히 들었어요."),
            Scene("end", "결말", SceneRole.ENDING_FULL, 9, "어느새 포근한 집이 보여요. 아기 곰은 집에 잘 돌아왔어요. 오늘 이야기는 여기까지."),
            Scene("end_short", "짧은 결말", SceneRole.ENDING_SHORT, 5, "아기 곰은 집에 잘 돌아왔어요. 오늘 이야기는 여기까지."),
        )
        const val STORY_TITLE = "아기 곰의 귀갓길"

        /** 진행자 폴더를 찾는 위치. 앱 내부 files/scenes 와 외부 files/scenes. */
        fun sceneRoots(context: android.content.Context): List<File> =
            listOfNotNull(File(context.filesDir, "scenes"), context.getExternalFilesDir("scenes"))
    }

    private val handler = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private var started = false
    private var resumeOnGain = false
    private var scheduler: StoryScheduler? = null
    private var story: Story? = null
    private val files = mutableMapOf<String, File>()
    private val kinds = mutableMapOf<String, String>()
    private var current: Scene? = null

    // 남은 시간 입력
    private var testMode = false
    private var testStartSec = 0
    private var testStartedAt = 0L
    private var testAdjust = 0
    private var rawRemain: Int? = null
    private var rawAt: Long? = null
    private var arrived = false

    private fun record(message: String) {
        val line = "${SystemClock.elapsedRealtime()} $message"
        Log.i("TodakStory", line)
        try { File(filesDir, "story.log").appendText(line + "\n") } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("story", "Story", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 3, Intent(this, StoryService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(2, Notification.Builder(this, "story").setContentTitle("토닥길 이야기 재생 중").setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "정지", stop).build()).build())
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                record("AUDIO_FOCUS change=$change")
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { player?.start(); resumeOnGain = false; setStatus("재생 중") }
                    AudioManager.AUDIOFOCUS_LOSS -> { player?.pause(); resumeOnGain = false; setStatus("다른 소리에 양보하고 멈춤") }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeOnGain = player?.isPlaying == true; player?.pause(); setStatus("내비 안내에 양보하는 중")
                    }
                }
            }, handler).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { record("STOP_REQUEST"); stopSelf(); return START_NOT_STICKY }
            ACTION_ADJUST -> {
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                if (testMode) { testAdjust += delta; record("TEST_INPUT adjust=$delta total=$testAdjust"); publish() }
                else record("ADJUST_IGNORED real_navi_mode")
                if (!started) stopSelf()
                return START_NOT_STICKY
            }
        }
        if (started) return START_NOT_STICKY
        started = true
        val testRemain = intent?.getIntExtra(EXTRA_TEST_REMAIN, -1) ?: -1
        testMode = testRemain > 0
        testStartSec = testRemain
        val narrator = intent?.getStringExtra(EXTRA_NARRATOR) ?: ""
        DemoState.running = true
        DemoState.storyTitle = STORY_TITLE
        DemoState.narrator = narrator
        DemoState.source = if (testMode) "시험 입력 (모의 남은 시간)" else "실제 내비 (Pleos NaviHelper)"
        DemoState.scenes = script; DemoState.marks = emptyMap(); DemoState.audioKinds = emptyMap()
        DemoState.currentText = ""; DemoState.lastReason = ""
        setStatus("음원 준비 중")
        record("STORY_START mode=${if (testMode) "TEST_INPUT start=$testRemain" else "REAL_NAVI"} narrator='$narrator'")

        if (!testMode) {
            try {
                helper = NaviHelper(this).also { it.initialize(); it.addListener(this) }
                record("NAVI initialize_returned")
            } catch (e: Exception) { record("NAVI_INIT_ERROR ${e.javaClass.simpleName}: ${e.message}") }
            handler.post(object : Runnable {
                override fun run() {
                    try { helper?.getDestinationInfo() } catch (e: Exception) { record("NAVI_REQUEST_ERROR ${e.javaClass.simpleName}: ${e.message}") }
                    handler.postDelayed(this, 2000)
                }
            })
        }
        handler.post(object : Runnable { override fun run() { publish(); handler.postDelayed(this, 1000) } })
        prepareAudio(narrator)
        return START_NOT_STICKY
    }

    // ---- 음원 준비: 가족 녹음 → 합성 음성 → 시험음

    private fun prepareAudio(narrator: String) {
        val missing = mutableListOf<Scene>()
        val dir = if (narrator.isNotEmpty()) sceneRoots(this).map { File(it, narrator) }.firstOrNull { it.isDirectory } else null
        for (s in script) {
            val f = dir?.let { d -> listOf("wav", "m4a", "mp3", "ogg").map { File(d, "${s.id}.$it") }.firstOrNull { it.isFile } }
                ?: if (narrator.isEmpty() || narrator.startsWith("synth")) {
                    try {
                        val bundled = File(cacheDir, "bundled-${s.id}.wav")
                        assets.open("scenes/synth/${s.id}.wav").use { input -> bundled.outputStream().use { input.copyTo(it) } }
                        bundled
                    } catch (_: Exception) { null }
                } else null
            // 폴더 이름이 synth로 시작하면 합성 음성으로 표시한다. 가족 녹음과 섞어 표기하지 않는다.
            if (f != null) { files[s.id] = f; kinds[s.id] = if (narrator.isEmpty() || narrator.startsWith("synth")) "합성 음성" else "가족 녹음" } else missing += s
        }
        if (missing.isEmpty()) { beginStory(); return }
        var finished = false
        fun finish(why: String) {
            if (finished) return
            finished = true
            record("AUDIO_PREP $why")
            for (s in missing) if (s.id !in files) {
                val f = File(cacheDir, "tone-${s.id}.wav"); writeTone(f, s.durationSec, 262 + 40 * script.indexOf(s))
                files[s.id] = f; kinds[s.id] = "시험음"
            }
            handler.post { beginStory() }
        }
        handler.postDelayed({ finish("tts_timeout") }, 25000)
        try {
            tts = TextToSpeech(this) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) { finish("tts_init_failed status=$status"); return@TextToSpeech }
                val lang = engine.setLanguage(Locale.KOREAN)
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) { finish("tts_korean_unavailable code=$lang"); return@TextToSpeech }
                engine.setSpeechRate(0.9f)
                var left = missing.size
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        handler.post {
                            val f = File(cacheDir, "tts-$id.wav")
                            if (id != null && f.length() > 44) { files[id] = f; kinds[id] = "합성 음성" }
                            if (--left == 0) finish("tts_done")
                        }
                    }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { handler.post { if (--left == 0) finish("tts_done_with_errors") } }
                })
                for (s in missing) engine.synthesizeToFile(s.text, null, File(cacheDir, "tts-${s.id}.wav"), s.id)
            }
        } catch (e: Exception) { finish("tts_exception ${e.javaClass.simpleName}") }
    }

    private fun writeTone(file: File, seconds: Int, hz: Int) {
        val rate = 16000; val samples = rate * seconds
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        for (i in 0 until samples) {
            val envelope = minOf(1.0, i / 800.0, (samples - 1 - i) / 800.0)
            bytes.putShort((sin(2 * Math.PI * hz * i / rate) * 1200 * envelope).toInt().toShort())
        }
        file.writeBytes(bytes.array())
    }

    private fun measureSec(file: File, fallback: Int): Int = try {
        val r = MediaMetadataRetriever(); r.setDataSource(file.absolutePath)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(); r.release()
        if (ms != null && ms > 0) ((ms + 999) / 1000).toInt() else fallback
    } catch (_: Exception) { fallback }

    // ---- 재생과 편성

    private fun beginStory() {
        if (scheduler != null) return
        // 편성은 실제 음원 길이를 기준으로 한다.
        val measured = script.map { it.copy(durationSec = measureSec(files.getValue(it.id), it.durationSec)) }
        val st = Story(STORY_TITLE, measured)
        story = st
        scheduler = StoryScheduler(st, ARRIVAL_MARGIN_SEC, (GAP_MS / 1000).toInt())
        DemoState.scenes = measured; DemoState.audioKinds = kinds.toMap()
        record("SCENES " + measured.joinToString(" ") { "${it.id}=${it.durationSec}s/${kinds[it.id]}" })
        testStartedAt = SystemClock.elapsedRealtime()
        val granted = audio.requestAudioFocus(focus)
        record("AUDIO_FOCUS_REQUEST result=$granted")
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { setStatus("오디오 포커스를 얻지 못함"); return }
        // 실제 내비 모드에서는 첫 응답을 잠깐 기다린 뒤 시작한다.
        handler.postDelayed({ playNext() }, if (testMode) 0 else 2500)
    }

    private fun remainingNow(): Remaining {
        val now = SystemClock.elapsedRealtime()
        if (testMode) {
            val r = testStartSec + testAdjust - ((now - testStartedAt) / 1000).toInt()
            return if (r > 0) Remaining.Known(r) else Remaining.Arrived
        }
        return RemainingInterpreter.interpret(rawRemain, rawAt, now, arrived)
    }

    private fun describe(r: Remaining) = when (r) {
        is Remaining.Known -> "${r.sec / 60}분 ${r.sec % 60}초"
        Remaining.Unknown -> "알 수 없음 (경로 없음 또는 응답 지연)"
        Remaining.Arrived -> "도착"
    }

    private fun playNext() {
        val sch = scheduler ?: return
        val remaining = remainingNow()
        current = null
        when (val d = sch.next(remaining)) {
            is Decision.Finished -> {
                record("FINISHED remaining=${describe(remaining)} reason=${d.reason}")
                DemoState.lastReason = d.reason; DemoState.currentText = ""
                setStatus("이야기 끝. 조용히 유지")
                player?.release(); player = null
                audio.abandonAudioFocusRequest(focus)
                DemoState.running = false
                publish()
                handler.postDelayed({ stopSelf() }, 1500)
            }
            is Decision.Play -> {
                val scene = d.scene
                current = scene
                record("DECISION play=${scene.id} dur=${scene.durationSec}s remaining=${describe(remaining)} source=${if (testMode) "TEST_INPUT" else "REAL_NAVI"} reason=${d.reason}")
                DemoState.lastReason = d.reason; DemoState.currentText = scene.text
                setStatus("재생 중")
                try {
                    player?.release()
                    player = MediaPlayer().apply {
                        setAudioAttributes(attrs); setDataSource(files.getValue(scene.id).absolutePath)
                        setOnErrorListener { _, what, extra -> record("AUDIO_ERROR what=$what extra=$extra"); handler.postDelayed({ playNext() }, GAP_MS); true }
                        setOnCompletionListener { record("SCENE_DONE ${scene.id}"); handler.postDelayed({ playNext() }, GAP_MS) }
                        prepare(); start()
                    }
                } catch (e: Exception) {
                    record("AUDIO_SETUP_ERROR ${e.javaClass.simpleName}: ${e.message}")
                    handler.postDelayed({ playNext() }, GAP_MS)
                }
                publish()
            }
        }
    }

    private fun setStatus(s: String) { DemoState.status = s; DemoState.changed() }

    private fun publish() {
        val sch = scheduler
        val remaining = if (sch == null && testMode) Remaining.Known(testStartSec + testAdjust) else remainingNow()
        DemoState.remainText = describe(remaining)
        if (sch != null) {
            val marks = mutableMapOf<String, DemoState.Mark>()
            // 현재 장면이 끝난 뒤를 기준으로 예상 편성을 보여 준다.
            val leftInScene = player?.let { p -> try { (p.duration - p.currentPosition) / 1000 } catch (_: Exception) { 0 } } ?: 0
            val after = if (remaining is Remaining.Known) Remaining.Known(maxOf(1, remaining.sec - leftInScene - 1)) else remaining
            for (s in sch.preview(after)) marks[s.id] = DemoState.Mark.PLANNED
            for (s in sch.skippedScenes) marks[s.id] = DemoState.Mark.SKIPPED
            for (s in sch.playedScenes) marks[s.id] = DemoState.Mark.PLAYED
            current?.let { marks[it.id] = DemoState.Mark.PLAYING }
            DemoState.marks = marks
        }
        DemoState.changed()
    }

    // ---- 실제 내비 콜백

    override fun onDestinationInfo(info: DestinationInfo) {
        rawRemain = info.remainTime; rawAt = SystemClock.elapsedRealtime()
        record("REAL_DESTINATION remainTimeRaw=${info.remainTime}")
    }
    override fun onDrivingInfo(info: DrivingInfo) { record("REAL_DRIVING durationRaw=${info.destination.duration}") }
    override fun onDestinationArrived(info: DestinationArrivedInfo) { arrived = true; record("REAL_ARRIVAL") }
    override fun onRouteCancelled() { rawRemain = null; rawAt = null; record("REAL_ROUTE_CANCELLED") }
    override fun onDestinationChanged() { arrived = false; record("REAL_DESTINATION_CHANGED") }
    override fun onError(error: NaviErrorCode) { record("NAVI_ERROR $error") }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        try { tts?.shutdown() } catch (_: Exception) {}
        try { audio.abandonAudioFocusRequest(focus) } catch (_: Exception) {}
        try { helper?.removeListener(this); helper?.release() } catch (e: Exception) { record("RELEASE_ERROR ${e.javaClass.simpleName}") }
        if (DemoState.running) { DemoState.running = false; DemoState.status = "정지함" }
        DemoState.changed()
        record("STORY_STOP"); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
