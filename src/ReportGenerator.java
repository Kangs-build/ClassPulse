import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Pure Java Executive Report Generator for ClassPulse.
 * Compiles database analytics, upvote trends, resolution metrics,
 * and resolved FAQs into a self-contained, printable HTML/PDF document.
 */
public class ReportGenerator {

    public static String generateHtmlReport(List<String> subjects, Teacher teacher, DoubtsManager doubtsManager) throws SQLException {
        int totalDoubts = 0;
        int resolvedCount = 0;
        int pendingCount = 0;
        int rejectedCount = 0;
        int totalUpvotes = 0;

        java.util.List<Doubt> allDoubts = new java.util.ArrayList<>();
        for (String subject : subjects) {
            allDoubts.addAll(doubtsManager.getAllDoubtsForSubject(subject));
        }

        allDoubts.removeIf(d->!teacher.handlesDoubt(d));
        for (Doubt d : allDoubts) {
            totalDoubts++;
            totalUpvotes += d.getUpvoteCount();
            if ("Resolved".equalsIgnoreCase(d.getStatus())) resolvedCount++;
            else if ("Pending".equalsIgnoreCase(d.getStatus())) pendingCount++;
            else if ("Rejected".equalsIgnoreCase(d.getStatus())) rejectedCount++;
        }

        Map<String, Integer> pulseStats = doubtsManager.getLivePulseStats(teacher);
        int totalLivePulses = 0;
        for (int count : pulseStats.values()) {
            totalLivePulses += count;
        }

        List<Doubt> resolvedFaqList = doubtsManager.getUnpublishedResolved(subjects);
        resolvedFaqList.removeIf(d->!teacher.handlesDoubt(d));
        List<Doubt> publishedArchive = doubtsManager.getArchive();
        for (Doubt d : publishedArchive) {
            if (teacher.handlesDoubt(d)) {
                resolvedFaqList.add(d);
            }
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String subjectsStr = String.join(", ", subjects);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            .append("<meta charset=\"UTF-8\">\n")
            .append("<title>ClassPulse Lecture Summary & FAQ Report - ").append(escapeHtml(subjectsStr)).append("</title>\n")
            .append("<style>\n")
            .append("  body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0d1117; color: #c9d1d9; margin: 0; padding: 24px; }\n")
            .append("  .report-container { max-width: 900px; margin: 0 auto; background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 32px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }\n")
            .append("  .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #30363d; padding-bottom: 16px; margin-bottom: 24px; }\n")
            .append("  .header h1 { color: #58a6ff; margin: 0; font-size: 1.8rem; }\n")
            .append("  .header .meta { font-size: 0.9rem; color: #8b949e; text-align: right; }\n")
            .append("  .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 32px; }\n")
            .append("  .kpi-card { background: #21262d; border: 1px solid #30363d; border-radius: 6px; padding: 16px; text-align: center; }\n")
            .append("  .kpi-card .number { font-size: 2rem; font-weight: bold; color: #7ee787; margin-top: 4px; }\n")
            .append("  .kpi-card .label { font-size: 0.85rem; color: #8b949e; text-transform: uppercase; letter-spacing: 0.5px; }\n")
            .append("  h2 { color: #f0883e; border-bottom: 1px solid #30363d; padding-bottom: 8px; margin-top: 32px; }\n")
            .append("  table { width: 100%; border-collapse: collapse; margin-top: 12px; }\n")
            .append("  th, td { border: 1px solid #30363d; padding: 10px 12px; text-align: left; font-size: 0.9rem; }\n")
            .append("  th { background: #21262d; color: #58a6ff; }\n")
            .append("  tr:nth-child(even) { background: rgba(255,255,255,0.02); }\n")
            .append("  .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: bold; }\n")
            .append("  .badge-resolved { background: rgba(126,231,135,0.2); color: #7ee787; }\n")
            .append("  .badge-pending { background: rgba(210,153,34,0.2); color: #d29922; }\n")
            .append("  .badge-urgent { background: rgba(255,123,114,0.2); color: #ff7b72; }\n")
            .append("  .print-btn { background: #238636; color: #fff; border: none; padding: 10px 20px; font-size: 1rem; border-radius: 6px; cursor: pointer; float: right; font-weight: bold; }\n")
            .append("  .print-btn:hover { background: #2ea043; }\n")
            .append("  @media print {\n")
            .append("    body { background: #fff; color: #000; padding: 0; }\n")
            .append("    .report-container { border: none; box-shadow: none; padding: 0; background: #fff; width: 100%; max-width: 100%; }\n")
            .append("    .print-btn { display: none; }\n")
            .append("    h1, h2, th { color: #000 !important; }\n")
            .append("    .kpi-card { background: #f6f8fa; border: 1px solid #ccc; color: #000; }\n")
            .append("    .kpi-card .number { color: #000; }\n")
            .append("    th { background: #eee; }\n")
            .append("    td, th { border: 1px solid #999; color: #000; }\n")
            .append("  }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<div class=\"report-container\">\n")
            .append("  <button class=\"print-btn\" onclick=\"window.print()\">🖨️ Print / Save as PDF</button>\n")
            .append("  <div class=\"header\">\n")
            .append("    <div>\n")
            .append("      <h1>ClassPulse Lecture & FAQ Analytics Report</h1>\n")
            .append("      <div style=\"color:#8b949e; margin-top:4px;\">Faculty: ").append(escapeHtml(teacher.getName())).append(" (").append(escapeHtml(teacher.getTeacherId())).append(")</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"meta\">\n")
            .append("      <div><strong>Subjects:</strong> ").append(escapeHtml(subjectsStr)).append("</div>\n")
            .append("      <div><strong>Generated:</strong> ").append(timestamp).append("</div>\n")
            .append("    </div>\n")
            .append("  </div>\n\n")
            .append("  <div class=\"kpi-grid\">\n")
            .append("    <div class=\"kpi-card\"><div class=\"label\">Total Doubts</div><div class=\"number\">").append(totalDoubts).append("</div></div>\n")
            .append("    <div class=\"kpi-card\"><div class=\"label\">Resolved Doubts</div><div class=\"number\">").append(resolvedCount).append("</div></div>\n")
            .append("    <div class=\"kpi-card\"><div class=\"label\">Student Upvotes</div><div class=\"number\">👍 ").append(totalUpvotes).append("</div></div>\n")
            .append("    <div class=\"kpi-card\"><div class=\"label\">Live Confusion Pulses</div><div class=\"number\">⚡ ").append(totalLivePulses).append("</div></div>\n")
            .append("  </div>\n\n")
            .append("  <p>Pending: ").append(pendingCount).append(" · Rejected: ").append(rejectedCount).append("</p>\n")
            .append("  <h2>Subject Breakdown & Live Signals (Past 30 Mins)</h2>\n")
            .append("  <table>\n")
            .append("    <thead><tr><th>Subject</th><th>Live Confusion Signals</th><th>Status</th></tr></thead>\n")
            .append("    <tbody>\n");

        for (String sub : subjects) {
            int pulses = pulseStats.getOrDefault(sub, 0);
            String statusBadge = pulses >= 5 ? "<span class=\"badge badge-urgent\">🔴 HIGH CONFUSION</span>" :
                                (pulses >= 2 ? "<span class=\"badge badge-pending\">🟡 Moderate</span>" :
                                               "<span class=\"badge badge-resolved\">🟢 Normal</span>");
            html.append("      <tr><td><strong>").append(escapeHtml(sub)).append("</strong></td><td>⚡ ").append(pulses).append(" signal(s)</td><td>").append(statusBadge).append("</td></tr>\n");
        }

        html.append("    </tbody>\n  </table>\n\n")
            .append("  <h2>Resolved Doubts & Class FAQs Log</h2>\n");

        if (resolvedFaqList.isEmpty()) {
            html.append("  <p style=\"color:#8b949e;\">No resolved doubts recorded yet.</p>\n");
        } else {
            html.append("  <table>\n")
                .append("    <thead><tr><th>Doubt ID</th><th>Subject</th><th>Question</th><th>Teacher Resolution</th><th>Upvotes</th></tr></thead>\n")
                .append("    <tbody>\n");
            for (Doubt d : resolvedFaqList) {
                html.append("      <tr>\n")
                    .append("        <td><code>").append(escapeHtml(d.getDoubtId())).append("</code></td>\n")
                    .append("        <td>").append(escapeHtml(d.getSubject())).append("</td>\n")
                    .append("        <td>").append(escapeHtml(d.getDescription())).append("</td>\n")
                    .append("        <td>").append(escapeHtml(d.getTeacherResponse())).append("</td>\n")
                    .append("        <td>👍 ").append(d.getUpvoteCount()).append("</td>\n")
                    .append("      </tr>\n");
            }
            html.append("    </tbody>\n  </table>\n");
        }

        html.append("</div>\n</body>\n</html>");
        return html.toString();
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}

