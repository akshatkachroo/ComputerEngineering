package com.scribsync.scribsync.data

import java.util.Calendar
import java.util.Date

object MockData {

    private fun daysAgo(n: Int, hour: Int = 10, minute: Int = 30): Date =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -n)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    val meetings: List<Meeting> = listOf(
        Meeting(
            id = "1",
            title = "Q2 Planning Session",
            date = daysAgo(1, 10, 0),
            durationSeconds = 3612,
            transcriptEntries = listOf(
                TranscriptEntry("Speaker 1", "Alright, let's get started. Can everyone confirm they can hear me okay?", 0),
                TranscriptEntry("Speaker 2", "Loud and clear from my end. Thanks for setting this up.", 8000),
                TranscriptEntry("Speaker 3", "Same here, audio is good.", 14000),
                TranscriptEntry("Speaker 1", "Great. Today we're covering three things: the Q3 roadmap, resource allocation, and the offsite planning.", 20000),
                TranscriptEntry("Speaker 2", "I have updates on the roadmap. We've made significant progress on the authentication module.", 42000),
                TranscriptEntry("Speaker 3", "That's great to hear. Is the Q3 release timeline still intact?", 60000),
                TranscriptEntry("Speaker 1", "We're on schedule, though the on-device model integration might shift us by about a week.", 72000),
                TranscriptEntry("Speaker 2", "The ML team mentioned quantization needs more time to hit our accuracy targets without degrading latency.", 88000),
                TranscriptEntry("Speaker 3", "Let's build in buffer then. What does the resource picture look like for the next sprint?", 106000),
                TranscriptEntry("Speaker 1", "We have capacity for two more engineers if needed. I'd pull from the infrastructure side.", 120000),
                TranscriptEntry("Speaker 2", "Agreed. I'll reach out to the team leads and get back to you by EOD Thursday.", 136000),
                TranscriptEntry("Speaker 3", "Let's also make sure we have daily standups on that track starting Monday.", 150000),
            )
        ),
        Meeting(
            id = "2",
            title = "Sprint Retrospective",
            date = daysAgo(3, 14, 0),
            durationSeconds = 1847,
            transcriptEntries = listOf(
                TranscriptEntry("Speaker 1", "Welcome to the retro. Let's start with what went well this sprint.", 0),
                TranscriptEntry("Speaker 2", "The deployment pipeline improvements really paid off. We cut deploy time by 40 percent.", 12000),
                TranscriptEntry("Speaker 3", "Agreed. The automated testing coverage also caught two regressions before they hit staging.", 26000),
                TranscriptEntry("Speaker 1", "Good. What can we improve? Any blockers or friction points from the last two weeks?", 40000),
                TranscriptEntry("Speaker 2", "The handoff process between design and engineering still needs work. Some specs arrived incomplete.", 54000),
                TranscriptEntry("Speaker 3", "I'd add that sprint planning estimates were off for the database migration tasks. We underestimated by almost double.", 70000),
                TranscriptEntry("Speaker 1", "Good callouts. Let's add those as action items. Who wants to own the design-engineering handoff process?", 88000),
                TranscriptEntry("Speaker 2", "I can take the lead. I'll set up a working session with the design team early next week.", 104000),
                TranscriptEntry("Speaker 1", "Perfect. Let's close there. Great work everyone — solid sprint overall.", 118000),
            )
        ),
        Meeting(
            id = "3",
            title = "Design Review",
            date = daysAgo(5, 11, 0),
            durationSeconds = 2703,
            transcriptEntries = listOf(
                TranscriptEntry("Speaker 1", "Thanks for joining. We're reviewing the mockups for the new dashboard today.", 0),
                TranscriptEntry("Speaker 2", "Looking at the first screen, the navigation flow needs some rethinking for the tablet layout.", 14000),
                TranscriptEntry("Speaker 1", "Agreed. The bottom nav doesn't translate well to landscape mode on larger devices.", 28000),
                TranscriptEntry("Speaker 3", "We should consider a navigation rail for tablets — that's what Material 3 recommends for that form factor.", 42000),
                TranscriptEntry("Speaker 2", "That makes sense. It would also open up horizontal space for the content area significantly.", 56000),
                TranscriptEntry("Speaker 1", "Let's prototype both and do a quick A/B with a few internal users before we commit.", 70000),
                TranscriptEntry("Speaker 3", "I can have the rail navigation prototype ready by Thursday.", 82000),
                TranscriptEntry("Speaker 2", "Perfect. Let's reconvene Friday to review the feedback together.", 92000),
                TranscriptEntry("Speaker 1", "Sounds good. One last thing — let's make sure the color contrast ratios are checked for accessibility.", 104000),
                TranscriptEntry("Speaker 3", "I'll run the audit alongside the prototype work.", 116000),
            )
        )
    )

    val simulatedRecordingLines: List<Pair<String, String>> = listOf(
        "Speaker 1" to "Alright, let's kick things off. Thanks everyone for joining.",
        "Speaker 2" to "Happy to be here. I reviewed the materials you sent over.",
        "Speaker 1" to "Great. The main agenda items are the roadmap and the infrastructure upgrade.",
        "Speaker 3" to "Before we start, can we align on the success metrics for this quarter?",
        "Speaker 2" to "Good point. We were targeting a 20 percent reduction in end-to-end latency.",
        "Speaker 1" to "Correct. And the mobile team has a deliverable tied to that as well.",
        "Speaker 3" to "What's the timeline for the on-device model integration?",
        "Speaker 2" to "We're aiming for end of July. Model compression is the main blocker right now.",
        "Speaker 1" to "Let's make sure we have daily standups on that track starting Monday.",
        "Speaker 3" to "Agreed. I'll set up the recurring invite after this call.",
        "Speaker 2" to "One thing I want to flag — the quantization approach might affect accuracy on older hardware.",
        "Speaker 1" to "We should test against a representative set of devices. Can you set that up?",
    )
}
