package gamerguy11.sixtoolsaddon.chat;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.System;

final class DmCrypto {
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final String RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
   private static final String AES = "AES/GCM/NoPadding";
   private static final int IV_LENGTH = 12;

   private final PrivateKey privateKey;
   private final String publicKey;

   DmCrypto() {
      try {
         KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
         generator.initialize(2048);
         KeyPair pair = generator.generateKeyPair();
         this.privateKey = pair.getPrivate();
         this.publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
      } catch (GeneralSecurityException error) {
         throw new IllegalStateException(error);
      }
   }

   String publicKey() {
      return this.publicKey;
   }

   static PublicKey parse(String encoded) throws GeneralSecurityException {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
   }

   String encrypt(PublicKey target, String text) throws GeneralSecurityException {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);
      SecretKey key = generator.generateKey();

      Cipher rsa = Cipher.getInstance(RSA);
      rsa.init(Cipher.ENCRYPT_MODE, target);
      byte[] wrapped = rsa.doFinal(key.getEncoded());

      byte[] iv = new byte[IV_LENGTH];
      RANDOM.nextBytes(iv);
      Cipher aes = Cipher.getInstance(AES);
      aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] body = aes.doFinal(text.getBytes(StandardCharsets.UTF_8));

      byte[] envelope = new byte[2 + wrapped.length + IV_LENGTH + body.length];
      envelope[0] = (byte) (wrapped.length >>> 8);
      envelope[1] = (byte) wrapped.length;
      System.arraycopy(wrapped, 0, envelope, 2, wrapped.length);
      System.arraycopy(iv, 0, envelope, 2 + wrapped.length, IV_LENGTH);
      System.arraycopy(body, 0, envelope, 2 + wrapped.length + IV_LENGTH, body.length);
      return Base64.getEncoder().encodeToString(envelope);
   }

   String decrypt(String encoded) throws GeneralSecurityException {
      byte[] envelope = Base64.getDecoder().decode(encoded);
      if (envelope.length < 2 + IV_LENGTH + 16) {
         throw new GeneralSecurityException("short envelope");
      }

      int wrappedLength = ((envelope[0] & 0xFF) << 8) | (envelope[1] & 0xFF);
      if (wrappedLength == 0 || 2 + wrappedLength + IV_LENGTH + 16 > envelope.length) {
         throw new GeneralSecurityException("bad envelope");
      }

      byte[] wrapped = Arrays.copyOfRange(envelope, 2, 2 + wrappedLength);
      byte[] iv = Arrays.copyOfRange(envelope, 2 + wrappedLength, 2 + wrappedLength + IV_LENGTH);
      byte[] body = Arrays.copyOfRange(envelope, 2 + wrappedLength + IV_LENGTH, envelope.length);

      Cipher rsa = Cipher.getInstance(RSA);
      rsa.init(Cipher.DECRYPT_MODE, this.privateKey);
      SecretKeySpec key = new SecretKeySpec(rsa.doFinal(wrapped), "AES");

      Cipher aes = Cipher.getInstance(AES);
      aes.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
      return new String(aes.doFinal(body), StandardCharsets.UTF_8);
   }
}
