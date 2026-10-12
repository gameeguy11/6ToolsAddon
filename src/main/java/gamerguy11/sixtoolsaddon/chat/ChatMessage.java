package gamerguy11.sixtoolsaddon.chat;

public record ChatMessage(String sender, String text, long time, boolean self, boolean system) {
}
