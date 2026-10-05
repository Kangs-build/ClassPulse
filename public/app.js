const API = "";  // same origin

let currentAdmin = null;
let verificationRequest = null;
let adminTeachers = [];
let currentStudent = null;
let currentTeacher = null;
let currentToken = sessionStorage.getItem("classpulse_token") || null;
let pendingDoubtPost = null; // holds {subject, description} while similar-doubt confirmation is shown

function showView(viewId) {
  document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
  document.getElementById(viewId).classList.add("active");
}

function getAuthHeaders(extra = {}) {
  const headers = { "Content-Type": "application/json", ...extra };
  if (currentToken) {
    headers["X-Session-Token"] = currentToken;
  }
  return headers;
}

async function apiFetch(url, options = {}) {
  options.headers = getAuthHeaders(options.headers || {});
  let res;
  try { res = await fetch(url, options); }
  catch (e) { alert("Cannot reach ClassPulse. Check that the server is running."); return null; }
  if (res.status === 401) {
    const wasLoggedIn = Boolean(currentToken);
    clearSessionAndLogout();
    if (wasLoggedIn) alert("Session expired or invalid. Please log in again.");
    return null;
  }
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    alert(error.error || "Request failed. Please try again.");
    return null;
  }
  return res;
}

async function logout() {
  if (currentToken) {
    await fetch("/api/logout", {
      method: "POST",
      headers: getAuthHeaders()
    }).catch(() => {});
  }
  clearSessionAndLogout();
}

function clearSessionAndLogout() {
  currentToken = null;
  currentStudent = null;
  currentTeacher = null;
  currentAdmin = null;
  sessionStorage.removeItem("classpulse_admin");
  sessionStorage.removeItem("classpulse_token");
  sessionStorage.removeItem("classpulse_student");
  sessionStorage.removeItem("classpulse_teacher");
  showView("view-landing");
}

// Restore session on page refresh if active
window.addEventListener("DOMContentLoaded", () => {
  fetch('/api/config').then(r=>r.json()).then(c=>{document.getElementById('demo-guide').hidden=!c.demo;}).catch(()=>{});
  const admin=sessionStorage.getItem('classpulse_admin');
  if(currentToken && admin) {try {currentAdmin=JSON.parse(admin);enterAdminDashboard();return;}catch(e){clearSessionAndLogout();}}

  if (currentToken) {
    const savedStudent = sessionStorage.getItem("classpulse_student");
    const savedTeacher = sessionStorage.getItem("classpulse_teacher");
    if (savedStudent) {
      try {
        currentStudent = JSON.parse(savedStudent);
        enterStudentDashboard();
      } catch (e) {
        clearSessionAndLogout();
      }
    } else if (savedTeacher) {
      try {
        currentTeacher = JSON.parse(savedTeacher);
        enterTeacherDashboard();
      } catch (e) {
        clearSessionAndLogout();
      }
    }
  }
});

// ---------- Student ----------

async function publicRequest(url, body) {
  try {
    const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    const d=await r.json();if(!r.ok){alert(d.error||'Request failed');return null;}return d;
  } catch(e){alert('Cannot reach ClassPulse. Check that the server is running.');return null;}
}
function acceptLogin(data) {
  clearSessionAndLogout();currentToken=data.token;sessionStorage.setItem('classpulse_token',currentToken);
  for(const role of ['student','teacher','admin']) if(data[role]) {
    sessionStorage.setItem('classpulse_'+role,JSON.stringify(data[role]));
    if(role==='student'){currentStudent=data.student;enterStudentDashboard();}
    if(role==='teacher'){currentTeacher=data.teacher;enterTeacherDashboard();}
    if(role==='admin'){currentAdmin=data.admin;enterAdminDashboard();}
  }
  document.querySelectorAll('input[type=password]').forEach(i=>i.value='');
  verificationRequest=null;document.getElementById('demo-inbox-message').textContent='';
}
async function accountLogin(role) {
  const email=document.getElementById(role+'-login-email').value.trim();
  const password=document.getElementById(role+'-login-password').value;
  const d=await publicRequest('/api/'+role+'/login',{email,password});if(d)acceptLogin(d);
}
async function beginEnrollment(role) {
  const email=document.getElementById(role+'-reg-email').value.trim();
  const d=await publicRequest('/api/auth/start',{email,role});if(!d)return;
  verificationRequest=d.requestId;
  const p=d.profile;document.getElementById('verification-profile').textContent=p.email+' · '+p.name+' · '+(role==='student'?'Roll '+p.userId+' · Section '+p.section:'Subjects '+p.subjects+' · Sections '+p.sections);
  document.getElementById('demo-inbox-button').hidden=!d.demo;
  for(const id of ['verification-code','verification-password','verification-confirm'])document.getElementById(id).value='';
  document.getElementById('demo-inbox-message').textContent='';showView('view-verification');
}
async function openDemoInbox() {
  const d=await publicRequest('/api/demo/inbox',{requestId:verificationRequest});if(d)document.getElementById('demo-inbox-message').textContent=d.message+' To: '+d.email+' — Code: '+d.code;
}
async function completeEnrollment() {
  const password=document.getElementById('verification-password').value;
  if(password!==document.getElementById('verification-confirm').value)return alert('Passwords do not match');
  const d=await publicRequest('/api/auth/complete',{requestId:verificationRequest,code:document.getElementById('verification-code').value.trim(),password});if(d)acceptLogin(d);
}
function enterAdminDashboard(){showView('view-admin-dashboard');loadAdminRoster();}
async function loadAdminRoster(){
  const tr=await apiFetch('/api/admin/teachers');if(!tr)return;adminTeachers=(await tr.json()).teachers;
  document.getElementById('admin-teacher-list').innerHTML=adminTeachers.map((t,i)=>`<div class="doubt-card"><strong>${escapeHtml(t.name)}</strong><p>${escapeHtml(t.email)} · ${t.approved?'Approved':'Pending approval'} · ${t.verified?'Activated':'Not activated'}</p><p>Subjects: ${escapeHtml(t.subjects||'None')} · Sections: ${escapeHtml(t.sections||'None')}</p><button class="secondary" data-index="${i}" onclick="editTeacherApproval(Number(this.dataset.index))">Edit Approval / Assignments</button></div>`).join('');
  const sr=await apiFetch('/api/admin/students');if(!sr)return;
  document.getElementById('admin-student-list').innerHTML=(await sr.json()).students.map(s=>`<div class="doubt-card">${escapeHtml(s.name)} · ${escapeHtml(s.email)} · ${escapeHtml(s.userId)} · Section ${escapeHtml(s.section)} · ${s.verified?'Activated':'Awaiting verification'}</div>`).join('');
}
function editTeacherApproval(i){const t=adminTeachers[i];for(const field of ['email','name','subjects','sections'])document.getElementById('admin-teacher-'+field).value=t[field];document.getElementById('admin-teacher-approved').checked=t.approved;document.getElementById('admin-teacher-email').focus();}
async function saveTeacherApproval(){
  const b={};for(const f of ['email','name','subjects','sections'])b[f]=document.getElementById('admin-teacher-'+f).value.trim();b.approved=document.getElementById('admin-teacher-approved').checked;
  const r=await apiFetch('/api/admin/teachers',{method:'POST',body:JSON.stringify(b)});if(r){document.getElementById('admin-save-feedback').textContent='Teacher approval and assignments saved.';loadAdminRoster();}
}
async function addApprovedStudent(){
  const b={};for(const [key,id]of [['email','email'],['name','name'],['rollNumber','roll'],['section','section']])b[key]=document.getElementById('admin-student-'+id).value.trim();
  const r=await apiFetch('/api/admin/students',{method:'POST',body:JSON.stringify(b)});if(r){document.getElementById('admin-save-feedback').textContent='Approved student added.';loadAdminRoster();}
}

function enterStudentDashboard() {
  if (!currentStudent) return showView("view-student-login");
  document.getElementById("student-name-display").textContent = currentStudent.name;
  showView("view-student-dashboard");
  switchStudentTab("post");
}

function switchStudentTab(tab) {
  document.querySelectorAll("#view-student-dashboard .tab-btn").forEach(b => b.classList.remove("active"));
  document.querySelectorAll("#view-student-dashboard .tab-content").forEach(c => c.classList.remove("active"));
  document.querySelector(`#view-student-dashboard .tab-btn[onclick*="${tab}"]`).classList.add("active");
  document.getElementById("student-tab-" + tab).classList.add("active");

  if (tab === "mine") loadMyDoubts();
  if (tab === "class") loadClassDoubts();
  if (tab === "faq") loadStudentFaq();
}

async function sendLivePulse() {
  const subject = document.getElementById("doubt-subject").value;
  const res = await apiFetch("/api/student/pulse", {
    method: "POST",
    body: JSON.stringify({ subject })
  });
  if (!res) return;
  const feedback = document.getElementById("pulse-feedback");
  const data = await res.json();
  feedback.textContent = data.pulsed ? `⚡ Signaled live confusion for ${subject}!` : "You already sent a signal for this subject in the last 30 minutes.";
  setTimeout(() => feedback.textContent = "", 4000);
}

async function upvoteDoubt(doubtId) {
  const res = await apiFetch("/api/student/upvote", {
    method: "POST",
    body: JSON.stringify({ doubtId })
  });
  if (!res) return;
  const data = await res.json();
  alert(data.upvoted ? "👍 Upvoted!" : "You already support this doubt, or it is closed.");
  if (currentStudent) { loadMyDoubts(); loadClassDoubts(); }
}

async function postDoubt(forcePost = false) {
  const subject = document.getElementById("doubt-subject").value;
  const description = document.getElementById("doubt-description").value.trim();
  if (!description) return alert("Please describe your doubt");

  const res = await apiFetch("/api/student/doubt", {
    method: "POST",
    body: JSON.stringify({ subject, description, forcePost })
  });
  if (!res) return;
  const data = await res.json();
  const resultDiv = document.getElementById("post-result");

  if (data.blocked) {
    resultDiv.innerHTML = `<p class="error">${escapeHtml(data.message)}</p>`;
    return;
  }

  if (data.similarFound) {
    let html = `<div class="similar-box"><p>Similar doubt(s) already posted:</p>`;
    data.similarDoubts.forEach(d => {
      html += `<div class="doubt-card">
                 <div class="meta"><span>${d.doubtId} · ${d.subject}</span><span>👍 ${d.upvoteCount || 1}</span></div>
                 <div class="desc">${escapeHtml(d.description)}</div>
                 <button class="secondary" onclick="upvoteDoubt('${d.doubtId}')">👍 +1 Me Too</button>
               </div>`;
    });
    html += `<button onclick="postDoubt(true)" style="margin-top:0.5rem;">Post Anyway</button></div>`;
    resultDiv.innerHTML = html;
    return;
  }

  if (data.posted) {
    resultDiv.innerHTML = `<p style="color:#7ee787;">Posted! Your Doubt ID: ${data.doubt.doubtId}</p>`;
    document.getElementById("doubt-description").value = "";
  }
}

async function loadMyDoubts() {
  const res = await apiFetch("/api/student/mydoubts");
  if (!res) return;
  const data = await res.json();
  const container = document.getElementById("my-doubts-list");

  if (data.doubts.length === 0) {
    container.innerHTML = `<p class="hint">You haven't posted any doubts yet.</p>`;
    return;
  }

  container.innerHTML = data.doubts.map(d => `
    <div class="doubt-card">
      <div class="meta">
        <span>${d.doubtId} · ${d.subject}</span>
        <div>
          <span>👍 ${d.upvoteCount || 1}</span>
          <span class="${d.priority === 'Escalated' || d.priority === 'High' || d.priority === 'Urgent' ? 'priority-escalated' : ''}">${d.priority} (${d.status})</span>
        </div>
      </div>
      <div class="desc">${escapeHtml(d.description)}</div>
      ${d.status === 'Resolved' ? `<div class="answer">Answer: ${escapeHtml(d.teacherResponse)}</div>` : ''}
      ${d.status === 'Rejected' ? `<div class="error">Reason: ${escapeHtml(d.rejectionReason)}</div>` : ''}
      ${d.responseFileName ? `<button class="link-button" data-file="${escapeHtml(d.responseFileName)}" onclick="downloadAttachment(this.dataset.file)">📎 Download attachment</button>` : ''}
      ${d.status === 'Pending' ? `<div class="actions" style="margin-top:0.5rem;"><button class="secondary" onclick="openPeerAnswerModal('${d.doubtId}')">💡 Suggest Peer Answer</button></div>` : ''}
    </div>
  `).join("");
}

async function loadClassDoubts() {
  const res = await apiFetch("/api/student/classdoubts");
  if (!res) return;
  const data = await res.json();
  const container = document.getElementById("class-doubts-list");
  container.innerHTML = data.doubts.length ? data.doubts.map(d => `
    <div class="doubt-card">
      <div class="meta"><span>${escapeHtml(d.doubtId)} · ${escapeHtml(d.subject)}</span><span>👍 ${d.upvoteCount} · ${escapeHtml(d.priority)}</span></div>
      <div class="desc">${escapeHtml(d.description)}</div>
      <button class="secondary" data-doubt="${escapeHtml(d.doubtId)}" onclick="upvoteDoubt(this.dataset.doubt)">👍 Me Too</button>
      <button class="secondary" data-doubt="${escapeHtml(d.doubtId)}" onclick="openPeerAnswerModal(this.dataset.doubt)">💡 Suggest Peer Answer</button>
    </div>`).join("") : '<p class="hint">No pending questions in your section.</p>';
}

async function loadStudentFaq() {
  const res = await fetch("/api/archive");
  const data = await res.json();
  renderArchive(data.archive, "student-faq-list");
}

// ---------- Teacher ----------

function enterTeacherDashboard() {
  if (!currentTeacher) return showView("view-teacher-login");
  document.getElementById("teacher-name-display").textContent = currentTeacher.name;
  document.getElementById("teacher-subjects-display").textContent = currentTeacher.subjectsHandled.join(", ");
  document.getElementById("teacher-sections-display").textContent=(currentTeacher.assignedSections||[]).join(", ");
  showView("view-teacher-dashboard");
  switchTeacherTab("pending");
}

function switchTeacherTab(tab) {
  document.querySelectorAll("#view-teacher-dashboard .tab-btn").forEach(b => b.classList.remove("active"));
  document.querySelectorAll("#view-teacher-dashboard .tab-content").forEach(c => c.classList.remove("active"));
  document.querySelector(`#view-teacher-dashboard .tab-btn[onclick*="${tab}"]`).classList.add("active");
  document.getElementById("teacher-tab-" + tab).classList.add("active");

  if (tab === "pending") {
    loadPendingDoubts();
    loadTeacherPulse();
  }
  if (tab === "publish") loadUnpublished();
  if (tab === "faq") loadTeacherFaq();
}

async function loadUnpublished() {
  const res = await apiFetch("/api/teacher/unpublished");
  if (!res) return;
  const data = await res.json();
  const container = document.getElementById("unpublished-doubts-list");

  if (data.doubts.length === 0) {
    container.innerHTML = `<p class="hint">No resolved doubts waiting to be published.</p>`;
    return;
  }

  container.innerHTML = data.doubts.map(d => `
    <div class="doubt-card">
      <div class="meta"><span>${d.doubtId} · ${d.subject}</span></div>
      <div class="desc"><strong>Q:</strong> ${escapeHtml(d.description)}</div>
      <div class="answer"><strong>A:</strong> ${escapeHtml(d.teacherResponse)}</div>
      <div class="actions">
        <button onclick="publishDoubt('${d.doubtId}')">Publish to FAQ</button>
      </div>
    </div>
  `).join("");
}

async function publishDoubt(doubtId) {
  const res = await apiFetch("/api/teacher/publish", {
    method: "POST",
    body: JSON.stringify({ doubtId })
  });
  if (!res) return;
  loadUnpublished();
}

async function exportTeacherReport() {
  if (!currentToken) return alert("Please log in as a teacher");
  const res = await apiFetch("/api/teacher/report");
  if (!res) return;
  const htmlText = await res.text();
  const reportWindow = window.open("", "_blank");
  if (reportWindow) {
    reportWindow.document.write(htmlText);
    reportWindow.document.close();
  } else {
    alert("Pop-up blocked. Please allow pop-ups for this site to view the report.");
  }
}

async function loadTeacherPulse() {
  const res = await apiFetch("/api/teacher/pulse");
  if (!res) return;
  const data = await res.json();
  const display = document.getElementById("teacher-pulse-display");
  const stats = data.pulseStats || {};
  const entries = Object.entries(stats);
  if (entries.length === 0) {
    display.innerHTML = `<span style="color:#7ee787;">🟢 Normal activity (No confusion signals in last 30m)</span>`;
    return;
  }
  display.innerHTML = entries.map(([sub, count]) => {
    let color = "#7ee787";
    let badge = "🟢 Low";
    if (count >= 5) { color = "#ff7b72"; badge = "🔴 HIGH CONFUSION!"; }
    else if (count >= 2) { color = "#ffa657"; badge = "🟡 Moderate"; }
    return `<div style="color:${color}; margin-top: 4px;">⚡ <strong>${sub}</strong>: ${count} signal(s) in last 30 min — ${badge}</div>`;
  }).join("");
}

async function loadPendingDoubts() {
  const res = await apiFetch("/api/teacher/pending");
  if (!res) return;
  const data = await res.json();
  if(data.teacher) {
    currentTeacher=data.teacher;sessionStorage.setItem('classpulse_teacher',JSON.stringify(currentTeacher));
    document.getElementById('teacher-name-display').textContent=currentTeacher.name;
    document.getElementById('teacher-subjects-display').textContent=currentTeacher.subjectsHandled.join(', ');
    document.getElementById('teacher-sections-display').textContent=currentTeacher.assignedSections.join(', ');
  }
  const container = document.getElementById("pending-doubts-list");

  if (data.doubts.length === 0) {
    container.innerHTML = `<p class="hint">No pending doubts right now.</p>`;
    return;
  }

  // Fetch peer answers for all pending doubts in parallel
  const peerAnswersMap = {};
  await Promise.all(data.doubts.map(async d => {
    try {
      const paRes = await apiFetch(`/api/doubt/peer-answers?doubtId=${encodeURIComponent(d.doubtId)}`);
      if (paRes && paRes.ok) {
        const paData = await paRes.json();
        peerAnswersMap[d.doubtId] = paData.peerAnswers || [];
      }
    } catch (e) {}
  }));

  // Anonymity note: these cards intentionally show only doubtId/subject/description -
  // the backend's toTeacherJson() never includes rollNumber.
  container.innerHTML = data.doubts.map(d => {
    const peerAnswers = peerAnswersMap[d.doubtId] || [];
    let peerHtml = "";
    if (peerAnswers.length > 0) {
      peerHtml = `<div class="peer-answers-section" style="margin-top:8px; padding:8px; background:rgba(255,255,255,0.05); border-radius:6px; border:1px solid rgba(255,255,255,0.1);">
        <strong style="color:#7ee787;">💡 Peer Suggested Answers (${peerAnswers.length}):</strong>`;
      peerAnswers.forEach(pa => {
        const safeAnswer = encodeURIComponent(pa.answerText).replace(/'/g, "%27");
        peerHtml += `<div style="margin-top:6px; font-size:0.9rem; display:flex; justify-content:space-between; align-items:center; background:rgba(0,0,0,0.2); padding:6px; border-radius:4px;">
          <span><em>"${escapeHtml(pa.answerText)}"</em></span>
          <button style="margin-left:8px; padding:3px 10px; font-size:0.8rem;" onclick="approvePeerAnswer('${d.doubtId}', decodeURIComponent('${safeAnswer}'))">Approve & Resolve</button>
        </div>`;
      });
      peerHtml += `</div>`;
    }

    return `
      <div class="doubt-card">
        <div class="meta">
          <span>${d.doubtId} · ${d.subject}</span>
          <div>
            <span style="margin-right:8px; background:rgba(255,255,255,0.1); padding:2px 6px; border-radius:4px;">👍 ${d.upvoteCount || 1}</span>
            <span class="${d.priority === 'Escalated' || d.priority === 'High' || d.priority === 'Urgent' ? 'priority-escalated' : ''}">${d.priority}</span>
          </div>
        </div>
        <div class="desc">${escapeHtml(d.description)}</div>
        ${peerHtml}
        <div class="actions" style="margin-top:10px;">
          <button onclick="respondToDoubt('${d.doubtId}', 'resolve')">Resolve</button>
          <button class="reject" onclick="respondToDoubt('${d.doubtId}', 'reject')">Reject</button>
        </div>
      </div>
    `;
  }).join("");
}

function approvePeerAnswer(doubtId, answerText) {
  resolveContext = { doubtId, action: 'resolve' };
  document.getElementById("resolve-modal-title").textContent = "Resolve Doubt with Approved Peer Answer";
  document.getElementById("resolve-text").value = answerText;
  document.getElementById("resolve-file").value = "";
  document.getElementById("resolve-modal").style.display = "flex";
}

let peerAnswerDoubtId = null;

function openPeerAnswerModal(doubtId) {
  peerAnswerDoubtId = doubtId;
  document.getElementById("peer-answer-text").value = "";
  document.getElementById("peer-answer-modal").style.display = "flex";
}

function closePeerAnswerModal() {
  document.getElementById("peer-answer-modal").style.display = "none";
  peerAnswerDoubtId = null;
}

async function submitPeerAnswer() {
  const text = document.getElementById("peer-answer-text").value.trim();
  if (!text) return alert("Please enter your suggested answer");

  const res = await apiFetch("/api/student/peer-answer", {
    method: "POST",
    body: JSON.stringify({ doubtId: peerAnswerDoubtId, answerText: text })
  });
  if (!res) return;
  alert("💡 Peer suggestion submitted anonymously!");
  closePeerAnswerModal();
}

let resolveContext = null; // {doubtId, action}

function respondToDoubt(doubtId, action) {
  resolveContext = { doubtId, action };
  document.getElementById("resolve-modal-title").textContent =
      action === "resolve" ? "Resolve Doubt" : "Reject Doubt";
  document.getElementById("resolve-text").value = "";
  document.getElementById("resolve-file").value = "";
  document.getElementById("resolve-modal").style.display = "flex";
}

function closeResolveModal() {
  document.getElementById("resolve-modal").style.display = "none";
  resolveContext = null;
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(",")[1]); // strip data:...;base64, prefix
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function submitResolve() {
  const text = document.getElementById("resolve-text").value.trim();
  if (!text) return alert("Please enter an answer or reason");

  const fileInput = document.getElementById("resolve-file");
  let fileName = "", fileData = "";
  if (fileInput.files.length > 0) {
    const file = fileInput.files[0];
    const allowed = ["pdf", "png", "jpg", "jpeg", "doc", "docx", "mp4"];
    const ext = file.name.split(".").pop().toLowerCase();
    if (!allowed.includes(ext)) {
      return alert("Invalid file type ." + ext + ". Allowed types: pdf, png, jpg, jpeg, doc, docx, mp4");
    }
    if (file.size > 10 * 1024 * 1024) {
      return alert("File size exceeds 10 MB limit");
    }
    fileName = file.name;
    fileData = await fileToBase64(file);
  }

  const res = await apiFetch("/api/teacher/resolve", {
    method: "POST",
    body: JSON.stringify({
      doubtId: resolveContext.doubtId,
      action: resolveContext.action,
      text,
      fileName,
      fileData
    })
  });
  if (!res) return;

  closeResolveModal();
  loadPendingDoubts();
}

function openPasswordModal() {
  document.getElementById("pwd-old").value = "";
  document.getElementById("pwd-new").value = "";
  document.getElementById("pwd-confirm").value = "";
  document.getElementById("password-modal").style.display = "flex";
}

function closePasswordModal() {
  document.getElementById("password-modal").style.display = "none";
}

async function submitPasswordChange() {
  const oldPassword = document.getElementById("pwd-old").value;
  const newPassword = document.getElementById("pwd-new").value;
  const confirmPassword = document.getElementById("pwd-confirm").value;

  if (!oldPassword || !newPassword || !confirmPassword) {
    return alert("Please fill in all password fields");
  }
  if (newPassword !== confirmPassword) {
    return alert("New passwords do not match");
  }

  const res = await apiFetch("/api/user/password", {
    method: "POST",
    body: JSON.stringify({ oldPassword, newPassword })
  });
  if (!res) return;
  const data = await res.json();
  if (data.updated) {
    alert("Password updated successfully!");
    closePasswordModal();
  }
}

async function loadTeacherFaq() {
  const res = await fetch("/api/archive");
  const data = await res.json();
  renderArchive(data.archive, "teacher-faq-list");
}

// ---------- Shared / Archive ----------

async function loadArchive() {
  showView("view-archive");
  const res = await fetch("/api/archive");
  const data = await res.json();
  renderArchive(data.archive, "standalone-archive-list");
}

function renderArchive(archive, containerId) {
  const container = document.getElementById(containerId);
  if (archive.length === 0) {
    container.innerHTML = `<p class="hint">No resolved doubts yet.</p>`;
    return;
  }
  container.innerHTML = archive.map(d => `
    <div class="doubt-card">
      <div class="meta"><span>${d.doubtId} · ${d.subject}</span></div>
      <div class="desc"><strong>Q:</strong> ${escapeHtml(d.description)}</div>
      <div class="answer"><strong>A:</strong> ${escapeHtml(d.teacherResponse)}</div>
      ${d.responseFileName ? `<button class="link-button" data-file="${escapeHtml(d.responseFileName)}" onclick="downloadAttachment(this.dataset.file)">📎 Download attachment</button>` : ''}
    </div>
  `).join("");
}

let searchDebounceTimer = null;

function searchFaq(inputId, containerId) {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(async () => {
    const inputEl = document.getElementById(inputId);
    if (!inputEl) return;
    const query = inputEl.value.trim();
    if (!query) {
      loadArchiveForContainer(containerId);
      return;
    }
    const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (!res.ok) return;
    const data = await res.json();
    renderSearchResults(data.results || [], containerId, query);
  }, 300);
}

async function loadArchiveForContainer(containerId) {
  const res = await fetch("/api/archive");
  const data = await res.json();
  renderArchive(data.archive, containerId);
}

function renderSearchResults(results, containerId, query) {
  const container = document.getElementById(containerId);
  if (results.length === 0) {
    container.innerHTML = `<p class="hint">No matching doubts found for "${escapeHtml(query)}".</p>`;
    return;
  }
  container.innerHTML = results.map(d => `
    <div class="doubt-card">
      <div class="meta"><span>${d.doubtId} · ${d.subject}</span></div>
      <div class="desc"><strong>Q:</strong> ${highlightTerm(d.description, query)}</div>
      <div class="answer"><strong>A:</strong> ${highlightTerm(d.teacherResponse, query)}</div>
      ${d.responseFileName ? `<button class="link-button" data-file="${escapeHtml(d.responseFileName)}" onclick="downloadAttachment(this.dataset.file)">📎 Download attachment</button>` : ''}
    </div>
  `).join("");
}

function highlightTerm(text, query) {
  text = String(text || "");
  if (!query) return escapeHtml(text);
  const regex = new RegExp(escapeRegExp(query), "gi");
  let result = "", last = 0;
  for (const match of text.matchAll(regex)) {
    result += escapeHtml(text.slice(last, match.index)) + '<mark>' + escapeHtml(match[0]) + '</mark>';
    last = match.index + match[0].length;
  }
  return result + escapeHtml(text.slice(last));
}

function escapeRegExp(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function escapeHtml(str) {
  return String(str || "").replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

async function downloadAttachment(file) {
  const res = await fetch('/api/download?file=' + encodeURIComponent(file), {headers: getAuthHeaders()});
  if (!res.ok) return alert('Attachment unavailable or login required.');
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a'); a.href = url; a.download = file; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
