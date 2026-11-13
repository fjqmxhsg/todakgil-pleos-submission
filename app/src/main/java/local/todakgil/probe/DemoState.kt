package local.todakgil.probe

import local.todakgil.probe.engine.Scene

/** 서비스가 쓰고 화면이 읽는 시연 상태. 같은 프로세스 안에서만 쓴다. */
object DemoState {
    enum class Mark { PLAYED, PLAYING, SKIPPED, PLANNED, NONE }

    var running = false
    var status = "대기 중"
    var storyTitle = ""
    var narrator = ""
    /** "실제 내비" 또는 "시험 입력". 시험 입력은 화면에 그대로 표시한다. */
    var source = ""
    var remainText = "-"
    var scenes: List<Scene> = emptyList()
    var marks: Map<String, Mark> = emptyMap()
    var audioKinds: Map<String, String> = emptyMap()
    var currentText = ""
    var lastReason = ""
    var listener: (() -> Unit)? = null

    fun changed() { listener?.invoke() }
}
