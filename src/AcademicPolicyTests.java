public class AcademicPolicyTests {
    public static void main(String[] args) {
        int passed=0;
        String[] blocked={"i love u","i like u","I LOVE YOU!","i.love.u","i\u200blove u", "I luv you", "How was your weekend?", "hwo was your weekend", "How is ur day?", "Hello", "How are you?", "I love u college", "Explain Java. I love you", "How was your weekend at college?", "Are you single?", "Will you marry me?", "Which movie should we watch?", "Subscribe to my college channel", "Buy now college", "Java", "I love Java", "random unrelated words", "Please tell me your favourite food"};
        for(String text:blocked) {if(AcademicPolicy.questionError(text)==null) throw new AssertionError("Allowed off-topic question: "+text); passed++;}
        String[] allowed={"How does Java garbage collection work?", "Hello professor, can you explain recursion?", "I don't understand gradient descent", "Explain threads", "What is the college admission deadline?", "How do I apply for a scholarship?", "When is the hostel fee due?", "Is the college library open this weekend?", "How should I prepare for campus placements?", "Which career can I choose after my degree?", "How can I find an internship?", "Can you review my resume?", "What is the eligibility for this course?", "Why does binary search take logarithmic time?", "How can I pursue a career in game development?", "Why does chemistry require balancing equations?", "Can we study love in psychology?", "How do I kill a process in Java?", "How should I plan my weekend study schedule?"};
        for(String text:allowed) {if(AcademicPolicy.questionError(text)!=null) throw new AssertionError("Blocked academic question: "+text);passed++;}
        String q="How does binary search work?";
        for(String text:new String[]{"i love u","How was your weekend?","college i like u","Have a nice vacation","Subscribe to my channel","random nonsense"}) {if(AcademicPolicy.answerError(text,q)==null) throw new AssertionError("Allowed social answer: "+text);passed++;}
        for(String text:new String[]{"Binary search halves the sorted search range each time.","Check the textbook","Yes","Use memoization"}) {if(AcademicPolicy.answerError(text,q)!=null) throw new AssertionError("Blocked academic answer: "+text);passed++;}
        System.out.println("ACADEMIC POLICY RESULTS: "+passed+" passed, 0 failed");
    }
}
