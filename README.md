# ClassPulse — College Login Prototype

This revision contains Java source, compiled Java 17 class files, the SQLite driver, web assets, and an isolated college-login demo. Java 17 or newer is required to run it; a JDK is required only to rebuild or run the Java test script.

## Run the presentation

1. Stop any previous ClassPulse server with Ctrl+C in its command window.
2. Extract the whole ZIP to a new folder. Do not run a CMD file from inside the ZIP.
3. Double-click `demo.cmd`. Keep its command window open.
4. Open http://127.0.0.1:8080/ in your browser. Refresh if an older page is cached.

You can also open Command Prompt in the extracted folder and type `demo.cmd`.
If port 8080 is already in use, stop the earlier server. If Java is missing, install Java 17 or newer and reopen Command Prompt. `build.cmd` rebuilds source; `test.cmd` runs isolated checks without modifying either application database.

## Student demonstration

Choose Student Login → First time? Activate approved account. Enter `student1@college.example`, request a verification code, and open the Demo Email Inbox. Copy the displayed six-digit code, choose a separate ClassPulse password (8–128 characters), confirm it, and activate.

The college roster supplies name, roll number and section; students cannot set those through registration. Subsequent logins use college email and the ClassPulse password chosen at activation. Never enter a real college mailbox password.

| Fictional address | Roll number | Section |
| --- | --- | --- |
| student1@college.example | DEMO_STUDENT | L |
| student2@college.example | DEMO_PEER | L |
| student3@college.example | DEMO_OTHER | M |

The students initially have no activated login password. Each activates individually. Try an unlisted college email or a Gmail address to demonstrate rejection.

## Teacher and administrator demonstration

1. Select Administrator Login. Email: `admin@college.example`; ClassPulse password: `AdminDemo123!`.
2. Under Teacher Roster, select Edit Approval / Assignments for `teacher@college.example`.
3. Keep subjects `Java,DAA` and section `L`, check Approved for activation and access, then Save Teacher.
4. Log out. Select Teacher Login → First time? Activate approved account. Enter `teacher@college.example`.
5. Request the code, open its Demo Email Inbox, and create the teacher's separate ClassPulse password.
6. Review Java/DAA doubts from section L, resolve an academic question and publish the resolution to FAQ.

Before administrator approval, teacher activation is rejected. Teachers cannot select their own subjects or sections. The administrator can add invitations, change assignments, revoke approval, and add students to the approved roster.

Assignments mean every selected subject in every selected section. For example `Java,DAA` and `L,M` authorize both subjects in both sections. Approval requires at least one supported subject and one section. Subject choices are the same nine categories in the student question form.

Revoking approval blocks the next protected request and invalidates that session. Updating assignments takes effect on existing sessions immediately. Teacher pending lists, resolutions, publications, private attachments, peer answers, confusion counts and exported reports all enforce section and subject scope. Published FAQs remain public. Private question author identities are not shown to classmates or teachers; the administrator and database retain the approved identities.

## What changed

- Approved college-email login replaced public roll-number/teacher-ID self-registration.
- Added expiring, single-use verification codes, retry limits and resend cooldown.
- Added administrator teacher approval, subject/section assignments, and roster management.
- Closed legacy registration API bypasses and added approval checks on protected requests.
- Kept the educational-purpose note as plain text without a yellow highlight box.
- Retained question posting, similarity suggestions, voting, anonymous peer answers, resolutions, attachments, published FAQ, search, confusion signals and printable reports.
- Included updated source and compiled class files, automated verification, and screenshots of the new screens.

## Prototype boundaries

The Demo Email Inbox is a simulation: **no email is sent and no real mailbox ownership is proved**. It is enabled only by `demo.cmd` and uses fictional `@college.example` addresses. The request identifier binds the inbox to the activation request; this is for presentation, not a real email security boundary.

`demo.cmd` creates and reuses `data/college-demo.db`, independently of the normal-run database `data/classpulse.db`. Existing demo passwords and approvals are kept on subsequent runs. To reset your presentation, stop its server and rename `college-demo.db` as a backup; the next demo run creates a fresh dataset.

`start.cmd` uses `data/classpulse.db` with the demo inbox disabled. Saved databases are not included in this public repository. Use `demo.cmd` for the ready-to-present college-login prototype. Production enrollment returns a clear unavailable response until real email delivery or college SSO is implemented. Production also needs administrator provisioning, a real college domain and roster, HTTPS, account recovery, login rate limiting, audit logs, and deployment/session hardening. Never use the known demo administrator password for deployment.

The existing English academic filter is rule-based. It blocks tested profanity, romantic messages and small talk, but cannot guarantee perfect relevance or understand every valid academic question. Mixed-topic text and unfamiliar subjects can produce mistakes; contextual peer answers can be rejected. This revision changes authentication and faculty authorization, not that classifier's known limits. The optional Python ML service is retained; without it, local academic and profanity checks still run. See ACADEMIC_POLICY.md.

The server serializes requests around one shared SQLite connection. That suits a classroom prototype; slow moderation calls can delay other requests, so concurrent production use needs a different connection/request design.

## Files

`src/`: revised Java source. `out/`: compiled Java 17 classes. `public/`: HTML, CSS and JavaScript. `lib/`: bundled SQLite driver. `verification/`: checks run on this revision. `preview/`: browser screenshots. 

## Credits

Original implementation assistance: Mita Clark, as acknowledged by the project team.

## Public repository contents

Java source and compiled Java 17 classes are included. Saved databases, user uploads, local settings and report documents are excluded. The demo creates its database on first launch. Use only fictional demo accounts for presentation.
