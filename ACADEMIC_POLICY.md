## Latest appearance update

The yellow notice box has been removed. The doubt form now displays only the plain sentence: **Only educational-purpose chats are allowed.** Moderation continues to block off-topic messages. Earlier verification descriptions below record the previous appearance.

# Academic-only moderation revision

ClassPulse now accepts academic, college and career questions and relevant answers. It is not a personal messaging platform.

## Strong platform rule

**ACADEMIC USE ONLY. Ask specific questions about studies, college services, admissions, exams, projects, internships and careers. Answers must help with the academic question. Personal chat, romantic messages, weekend small talk, harassment and promotions are not allowed. Selecting a subject or using Post Anyway cannot bypass this rule.**

Examples blocked: “I love u”, “I like u”, “how was your weekend”, “college I love u”, and an academic question followed by “I love you”.

Examples allowed: “How does Java garbage collection work?”, “How do I apply for a scholarship?”, “How should I prepare for placements?”, and “Is the college library open this weekend?”. Words such as love/weekend are not automatically forbidden when they are used in a genuine academic context, for example studying love in psychology.

## What changed

- Added AcademicPolicy.java: local relevance validation that runs before toxicity checks and database writes. It works even when Python is offline.
- Doubt submission requires recognizable educational/college/career context and a specific question or help request. An unrelated message cannot become acceptable just by selecting ML/Java/another subject.
- Explicit romantic, social and promotional patterns are rejected, including basic punctuation/case/Unicode normalization and common shorthand.
- Peer suggestions, teacher answers and rejection reasons receive relevance validation before storage; toxicity is also checked on teacher responses.
- Questions that fail relevance return blocked=true, reason=OFF_TOPIC and a specific explanation. Blocked text is not inserted into doubts or moderation_log.
- Added College & Career and General Academics categories, with matching teacher registration/routing support.
- Added a prominent yellow academic-only notice above the doubt form, clearer placeholders, and an academic-only reminder in the peer-answer modal.
- Added AcademicPolicyTests.java and server regressions for selected-subject/forcePost bypass, database non-insertion, teacher/peer checks, offline rejection and career routing.

## Verification

- 62 existing integration assertions passed.
- 108 server regression assertions passed.
- 52 academic-policy examples passed, including legitimate questions that contain weekend/love and the user's personal-chat examples.
- 7 frontend checks passed.
- All Java sources were compiled into Java 17-compatible class files; JavaScript syntax passed.
- Browser verification confirmed the strong notice, both new categories and the visible blocking response for “i love u”.

Logs are in verification/. The browser screenshot is in preview/message-blocked.png. Existing original Java class names are retained.

## Run this updated version

1. Stop your old server with Ctrl+C in its terminal. A running Java process continues using its previously loaded classes until restarted.
2. Extract ClassPulseWeb_AcademicOnly.zip into a new folder. Do not run the old demo.cmd.
3. Open ClassPulseWeb_AcademicOnly and run demo.cmd.
4. Open http://127.0.0.1:8080 and refresh the page.

Demo student: DEMO_STUDENT / DemoStudent123!

Demo teacher: DEMO_TEACHER / DemoTeacher123!

The ZIP does not contain an existing demo.db: demo.cmd creates a fresh practice database, including a teacher that handles College & Career and General Academics. Your prior project's files/database are not overwritten. start.cmd uses the preserved main database; existing teachers must handle a new category to see its questions. Teacher subject changes are not exposed in the current UI, so register an appropriate new teacher with the configured faculty key when needed.

## Scope and limits

This is a conservative English-language rule filter, not a newly trained semantic classifier. It rejects unrecognized or vague text and may require a genuine question to be rephrased with its actual academic topic. No finite rule list can recognize every possible off-topic message or evasion; production use needs evaluated semantic relevance moderation and/or teacher review. The supplied optional Python model is a toxicity model, not an academic-relevance model; it has not been retrained or falsely presented as one.

The new rule applies to new submissions. Existing stored questions and answers are preserved and are not automatically deleted or reclassified. The previous revision's security, data migrations and deployment limitations still apply. PREVIOUS_REVIEW.md records that earlier review; its counts describe the earlier version.
