package br.edu.utfpr.sdcontas.servidor;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Senhas {
    private static final String PREFIXO = "pbkdf2-sha256";
    private static final int ITERACOES = 65_536;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    static final String HASH_FICTICIO = gerarHash("Senha-ficticia#1");

    private Senhas() {
    }

    static String gerarHash(String senha) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = pbkdf2(senha, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXO + "$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    static boolean confere(String senha, String armazenado) {
        if (senha == null || armazenado == null) {
            return false;
        }
        String[] partes = armazenado.split("\\$");
        if (partes.length != 4 || !PREFIXO.equals(partes[0])) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = pbkdf2(senha, sal, iteracoes);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException formatoInvalido) {
            return false;
        }
    }

    private static byte[] pbkdf2(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível nesta JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
