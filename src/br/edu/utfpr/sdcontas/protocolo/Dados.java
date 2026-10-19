package br.edu.utfpr.sdcontas.protocolo;

import java.util.Map;

public final class Dados {
    private Dados() {
    }

    public static boolean temCampo(Map<String, ?> objeto, String nome) {
        return chaveReal(objeto, nome) != null;
    }

    public static Object campo(Map<String, ?> objeto, String nome) {
        String chave = chaveReal(objeto, nome);
        return chave == null ? null : objeto.get(chave);
    }

    public static String texto(Map<String, ?> objeto, String nome) {
        Object valor = campo(objeto, nome);
        return valor instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> comoObjeto(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            return (Map<String, Object>) mapa;
        }
        if (valor instanceof String s && s.strip().startsWith("{")) {
            try {
                Object interno = Json.ler(s.strip());
                if (interno instanceof Map<?, ?> mapa) {
                    return (Map<String, Object>) mapa;
                }
            } catch (JsonInvalidoException ignorada) {
            }
        }
        return null;
    }

    private static String chaveReal(Map<String, ?> objeto, String nome) {
        if (objeto == null) {
            return null;
        }
        if (objeto.containsKey(nome)) {
            return nome;
        }
        for (String chave : objeto.keySet()) {
            if (chave != null && chave.equalsIgnoreCase(nome)) {
                return chave;
            }
        }

        String procurado = soLetrasEDigitos(nome);
        for (String chave : objeto.keySet()) {
            if (chave != null && soLetrasEDigitos(chave).equals(procurado)) {
                return chave;
            }
        }
        return null;
    }

    private static String soLetrasEDigitos(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
