package ma.emsi.zougagh.TP4Web_ZougaghMasters.Models;

public class ChatMessage implements java.io.Serializable {
    private String author;
    private String text;
    // Constructeur
    public ChatMessage(String author, String text) {
        this.author = author;
        this.text = text;
    }
    // Getters
    public String getAuthor() { return author; }
    public String getText() { return text; }
}