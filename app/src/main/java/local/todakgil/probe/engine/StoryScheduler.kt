package local.todakgil.probe.engine

/**
 * 도착 연동 편성 엔진. Android에 의존하지 않는 순수 Kotlin 코드다.
 *
 * 장면이 끝날 때마다 [next]를 호출하면 남은 이동 시간에 맞춰 다음 장면을 고른다.
 * 속도를 바꾸거나 장면 중간을 자르지 않고, 장면 단위로 넣고 빼서 길이를 맞춘다.
 */
enum class SceneRole { INTRO, REQUIRED, OPTIONAL, ENDING_FULL, ENDING_SHORT }

data class Scene(val id: String, val title: String, val role: SceneRole, val durationSec: Int, val text: String = "")

data class Story(val title: String, val scenes: List<Scene>) {
    val intro = scenes.single { it.role == SceneRole.INTRO }
    val required = scenes.filter { it.role == SceneRole.REQUIRED }
    val optional = scenes.filter { it.role == SceneRole.OPTIONAL }
    val endingFull = scenes.single { it.role == SceneRole.ENDING_FULL }
    val endingShort = scenes.single { it.role == SceneRole.ENDING_SHORT }
}

/** 내비에서 받은 남은 시간의 해석 결과. */
sealed class Remaining {
    /** 유효한 남은 시간(초). */
    data class Known(val sec: Int) : Remaining()
    /** 경로 없음, 응답 지연, 0 수신 등으로 믿을 수 없는 상태. */
    object Unknown : Remaining()
    /** 내비의 도착 이벤트를 받은 상태. */
    object Arrived : Remaining()
}

sealed class Decision {
    data class Play(val scene: Scene, val reason: String) : Decision()
    data class Finished(val reason: String) : Decision()
}

class StoryScheduler(
    private val story: Story,
    /** 결말이 도착보다 이만큼 먼저 끝나도록 남겨 두는 여유(초). */
    private val arrivalMarginSec: Int = 5,
    /** 장면 사이의 쉼(초). 예산 계산에 포함한다. */
    private val gapSec: Int = 1,
) {
    private val played = mutableListOf<Scene>()
    private val skipped = mutableListOf<Scene>()
    private var ended = false

    val playedScenes: List<Scene> get() = played
    val skippedScenes: List<Scene> get() = skipped
    val isEnded: Boolean get() = ended

    /** 직전 장면이 끝난 시점에 호출한다. 고른 장면은 재생한 것으로 기록된다. */
    fun next(remaining: Remaining): Decision {
        val d = decide(remaining, played, skipped, ended)
        if (d is Decision.Play) {
            played += d.scene
            if (d.scene.role == SceneRole.ENDING_FULL || d.scene.role == SceneRole.ENDING_SHORT) {
                ended = true
                skipped += story.scenes.filter { it.role == SceneRole.REQUIRED || it.role == SceneRole.OPTIONAL }
                    .filter { it !in played && it !in skipped }
            }
        }
        return d
    }

    /** 상태를 바꾸지 않고, 남은 시간이 장면 길이만큼 줄어든다고 가정한 예상 편성을 돌려준다. */
    fun preview(remaining: Remaining): List<Scene> {
        val p = played.toMutableList(); val s = skipped.toMutableList(); var e = ended
        var r = remaining
        val out = mutableListOf<Scene>()
        while (true) {
            val d = decide(r, p, s, e)
            if (d !is Decision.Play) break
            out += d.scene; p += d.scene
            if (d.scene.role == SceneRole.ENDING_FULL || d.scene.role == SceneRole.ENDING_SHORT) e = true
            if (r is Remaining.Known) r = Remaining.Known(maxOf(0, r.sec - d.scene.durationSec - gapSec))
        }
        return out
    }

    private fun decide(remaining: Remaining, played: MutableList<Scene>, skipped: MutableList<Scene>, ended: Boolean): Decision {
        if (ended) return Decision.Finished("결말을 마쳤습니다. 다음 이야기를 자동으로 시작하지 않습니다.")
        val full = story.endingFull; val short = story.endingShort
        val pending = (listOf(story.intro) + story.required + story.optional).filter { it !in played && it !in skipped }

        when (remaining) {
            is Remaining.Arrived ->
                return Decision.Play(short, "도착 이벤트를 받아 짧은 결말로 마무리합니다.")
            is Remaining.Unknown -> {
                // 도착 연동 해제: 필수 장면만 재생하고 기본 결말로 전환한다.
                val nextRequired = pending.firstOrNull { it.role != SceneRole.OPTIONAL }
                return if (nextRequired != null) Decision.Play(nextRequired, "남은 시간을 알 수 없어 기본 순서로 재생합니다.")
                else Decision.Play(full, "남은 시간을 알 수 없어 기본 결말로 전환합니다.")
            }
            is Remaining.Known -> {
                val budget = remaining.sec - arrivalMarginSec
                fun fits(scene: Scene, ending: Scene) = budget >= scene.durationSec + gapSec + ending.durationSec
                val nextRequired = pending.firstOrNull { it.role != SceneRole.OPTIONAL }
                if (nextRequired != null) {
                    // 필수 장면은 짧은 결말을 붙일 시간만 있으면 재생한다.
                    if (fits(nextRequired, short)) return Decision.Play(nextRequired, "남은 ${remaining.sec}초: 필수 장면을 재생합니다.")
                } else {
                    // 추가 장면은 온전한 결말까지 들어갈 때만 넣는다. 안 들어가는 장면은 건너뛴다.
                    for (opt in pending) {
                        if (fits(opt, full)) return Decision.Play(opt, "남은 ${remaining.sec}초: 시간이 넉넉해 추가 장면을 넣습니다.")
                        skipped += opt
                    }
                }
                return if (budget >= full.durationSec) Decision.Play(full, "남은 ${remaining.sec}초: 새 장면을 시작하지 않고 결말로 이어갑니다.")
                else Decision.Play(short, "남은 ${remaining.sec}초: 시간이 부족해 짧은 결말로 마무리합니다.")
            }
        }
    }
}

/** 내비 원시값을 [Remaining]으로 해석한다. 0만으로 도착을 판단하지 않는다. */
object RemainingInterpreter {
    const val STALE_AFTER_MS = 15_000L

    fun interpret(rawRemainSec: Int?, receivedAtMs: Long?, nowMs: Long, arrivedEvent: Boolean): Remaining {
        if (arrivedEvent) return Remaining.Arrived
        if (rawRemainSec == null || receivedAtMs == null) return Remaining.Unknown
        if (nowMs - receivedAtMs > STALE_AFTER_MS) return Remaining.Unknown
        if (rawRemainSec <= 0) return Remaining.Unknown
        return Remaining.Known(rawRemainSec)
    }
}
