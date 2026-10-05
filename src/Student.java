public class Student {
    private String rollNumber;
    private String name;
    private String section;

    public Student(String rollNumber, String name, String section) {
        this.rollNumber = rollNumber;
        this.name = name;
        this.section = section;
    }

    public String getRollNumber() { return rollNumber; }
    public String getName() { return name; }
    public String getSection() { return section; }

    public String toJson() {
        return "{" +
                JsonUtil.field("rollNumber", rollNumber) + "," +
                JsonUtil.field("name", name) + "," +
                JsonUtil.field("section", section) +
                "}";
    }
}
