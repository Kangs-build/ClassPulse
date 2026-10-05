import java.util.HashMap;
import java.util.Map;
public class SimpleJsonParser {
    private final String s;
    private int i;
    private SimpleJsonParser(String s) { this.s = s; }
    public static Map<String,String> parseFlatObject(String json) {
        if (json == null) throw new IllegalArgumentException("JSON body required");
        return new SimpleJsonParser(json).object();
    }
    private void ws() { while (i<s.length() && " \n\r\t".indexOf(s.charAt(i))>=0) i++; }
    private boolean take(char c) { ws(); if (i<s.length() && s.charAt(i)==c) {i++; return true;} return false; }
    private void need(char c) { if (!take(c)) throw new IllegalArgumentException("Invalid JSON: expected " + c); }
    private Map<String,String> object() {
        Map<String,String> m = new HashMap<>(); need('{');
        if (!take('}')) {
            do {
                ws(); String key=string(); need(':'); ws();
                String value;
                if (i<s.length() && s.charAt(i)=='"') value=string();
                else {
                    int start=i;
                    while(i<s.length() && ",} \n\r\t".indexOf(s.charAt(i))<0) i++;
                    value=s.substring(start,i);
                    if (value.equals("null")) value=null;
                    else if (!value.equals("true") && !value.equals("false") && !value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON scalar");
                }
                if(m.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key");
                m.put(key,value);
            } while(take(','));
            need('}');
        }
        ws(); if(i!=s.length()) throw new IllegalArgumentException("Trailing JSON data"); return m;
    }
    private String string() {
        need('"'); StringBuilder b=new StringBuilder();
        while(i<s.length()) {
            char c=s.charAt(i++); if(c=='"') return b.toString();
            if(c<32) throw new IllegalArgumentException("Unescaped control character");
            if(c!='\\') {b.append(c); continue;}
            if(i>=s.length()) break;
            c=s.charAt(i++);
            switch(c) {
                case '"', '\\', '/' -> b.append(c);
                case 'n' -> b.append('\n'); case 'r' -> b.append('\r'); case 't' -> b.append('\t');
                case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                case 'u' -> {
                    if(i+4>s.length()) throw new IllegalArgumentException("Invalid Unicode escape");
                    try { b.append((char)Integer.parseInt(s.substring(i,i+4),16)); }
                    catch(NumberFormatException e) {throw new IllegalArgumentException("Invalid Unicode escape");} i+=4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape");
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }
}
