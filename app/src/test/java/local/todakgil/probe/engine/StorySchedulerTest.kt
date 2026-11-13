package local.todakgil.probe.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySchedulerTest {
    private val story = Story("테스트", listOf(
        Scene("intro", "도입", SceneRole.INTRO, 10),
        Scene("cloud", "구름", SceneRole.REQUIRED, 15),
        Scene("tree", "나무", SceneRole.OPTIONAL, 15),
        Scene("stream", "시냇물", SceneRole.OPTIONAL, 15),
        Scene("end", "결말", SceneRole.ENDING_FULL, 10),
        Scene("end_short", "짧은 결말", SceneRole.ENDING_SHORT, 5),
    ))

    /** 남은 시간이 장면 길이만큼 줄어드는 이동을 흉내 내어 끝까지 재생한 장면 id를 돌려준다. */
    private fun run(startSec: Int, change: (step: Int, remain: Int) -> Int = { _, r -> r }): List<String> {
        val s = StoryScheduler(story, arrivalMarginSec = 5, gapSec = 1)
        var remain = startSec; var step = 0
        val ids = mutableListOf<String>()
        while (true) {
            remain = change(step, remain)
            val d = s.next(Remaining.Known(remain))
            if (d !is Decision.Play) break
            ids += d.scene.id
            remain = maxOf(1, remain - d.scene.durationSec - 1)
            step++
        }
        return ids
    }

    @Test fun shortTripPlaysOnlyRequiredScenes() {
        assertEquals(listOf("intro", "cloud", "end"), run(45))
    }

    @Test fun longerTripAddsOptionalScenes() {
        assertEquals(listOf("intro", "cloud", "tree", "end"), run(62))
        assertEquals(listOf("intro", "cloud", "tree", "stream", "end"), run(80))
    }

    @Test fun trafficDelayAddsSceneMidway() {
        // 45초로 출발했지만 두 번째 장면 뒤에 20초가 늘어난다.
        val ids = run(45) { step, r -> if (step == 2) r + 20 else r }
        assertEquals(listOf("intro", "cloud", "tree", "end"), ids)
    }

    @Test fun earlyArrivalSkipsOptionalAndUsesShortEnding() {
        // 80초로 출발했지만 두 번째 장면 뒤에 남은 시간이 12초로 줄어든다.
        val ids = run(80) { step, r -> if (step == 2) 12 else r }
        assertEquals(listOf("intro", "cloud", "end_short"), ids)
    }

    @Test fun endingIsNeverRepeatedAndNothingStartsAfterIt() {
        val s = StoryScheduler(story)
        s.next(Remaining.Known(12))
        assertTrue(s.isEnded)
        assertTrue(s.next(Remaining.Known(600)) is Decision.Finished)
    }

    @Test fun eachSceneIsPlayedAtMostOnce() {
        val ids = run(600)
        assertEquals(ids.distinct(), ids)
        assertEquals("end", ids.last())
    }

    @Test fun unknownRemainingFallsBackToDefaultOrder() {
        val s = StoryScheduler(story)
        val ids = generateSequence { (s.next(Remaining.Unknown) as? Decision.Play)?.scene?.id }.toList()
        assertEquals(listOf("intro", "cloud", "end"), ids)
    }

    @Test fun arrivalEventGoesStraightToShortEnding() {
        val s = StoryScheduler(story)
        s.next(Remaining.Known(80))
        assertEquals("end_short", (s.next(Remaining.Arrived) as Decision.Play).scene.id)
    }

    @Test fun previewDoesNotChangeState() {
        val s = StoryScheduler(story)
        assertEquals(listOf("intro", "cloud", "tree", "end"), s.preview(Remaining.Known(62)).map { it.id })
        assertTrue(s.playedScenes.isEmpty())
        assertEquals("intro", (s.next(Remaining.Known(62)) as Decision.Play).scene.id)
    }

    @Test fun zeroOrStaleValueIsNotTreatedAsArrival() {
        assertEquals(Remaining.Unknown, RemainingInterpreter.interpret(0, 1000, 2000, false))
        assertEquals(Remaining.Unknown, RemainingInterpreter.interpret(60, 1000, 20_000, false))
        assertEquals(Remaining.Unknown, RemainingInterpreter.interpret(null, null, 2000, false))
        assertEquals(Remaining.Known(60), RemainingInterpreter.interpret(60, 1000, 2000, false))
        assertEquals(Remaining.Arrived, RemainingInterpreter.interpret(0, 1000, 2000, true))
    }
}
