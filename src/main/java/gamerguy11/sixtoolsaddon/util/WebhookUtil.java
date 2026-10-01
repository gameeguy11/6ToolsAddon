package gamerguy11.sixtoolsaddon.util;

import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownServiceException;
import javax.net.ssl.HttpsURLConnection;

public class WebhookUtil {
   public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
      String json = "{\"embeds\": [{\"title\": \"" + title + "\",\"description\": \"" + message + "\",\"color\": 15258703,\"footer\": {\"text\": \"From: " + playerName + "\"}}]}";
      sendRequest(webhookURL, json);
      if (pingID != null) {
         sendRequest(webhookURL, "{\"content\": \"<@" + pingID + ">\"}");
      }

   }

   public static void sendWebhook(String webhookURL, String jsonObject, String pingID) {
      sendRequest(webhookURL, jsonObject);
      if (pingID != null) {
         sendRequest(webhookURL, "{\"content\": \"<@" + pingID + ">\"}");
      }

   }

   private static void sendRequest(String webhookURL, String json) {
      try {
         URL url = URI.create(webhookURL).toURL();
         HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
         con.addRequestProperty("Content-Type", "application/json");
         con.addRequestProperty("User-Agent", "Mozilla");
         con.setDoOutput(true);
         con.setRequestMethod("POST");
         OutputStream stream = con.getOutputStream();

         try {
            stream.write(json.getBytes());
            stream.flush();
         } catch (Throwable var8) {
            if (stream != null) {
               try {
                  stream.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (stream != null) {
            stream.close();
         }

         con.getInputStream().close();
         con.disconnect();
      } catch (UnknownServiceException | MalformedURLException var9) {
      } catch (Exception e) {
         e.printStackTrace();
      }

   }
}
