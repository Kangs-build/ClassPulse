import java.util.List;

public class Teacher {
    private String teacherId;
    private String name;
    private List<String> subjectsHandled;
    private List<String> assignedSections = List.of();

    public Teacher(String teacherId, String name, List<String> subjectsHandled) {
        this.teacherId = teacherId;
        this.name = name;
        this.subjectsHandled = subjectsHandled;
    }

    public Teacher(String id, String name, List<String> subjects, List<String> sections) {
        this(id,name,subjects); this.assignedSections=List.copyOf(sections);
    }
    public List<String> getAssignedSections() {return assignedSections;}
    public boolean handlesClass(String subject,String section) {return handlesSubject(subject) && assignedSections.stream().anyMatch(s->s.equalsIgnoreCase(section));}
    public boolean handlesDoubt(Doubt d) {return handlesClass(d.getSubject(),d.getSection());}
    public String getTeacherId() { return teacherId; }
    public String getName() { return name; }
    public List<String> getSubjectsHandled() { return subjectsHandled; }

    public boolean handlesSubject(String subject) {
        for (String s : subjectsHandled) {
            if (s.equalsIgnoreCase(subject)) return true;
        }
        return false;
    }

    public String toJson() {
        StringBuilder subjectsArr = new StringBuilder("[");
        for (int i = 0; i < subjectsHandled.size(); i++) {
            if (i > 0) subjectsArr.append(",");
            subjectsArr.append("\"").append(JsonUtil.escape(subjectsHandled.get(i))).append("\"");
        }
        subjectsArr.append("]");

        return "{" +
                JsonUtil.field("teacherId", teacherId) + "," +
                JsonUtil.field("name", name) + "," +
                "\"assignedSections\":" + assignedSections.stream().map(s->"\""+JsonUtil.escape(s)+"\"").collect(java.util.stream.Collectors.joining(",","[","]")) + "," +
                "\"subjectsHandled\":" + subjectsArr +
                "}";
    }
}
