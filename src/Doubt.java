public class Doubt {
    private String doubtId;
    private String subject;
    private String section;
    private String rollNumber;
    private String description;
    private String status;
    private String postedTime;
    private String priority;
    private String teacherResponse;
    private String rejectionReason;
    private String responseFileName;
    private int upvoteCount;

    public Doubt(String doubtId, String subject, String section, String rollNumber,
                 String description, String status, String postedTime, String priority,
                 String teacherResponse, String rejectionReason, String responseFileName) {
        this(doubtId, subject, section, rollNumber, description, status, postedTime, priority, teacherResponse, rejectionReason, responseFileName, 1);
    }

    public Doubt(String doubtId, String subject, String section, String rollNumber,
                 String description, String status, String postedTime, String priority,
                 String teacherResponse, String rejectionReason, String responseFileName, int upvoteCount) {
        this.doubtId = doubtId;
        this.subject = subject;
        this.section = section;
        this.rollNumber = rollNumber;
        this.description = description;
        this.status = status;
        this.postedTime = postedTime;
        this.priority = priority;
        this.teacherResponse = teacherResponse == null ? "" : teacherResponse;
        this.rejectionReason = rejectionReason == null ? "" : rejectionReason;
        this.responseFileName = responseFileName == null ? "" : responseFileName;
        this.upvoteCount = upvoteCount < 1 ? 1 : upvoteCount;
    }

    public String getDoubtId() { return doubtId; }
    public String getSubject() { return subject; }
    public String getSection() { return section; }
    public String getRollNumber() { return rollNumber; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPostedTime() { return postedTime; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getTeacherResponse() { return teacherResponse; }
    public void setTeacherResponse(String teacherResponse) { this.teacherResponse = teacherResponse; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getResponseFileName() { return responseFileName; }
    public void setResponseFileName(String responseFileName) { this.responseFileName = responseFileName; }
    public int getUpvoteCount() { return upvoteCount; }
    public void setUpvoteCount(int upvoteCount) { this.upvoteCount = upvoteCount; }

    public String toJson() {
        return "{" +
                JsonUtil.field("doubtId", doubtId) + "," +
                JsonUtil.field("subject", subject) + "," +
                JsonUtil.field("rollNumber", rollNumber) + "," +
                JsonUtil.field("description", description) + "," +
                JsonUtil.field("status", status) + "," +
                JsonUtil.field("postedTime", postedTime) + "," +
                JsonUtil.field("priority", priority) + "," +
                JsonUtil.field("teacherResponse", teacherResponse) + "," +
                JsonUtil.field("rejectionReason", rejectionReason) + "," +
                JsonUtil.field("responseFileName", responseFileName) + "," +
                "\"upvoteCount\":" + upvoteCount +
                "}";
    }

    public String toTeacherJson() {
        return "{" +
                JsonUtil.field("doubtId", doubtId) + "," +
                JsonUtil.field("subject", subject) + "," +
                JsonUtil.field("description", description) + "," +
                JsonUtil.field("status", status) + "," +
                JsonUtil.field("postedTime", postedTime) + "," +
                JsonUtil.field("priority", priority) + "," +
                "\"upvoteCount\":" + upvoteCount +
                "}";
    }

    public String toArchiveJson() {
        return "{" +
                JsonUtil.field("doubtId", doubtId) + "," +
                JsonUtil.field("subject", subject) + "," +
                JsonUtil.field("description", description) + "," +
                JsonUtil.field("teacherResponse", teacherResponse) + "," +
                JsonUtil.field("responseFileName", responseFileName) + "," +
                "\"upvoteCount\":" + upvoteCount +
                "}";
    }
}
