package gamerguy11.sixtoolsaddon.utils;

import java.util.regex.Pattern;

public final class ChatNames {
   private static final String SEPARATORS = "\u00bb\u203a:>";
   private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]");
   private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]+");

   private ChatNames() {
   }

   public static int headerEnd(String text) {
      for (int i = 1; i < text.length(); i++) {
         if (SEPARATORS.indexOf(text.charAt(i)) >= 0) {
            return i + 1;
         }
      }

      return text.length();
   }

   public static String singleName(String raw) {
      if (raw == null) {
         return null;
      }

      java.util.regex.Matcher matcher = NAME_TOKEN.matcher(BRACKETS.matcher(raw).replaceAll(" "));
      String found = null;
      while (matcher.find()) {
         if (found != null) {
            return null;
         }

         found = matcher.group();
      }

      return found != null && found.length() >= 2 && found.length() <= 16 ? found : null;
   }

   public static boolean startsWithSender(String text, String username) {
      if (username == null || username.isEmpty()) {
         return false;
      }

      Pattern pattern = Pattern.compile(
         "^(?:\\[[^\\]]*\\]|[^A-Za-z0-9_])*" + Pattern.quote(username) + "\\s*[\u00bb\u203a:]",
         Pattern.CASE_INSENSITIVE);
      return pattern.matcher(text).find();
   }
}
