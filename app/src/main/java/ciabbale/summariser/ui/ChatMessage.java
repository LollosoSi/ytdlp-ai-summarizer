package ciabbale.summariser.ui;

public class ChatMessage {
    public String text;
    public boolean isUser;

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
    }
}