import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local, conservative English relevance rules; independent of the optional toxicity model. */
public final class AcademicPolicy {
    public static final String NOTICE = "ACADEMIC USE ONLY: ClassPulse accepts specific questions about studies, college services, admissions, exams, projects, internships and careers, plus relevant academic answers. Personal chat, romantic messages, weekend small talk, harassment and promotions are not allowed. Selecting a subject does not make an unrelated message acceptable.";
    public static final String QUESTION_ERROR = "Message blocked. Ask a specific academic, college or career question. Personal chat (such as 'I love u', 'I like u' or 'how was your weekend') is not allowed. Selecting a subject or Post Anyway cannot bypass this rule.";
    public static final String ANSWER_ERROR = "Message blocked. Answers must help with the academic question. Personal chat, romantic messages, small talk and promotions are not allowed.";
    private static final Pattern SOCIAL = pattern(
        "\\b(?:i|we) (?:really |very much )?(?:love|luv|like|adore|miss|want) (?:u|you|ya)\\b|" +
        "\\b(?:love|luv|miss) (?:u|you)\\b|\\b(?:do|will|would|can) (?:u|you) (?:love|like|date|marry|kiss) me\\b|" +
        "\\b(?:how (?:was|is)|hwo (?:was|is)|how s|hows) (?:your|ur|the) (?:college )?(?:weekend|day|vacation|holiday|dinner|lunch|evening|date)\\b|" +
        "\\b(?:i have a crush on (?:you|u)|(?:you|u) (?:are|look) (?:cute|beautiful|sexy)|(?:love|luv|like) (?:you|u) so much)\\b|" +
        "\\b(?:are (?:you|u) single|be my (?:girlfriend|boyfriend)|send (?:me )?(?:your )?(?:selfie|nudes)|let s (?:date|party|hang out))\\b|" +
        "\\b(?:what|which) (?:movie|film) should (?:we|i) watch\\b|\\b(?:your|ur) (?:favorite|favourite) (?:food|movie|color|colour)\\b|" +
        "\\b(?:subscribe to my|follow me on|buy now|click (?:on )?(?:this|my) link|win cash|free money)\\b");
    private static final Pattern TOPIC = pattern("\\b(?:" +
        "academic\\w*|stud(?:y|ies|ying)|college\\w*|universit\\w*|campus|school|education\\w*|course\\w*|subject\\w*|syllabus|curriculum|semester\\w*|lecture\\w*|class(?:es|room)?|teacher\\w*|professor\\w*|faculty|department\\w*|" +
        "exam\\w*|test(?:s|ing)?|quiz(?:zes)?|assignment\\w*|homework|project\\w*|research\\w*|thesis|dissertation|lab(?:s|oratory)?|practical\\w*|marks|grades?|results?|credits?|backlogs?|attendance|timetable|schedule|notes|textbook\\w*|" +
        "admission\\w*|enrollment|enrolment|eligibility|degree\\w*|diploma\\w*|scholarship\\w*|fees?|tuition|hostel\\w*|library|libraries|counselling|counseling|placement\\w*|career\\w*|internship\\w*|interview\\w*|resume\\w*|cv|job\\w*|recruitment|employment|aptitude|certification\\w*|" +
        "java|python|program\\w*|coding|code|algorithm\\w*|data|database\\w*|sql|computer\\w*|architecture|software|hardware|operating system|network\\w*|security|file|upload|" +
        "machine learning|ml|daa|artificial intelligence|ai|gradient\\w*|descent|regression|weights?|recursion|recursive|loops?|arrays?|lists?|queues?|stacks?|trees?|graphs?|sort\\w*|search\\w*|complexity|quicksort|bellman|ford|cycles?|threads?|jvm|heap|garbage|collection|process\\w*|dist|iterations?|memoization|dynamic|binary|logarithm\\w*|" +
        "math\\w*|calculus|algebra|probability|statistics|discrete|geometry|physics|chemistry|biology|economics|finance|accounting|engineering|circuit\\w*|electronics|thermodynamics|literature|history|psychology|sociology|law|medicine|anatomy|grammar|language|communication skills" +
        ")\\b");
    private static final Pattern QUESTION = pattern("\\b(?:what|why|how|when|where|which|who|explain|clarify|compare|difference|define|meaning|help|understand|confus\\w*|difficult|struggling|need|can|could|should|does|do|is|are|will|would|may|apply|prepare|solve|find|calculate|derive|prove|suggest|recommend|guide|requirement\\w*|eligibility)\\b");
    private static final Pattern CONTEXTUAL_ANSWER = pattern("^(?:yes|no|correct|incorrect|check (?:the|your) (?:notes|textbook|syllabus)|use memoization|needs clarification|please (?:clarify|provide more details))$");
    private static Pattern pattern(String regex) { return Pattern.compile(regex); }
    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replace("l0ve", "love").replace("1ove", "love").replace("1ike", "like")
            .replaceAll("\\p{Cf}", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    public static String questionError(String text) {
        String clean=normalize(text);
        if (SOCIAL.matcher(clean).find()) return QUESTION_ERROR;
        if (clean.split(" ").length<2 || !TOPIC.matcher(clean).find()) return QUESTION_ERROR;
        if (!(text.contains("?") || QUESTION.matcher(clean).find())) return QUESTION_ERROR;
        return null;
    }
    public static String answerError(String text, String question) {
        String clean=normalize(text);
        if (SOCIAL.matcher(clean).find()) return ANSWER_ERROR;
        if (TOPIC.matcher(clean).find()) return null;
        if (questionError(question)==null && CONTEXTUAL_ANSWER.matcher(clean).matches()) return null;
        return ANSWER_ERROR;
    }
}
