package crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

// Based on:
//   - https://medium.com/lumenauts/sending-secret-and-anonymous-memos-with-stellar-8914479e949b
//   - https://github.com/travisdazell/AES-CTR-BOUNCYCASTLE/blob/master/AES%20CTR%20Example/src/net/travisdazell/crypto/aes/example/AesCtrExample.scala
public class AesCtrCrypto {
    public static final int RANDOM_IV_LENGTH = 8;

    private static final char[] hexSymbols = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final SecureRandom secureRandom = new SecureRandom();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String encrypt(String hexKey, String message) {
        byte[] keyBytes = hexStringToByteArray(hexKey);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        // TODO: Move to separate function
        try {
            Cipher aes = Cipher.getInstance("AES/CTR/NoPadding", BouncyCastleProvider.PROVIDER_NAME);

            byte[] randomIvBytes = randomIv();
            IvParameterSpec ivParameterSpec = new IvParameterSpec(randomIvBytes);

            aes.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] messageBytes = message.getBytes();
            byte[] encryptedBytes = aes.doFinal(messageBytes);

            byte[] resultBytes = new byte[randomIvBytes.length + encryptedBytes.length];
            System.arraycopy(randomIvBytes, 0, resultBytes, 0, randomIvBytes.length);
            System.arraycopy(encryptedBytes, 0, resultBytes, randomIvBytes.length, encryptedBytes.length);

            return bytesToHex(resultBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        // TODO
        return null;
    }

    public static String decrypt(String hexKey, String hexCipher) {
        byte[] keyBytes = hexStringToByteArray(hexKey);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        try {
            Cipher aes = Cipher.getInstance("AES/CTR/NoPadding", BouncyCastleProvider.PROVIDER_NAME);

            String hexRandomIv = hexCipher.substring(0, 16);
            byte[] randomIvBytes = hexStringToByteArray(hexRandomIv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(randomIvBytes);

            aes.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            String hexEncryptedMessage = hexCipher.substring(16);
            byte[] encryptedMessageBytes = hexStringToByteArray(hexEncryptedMessage);

            byte[] decrpytedBytes = aes.doFinal(encryptedMessageBytes);
            return new String(decrpytedBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        // TODO
        return null;
    }

    private static byte[] hexStringToByteArray(String s) {
        int length = s.length();
        byte[] result = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            int topDigit = Character.digit(s.charAt(i), 16);
            int bottomDigit = Character.digit(s.charAt(i + 1), 16);
            int combined = (topDigit << 4) + bottomDigit;
            result[i / 2] = (byte) combined;
        }

        return result;
    }

    private static byte[] randomIv() {
        byte[] result = new byte[RANDOM_IV_LENGTH];
        secureRandom.nextBytes(result);
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int byteAsInt = bytes[i] & 0xFF;
            hexChars[i * 2] = hexSymbols[byteAsInt >>> 4];
            hexChars[i * 2 + 1] = hexSymbols[byteAsInt & 0x0F];
        }

        return new String(hexChars);
    }

    public static void main(String[] args) {
        System.out.println(encrypt("12345678123456781234567812345678", "1"));
        System.out.println(encrypt("12345678123456781234567812345678", "1"));
        System.out.println(encrypt("12345678123456781234567812345678", "1"));
        System.out.println(encrypt("12345678123456781234567812345678", "1"));

        String secretMessageHex = encrypt("42844221428442214284422142844221", "Hello World!@#()");
        System.out.println("Secret hex message: " + secretMessageHex);
        String decryptedMessage = decrypt("42844221428442214284422142844221", secretMessageHex);
        System.out.println("decryptedMessage: " + decryptedMessage);
    }
}
