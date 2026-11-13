package local.todakgil.probe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

/**
 * 시연 화면. 이야기 편성 상태를 크게 보여 준다. 출발 전·주차 상태에서 조작하는 화면이며
 * 제품의 주행 중 UI가 아니다.
 */
class MainActivity : Activity() {
    private lateinit var header: TextView
    private lateinit var remain: TextView
    private lateinit var timeline: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var reason: TextView
    private var narrator = ""
    private lateinit var narratorGroup: RadioGroup
    private var narratorKeys: List<String> = emptyList()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(36, 28, 36, 28); setBackgroundColor(Color.parseColor("#FBF7F0")) }

        // 왼쪽: 편성 상태
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header = TextView(this).apply { textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#16324F")) }
        remain = TextView(this).apply { textSize = 22f; setTextColor(Color.parseColor("#333333")); setPadding(0, 10, 0, 14) }
        timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        subtitle = TextView(this).apply { textSize = 22f; setTextColor(Color.parseColor("#222222")); setPadding(0, 18, 0, 8) }
        reason = TextView(this).apply { textSize = 17f; setTextColor(Color.parseColor("#5B6B7A")) }
        left.addView(header); left.addView(remain); left.addView(timeline); left.addView(subtitle); left.addView(reason)
        root.addView(ScrollView(this).apply { addView(left) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f))

        // 오른쪽: 조작
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 0, 0, 0) }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setTextColor(Color.parseColor("#5B6B7A")); setPadding(0, 14, 0, 4) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; textSize = 16f; isAllCaps = false; setOnClickListener { onClick() } }

        right.addView(label("진행자"))
        val narrators = listOf("" to "기본 합성 음성 (내장)") + narratorFolders().map { it to (if (it.startsWith("synth")) "합성 음성" else it) }
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        narratorGroup = group; narratorKeys = narrators.map { it.first }
        narrators.forEachIndexed { i, (key, name) ->
            group.addView(RadioButton(this).apply { text = name; textSize = 16f; id = 100 + i; isChecked = i == 0; setOnClickListener { narrator = key } })
        }
        right.addView(group)

        right.addView(label("실제 내비 연동"))
        right.addView(button("이야기 시작") { startStory(-1) })

        right.addView(label("시험 입력 (모의 남은 시간)"))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("45초 길") { startStory(45) }); row1.addView(button("75초 길") { startStory(75) })
        right.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("막힘 +20초") { adjust(20) }); row2.addView(button("빨리 −20초") { adjust(-20) })
        right.addView(row2)

        right.addView(label(""))
        right.addView(button("정지") { startService(Intent(this, StoryService::class.java).setAction(StoryService.ACTION_STOP)) })
        right.addView(label("SDK 검증용"))
        right.addView(button("내비·오디오 검증 120초") { startForegroundService(Intent(this, ProbeService::class.java)) })
        root.addView(ScrollView(this).apply { addView(right) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f))

        setContentView(root)
        render()
    }

    private fun narratorFolders(): List<String> =
        StoryService.sceneRoots(this).flatMap { it.listFiles()?.filter(File::isDirectory)?.map { d -> d.name } ?: emptyList() }.distinct().sorted()

    private fun startStory(testRemain: Int) {
        val i = Intent(this, StoryService::class.java).setAction(StoryService.ACTION_START).putExtra(StoryService.EXTRA_NARRATOR, narrator)
        if (testRemain > 0) i.putExtra(StoryService.EXTRA_TEST_REMAIN, testRemain)
        startService(Intent(this, StoryService::class.java).setAction(StoryService.ACTION_STOP))
        header.postDelayed({ startForegroundService(i) }, 400)
    }

    private fun adjust(delta: Int) {
        if (DemoState.running) startService(Intent(this, StoryService::class.java).setAction(StoryService.ACTION_ADJUST).putExtra(StoryService.EXTRA_DELTA, delta))
    }

    override fun onResume() {
        super.onResume()
        DemoState.listener = { runOnUiThread { render() } }
        render()
        // adb 자동 실행: --ez start_probe true / --ei story_test 45 / --ez story_real true [--es narrator 이름]
        if (intent.getBooleanExtra("start_probe", false)) { intent.removeExtra("start_probe"); startForegroundService(Intent(this, ProbeService::class.java)) }
        val test = intent.getIntExtra("story_test", -1)
        if (test > 0 || intent.getBooleanExtra("story_real", false)) {
            narrator = intent.getStringExtra("narrator") ?: ""
            narratorKeys.indexOf(narrator).takeIf { it >= 0 }?.let { narratorGroup.check(100 + it) }
            intent.removeExtra("story_test"); intent.removeExtra("story_real")
            startStory(test)
        }
        // 시험 입력 조정: --ei story_adjust -20
        val delta = intent.getIntExtra("story_adjust", 0)
        if (delta != 0) { intent.removeExtra("story_adjust"); adjust(delta) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onPause() { DemoState.listener = null; super.onPause() }

    private fun render() {
        val s = DemoState
        header.text = if (s.storyTitle.isEmpty()) "토닥길 · 도착 연동 이야기" else "토닥길 · ${s.storyTitle}"
        remain.text = if (s.source.isEmpty()) "출발 전에 진행자를 고르고 이야기를 시작하세요."
            else "남은 이동 시간  ${s.remainText}\n입력: ${s.source}\n상태: ${s.status}"
        timeline.removeAllViews()
        for (scene in s.scenes) {
            val mark = s.marks[scene.id] ?: DemoState.Mark.NONE
            val (sign, color) = when (mark) {
                DemoState.Mark.PLAYED -> "✓ 들음" to "#2E7D32"
                DemoState.Mark.PLAYING -> "▶ 재생 중" to "#C2410C"
                DemoState.Mark.SKIPPED -> "– 건너뜀" to "#9AA5B1"
                DemoState.Mark.PLANNED -> "· 예정" to "#16324F"
                DemoState.Mark.NONE -> "" to "#9AA5B1"
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 6, 0, 6) }
            row.addView(TextView(this).apply { text = sign; textSize = 19f; setTextColor(Color.parseColor(color)); setTypeface(null, Typeface.BOLD) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f))
            row.addView(TextView(this).apply {
                text = scene.title; textSize = 19f; setTextColor(Color.parseColor(if (mark == DemoState.Mark.SKIPPED || mark == DemoState.Mark.NONE) "#9AA5B1" else "#222222"))
                if (mark == DemoState.Mark.PLAYING) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.6f))
            row.addView(TextView(this).apply { text = "${scene.durationSec}초 · ${s.audioKinds[scene.id] ?: ""}"; textSize = 15f; setTextColor(Color.parseColor("#5B6B7A")) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f))
            timeline.addView(row)
        }
        subtitle.text = if (s.currentText.isEmpty()) "" else "“${s.currentText}”"
        subtitle.visibility = if (s.currentText.isEmpty()) View.GONE else View.VISIBLE
        reason.text = s.lastReason
    }
}
