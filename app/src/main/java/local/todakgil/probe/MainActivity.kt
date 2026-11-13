package local.todakgil.probe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

/**
 * 토닥길 화면. 출발 전에는 진행자와 이야기를 고르고, 재생 중에는 남은 시간과 장면 흐름을 보여 준다.
 * 조작은 출발 전·주차 상태에서만 하고, 이동 중에는 소리로 이용한다.
 */
class MainActivity : Activity() {
    private val navy = Color.parseColor("#16324F")
    private val ink = Color.parseColor("#22303C")
    private val mute = Color.parseColor("#7A8794")
    private val cream = Color.parseColor("#FBF6EC")
    private val orange = Color.parseColor("#D9641E")
    private val green = Color.parseColor("#2F7D4F")

    private lateinit var home: LinearLayout
    private lateinit var play: LinearLayout
    private lateinit var narratorRow: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var playTitle: TextView
    private lateinit var playNarrator: TextView
    private lateinit var remainValue: TextView
    private lateinit var sourceBadge: TextView
    private lateinit var journey: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var reason: TextView
    private lateinit var stopButton: TextView
    private lateinit var testTools: LinearLayout

    private var narrator = ""
    private var narrators: List<String> = emptyList()
    private var showHome = true

    private fun box(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 3) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius; if (stroke != null) setStroke(strokeWidth, stroke)
    }
    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun lp(w: Int, h: Int, top: Int = 0, left: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top; leftMargin = left }

    private fun narratorName(key: String) = when {
        key.isEmpty() || key.startsWith("synth") -> "기본 음성"
        key == "mom" -> "엄마"; key == "dad" -> "아빠"; key == "grandma" -> "할머니"; key == "grandpa" -> "할아버지"
        else -> key
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = FrameLayout(this).apply { setBackgroundColor(cream); setPadding(56, 40, 56, 36) }

        // ---------- 출발 전 화면
        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(text("토닥길", 56f, navy, true))
        home.addView(text("가족 목소리로 듣고, 도착에 맞춰 끝나는 이야기", 26f, mute), lp(WRAP_CONTENT, WRAP_CONTENT, 6))
        home.addView(text("누가 들려줄까요?", 24f, ink, true), lp(WRAP_CONTENT, WRAP_CONTENT, 44))
        narratorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        home.addView(narratorRow, lp(MATCH_PARENT, WRAP_CONTENT, 14))

        home.addView(text("오늘의 이야기", 24f, ink, true), lp(WRAP_CONTENT, WRAP_CONTENT, 40))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = box(Color.WHITE, 28f, Color.parseColor("#E8DFCF")); setPadding(36, 28, 36, 28) }
        card.addView(text(StoryService.STORY_TITLE, 36f, navy, true))
        card.addView(text("가는 길이 짧으면 짧게, 길면 장면을 더해서 들려줘요", 24f, mute), lp(WRAP_CONTENT, WRAP_CONTENT, 8))
        home.addView(card, lp(MATCH_PARENT, WRAP_CONTENT, 14))

        val start = text("이야기 시작", 32f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = box(navy, 48f); setPadding(0, 26, 0, 26); setOnClickListener { startStory(-1) }
        }
        home.addView(start, lp(MATCH_PARENT, WRAP_CONTENT, 40))
        home.addView(text("내비에 설정한 목적지까지 남은 시간에 맞춰 편성합니다", 20f, mute).apply { gravity = Gravity.CENTER }, lp(MATCH_PARENT, WRAP_CONTENT, 12))

        // 시연 도구: 평소에는 접혀 있다. 모의 남은 시간으로 편성을 확인할 때만 쓴다.
        val toolsToggle = text("시연 도구", 18f, mute).apply { setPadding(0, 18, 0, 8); setOnClickListener { tools.visibility = if (tools.visibility == View.VISIBLE) View.GONE else View.VISIBLE } }
        home.addView(toolsToggle, lp(WRAP_CONTENT, WRAP_CONTENT, 18))
        tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        fun tool(t: String, onClick: () -> Unit) = text(t, 18f, navy).apply {
            background = box(Color.TRANSPARENT, 24f, Color.parseColor("#B9C3CE"), 2); setPadding(22, 12, 22, 12); setOnClickListener { onClick() }
        }
        tools.addView(tool("시험 45초 길") { startStory(45) })
        tools.addView(tool("시험 75초 길") { startStory(75) }, lp(WRAP_CONTENT, WRAP_CONTENT, 0, 12))
        tools.addView(tool("내비·오디오 검증 120초") { startForegroundService(Intent(this, ProbeService::class.java)) }, lp(WRAP_CONTENT, WRAP_CONTENT, 0, 12))
        home.addView(tools)
        root.addView(ScrollView(this).apply { addView(home) })

        // ---------- 재생 화면
        play = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        playTitle = text("", 34f, navy, true); playNarrator = text("", 24f, mute)
        titles.addView(playTitle); titles.addView(playNarrator, lp(WRAP_CONTENT, WRAP_CONTENT, 4))
        top.addView(titles, lp(0, WRAP_CONTENT, weight = 1f))
        stopButton = text("정지", 24f, navy, true).apply {
            background = box(Color.WHITE, 40f, navy, 3); setPadding(40, 16, 40, 16)
            setOnClickListener {
                if (DemoState.running) startService(Intent(this@MainActivity, StoryService::class.java).setAction(StoryService.ACTION_STOP))
                else { showHome = true; render() }
            }
        }
        top.addView(stopButton)
        play.addView(top)

        val remainRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val remainBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        remainBox.addView(text("도착까지", 24f, mute))
        remainValue = text("", 88f, navy, true)
        remainBox.addView(remainValue)
        remainRow.addView(remainBox)
        sourceBadge = text("", 20f, orange, true).apply { setPadding(22, 10, 22, 10) }
        remainRow.addView(sourceBadge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = 28; bottomMargin = 26 })
        play.addView(remainRow, lp(MATCH_PARENT, WRAP_CONTENT, 26))

        journey = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        play.addView(journey, lp(MATCH_PARENT, WRAP_CONTENT, 22))

        subtitle = text("", 36f, ink).apply { background = box(Color.WHITE, 28f, Color.parseColor("#E8DFCF")); setPadding(40, 32, 40, 32); setLineSpacing(10f, 1f) }
        play.addView(subtitle, lp(MATCH_PARENT, WRAP_CONTENT, 30))
        reason = text("", 21f, mute)
        play.addView(reason, lp(MATCH_PARENT, WRAP_CONTENT, 16))

        // 재생 중 시연 도구(모의 남은 시간 조정). 시험 입력 배지를 누르면 펼쳐진다.
        testTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        fun playTool(t: String, onClick: () -> Unit) = text(t, 20f, navy).apply {
            background = box(Color.TRANSPARENT, 24f, Color.parseColor("#B9C3CE"), 2); setPadding(24, 12, 24, 12); setOnClickListener { onClick() }
        }
        testTools.addView(playTool("시험: 길 막힘 +20초") { adjust(20) })
        testTools.addView(playTool("시험: 빨리 도착 −20초") { adjust(-20) }, lp(WRAP_CONTENT, WRAP_CONTENT, 0, 12))
        play.addView(testTools, lp(WRAP_CONTENT, WRAP_CONTENT, 16))
        sourceBadge.setOnClickListener {
            if (DemoState.source.startsWith("시험")) testTools.visibility = if (testTools.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.addView(ScrollView(this).apply { addView(play) })
        setContentView(root)

        narrators = StoryService.narrators(this)
        narrator = narrators.firstOrNull { !it.startsWith("synth") } ?: narrators.firstOrNull() ?: ""
        render()
    }

    private fun startStory(testRemain: Int) {
        val i = Intent(this, StoryService::class.java).setAction(StoryService.ACTION_START).putExtra(StoryService.EXTRA_NARRATOR, narrator)
        if (testRemain > 0) i.putExtra(StoryService.EXTRA_TEST_REMAIN, testRemain)
        startService(Intent(this, StoryService::class.java).setAction(StoryService.ACTION_STOP))
        showHome = false
        home.postDelayed({ startForegroundService(i) }, 400)
    }

    private fun adjust(delta: Int) {
        if (DemoState.running) startService(Intent(this, StoryService::class.java).setAction(StoryService.ACTION_ADJUST).putExtra(StoryService.EXTRA_DELTA, delta))
    }

    override fun onResume() {
        super.onResume()
        DemoState.listener = { runOnUiThread { render() } }
        // adb 자동 실행: --ez start_probe true / --ei story_test 45 / --ez story_real true [--es narrator 이름]
        //               --ei story_adjust 25 / --ez home true / --es pick 이름(진행자만 고름)
        if (intent.getBooleanExtra("start_probe", false)) { intent.removeExtra("start_probe"); startForegroundService(Intent(this, ProbeService::class.java)) }
        intent.getStringExtra("pick")?.let { if (it in narrators) narrator = it; intent.removeExtra("pick") }
        if (intent.getBooleanExtra("home", false)) { intent.removeExtra("home"); showHome = true }
        val test = intent.getIntExtra("story_test", -1)
        if (test > 0 || intent.getBooleanExtra("story_real", false)) {
            intent.getStringExtra("narrator")?.let { narrator = it }
            intent.removeExtra("story_test"); intent.removeExtra("story_real")
            startStory(test)
        }
        val delta = intent.getIntExtra("story_adjust", 0)
        if (delta != 0) { intent.removeExtra("story_adjust"); adjust(delta) }
        render()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onPause() { DemoState.listener = null; super.onPause() }

    private fun render() {
        val s = DemoState
        val playing = !showHome && s.source.isNotEmpty()
        home.visibility = if (playing) View.GONE else View.VISIBLE
        play.visibility = if (playing) View.VISIBLE else View.GONE
        (home.parent as View).visibility = home.visibility
        (play.parent as View).visibility = play.visibility

        if (!playing) {
            narratorRow.removeAllViews()
            narrators.forEachIndexed { i, key ->
                val on = key == narrator
                val chip = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = if (on) box(navy, 28f) else box(Color.WHITE, 28f, Color.parseColor("#D8CFBF"))
                    setPadding(48, 22, 48, 22); setOnClickListener { narrator = key; render() }
                }
                chip.addView(text(narratorName(key), 32f, if (on) Color.WHITE else navy, true).apply { gravity = Gravity.CENTER })
                chip.addView(text(StoryService.narratorKind(this, key), 19f, if (on) Color.parseColor("#C9D6E2") else mute).apply { gravity = Gravity.CENTER })
                narratorRow.addView(chip, lp(WRAP_CONTENT, WRAP_CONTENT, 0, if (i == 0) 0 else 18))
            }
            // 가족 녹음이 아직 없으면 그 자리를 보여 준다. 누를 수 없는 안내 칸이다.
            if (narrators.none { !it.startsWith("synth") }) {
                val slot = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 28f; setStroke(3, Color.parseColor("#C9BFAD"), 14f, 10f) }
                    setPadding(40, 22, 40, 22)
                }
                slot.addView(text("+ 가족 목소리", 32f, Color.parseColor("#A9A294"), true).apply { gravity = Gravity.CENTER })
                slot.addView(text("엄마·할머니 녹음 자리 · 준비 중", 19f, Color.parseColor("#A9A294")).apply { gravity = Gravity.CENTER })
                narratorRow.addView(slot, lp(WRAP_CONTENT, WRAP_CONTENT, 0, 18))
            }
            return
        }

        playTitle.text = s.storyTitle
        val kind = s.audioKinds.values.firstOrNull() ?: StoryService.narratorKind(this, s.narrator)
        playNarrator.text = if (kind == "가족 녹음") "${narratorName(s.narrator)} 목소리 · 가족 녹음" else "${narratorName(s.narrator)} · $kind"
        remainValue.text = s.remainText
        val test = s.source.startsWith("시험")
        sourceBadge.text = if (test) "시험 입력 · 모의 남은 시간" else "실제 내비 연동"
        sourceBadge.setTextColor(if (test) orange else green)
        sourceBadge.background = box(Color.TRANSPARENT, 30f, if (test) orange else green, 2)
        stopButton.text = if (s.running) "정지" else "처음으로"

        // 장면 흐름: 짧은 결말은 실제로 쓰였을 때만 결말 자리에 표시한다.
        journey.removeAllViews()
        val shortUsed = s.marks["end_short"].let { it == DemoState.Mark.PLAYING || it == DemoState.Mark.PLAYED }
        val shown = s.scenes.filter { if (shortUsed) it.id != "end" else it.id != "end_short" }
        shown.forEachIndexed { i, scene ->
            if (i > 0) journey.addView(text("›", 34f, Color.parseColor("#B9C3CE")).apply { gravity = Gravity.CENTER }, lp(WRAP_CONTENT, WRAP_CONTENT, 0, 10).apply { rightMargin = 10 })
            val mark = s.marks[scene.id] ?: DemoState.Mark.NONE
            val name = when (scene.id) { "intro" -> "도입"; "cloud" -> "구름"; "tree" -> "나무"; "stream" -> "시냇물"; "end" -> "결말"; "end_short" -> "짧은 결말"; else -> scene.title }
            val (caption, fill, stroke, fg) = when (mark) {
                DemoState.Mark.PLAYED -> Quad("들음", Color.parseColor("#E3F1E8"), green, green)
                DemoState.Mark.PLAYING -> Quad("재생 중", orange, orange, Color.WHITE)
                DemoState.Mark.PLANNED -> Quad("예정", Color.WHITE, navy, navy)
                DemoState.Mark.SKIPPED -> Quad("건너뜀", Color.parseColor("#F1ECE2"), Color.parseColor("#D8CFBF"), Color.parseColor("#A9A294"))
                DemoState.Mark.NONE -> Quad("", Color.parseColor("#F1ECE2"), Color.parseColor("#D8CFBF"), Color.parseColor("#A9A294"))
            }
            val chip = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = box(fill, 26f, stroke); setPadding(10, 18, 10, 18) }
            chip.addView(text(name, 28f, fg, true).apply { gravity = Gravity.CENTER })
            chip.addView(text(if (caption.isEmpty()) " " else caption, 19f, fg).apply { gravity = Gravity.CENTER })
            journey.addView(chip, lp(0, WRAP_CONTENT, weight = 1f))
        }

        if (s.currentText.isNotEmpty()) { subtitle.text = s.currentText; subtitle.visibility = View.VISIBLE }
        else if (!s.running) { subtitle.text = "오늘 이야기는 여기까지예요.\n다음 이야기는 자동으로 시작하지 않아요."; subtitle.visibility = View.VISIBLE }
        else subtitle.visibility = View.INVISIBLE
        reason.text = s.lastReason
    }

    private data class Quad(val caption: String, val fill: Int, val stroke: Int, val fg: Int)
}
