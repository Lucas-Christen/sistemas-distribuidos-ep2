package lucaschristen.sistemasdistribuidos.protocolo;

import java.util.Locale;

public final class Validacao {
    private Validacao() {
    }

    public static final int NOME_MIN = 1;
    public static final int NOME_MAX = 60;
    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 20;
    public static final int SENHA_MIN = 8;
    public static final int SENHA_MAX = 20;
    public static final String ESPECIAIS_SENHA = "#.*&%$@!()-_=+";

    public static final String REGRA_NOME =
            "Nome: só letras e espaços, de 1 a 60 caracteres.";
    public static final String REGRA_USERNAME =
            "Username: 3 a 20 caracteres, só letras minúsculas, números, '.' e '_' (sem acento e sem espaço).";
    public static final String REGRA_SENHA =
            "Senha: 8 a 20 caracteres, com pelo menos 1 maiúscula, 1 minúscula, 1 número e 1 símbolo entre "
                    + "# . * & % $ @ ! ( ) - _ = +";

    public static boolean nomeValido(String nome) {
        if (nome == null) {
            return false;
        }
        int tamanho = nome.codePointCount(0, nome.length());
        if (tamanho < NOME_MIN || tamanho > NOME_MAX) {
            return false;
        }
        boolean temLetra = false;
        for (int i = 0; i < nome.length(); ) {
            int caractere = nome.codePointAt(i);
            if (Character.isLetter(caractere)) {
                temLetra = true;
            } else if (caractere != ' ') {
                return false;
            }
            i += Character.charCount(caractere);
        }
        return temLetra;
    }

    public static boolean usernameValido(String username) {
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            return false;
        }
        for (char c : username.toCharArray()) {
            boolean permitido = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_';
            if (!permitido) {
                return false;
            }
        }
        return true;
    }

    public static boolean senhaValida(String senha) {
        if (senha == null || senha.length() < SENHA_MIN || senha.length() > SENHA_MAX) {
            return false;
        }
        boolean maiuscula = false;
        boolean minuscula = false;
        boolean numero = false;
        boolean simbolo = false;
        for (char c : senha.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                maiuscula = true;
            } else if (c >= 'a' && c <= 'z') {
                minuscula = true;
            } else if (c >= '0' && c <= '9') {
                numero = true;
            } else if (ESPECIAIS_SENHA.indexOf(c) >= 0) {
                simbolo = true;
            } else {
                return false;
            }
        }
        return maiuscula && minuscula && numero && simbolo;
    }

    public static final int PLACA_TAMANHO = 7;
    public static final int VAGA_MIN = 1;
    public static final int VAGA_MAX = 4;
    public static final int MARCA_MAX = 20;
    public static final int MODELO_MAX = 20;

    public static final String REGRA_PLACA = "Placa: exatamente 7 letras ou números (ex.: ABC1234 ou ABC1D23).";
    public static final String REGRA_VAGA = "Vaga: 1 a 4 letras ou números (ex.: A1, B12).";
    public static final String REGRA_MARCA = "Marca: obrigatória, de 1 a 20 caracteres.";
    public static final String REGRA_MODELO = "Modelo: opcional, até 20 caracteres.";

    public static boolean roleValida(String role) {
        return Protocolo.ROLE_ADMIN.equals(role) || Protocolo.ROLE_USER.equals(role);
    }

    public static boolean tipoValido(String tipo) {
        return Protocolo.TIPO_ELETRICO.equals(tipo) || Protocolo.TIPO_COMBUSTAO.equals(tipo);
    }

    public static boolean statusVagaValido(String status) {
        return Protocolo.VAGA_DISPONIVEL.equals(status) || Protocolo.VAGA_OCUPADA.equals(status)
                || Protocolo.VAGA_FORA_DE_SERVICO.equals(status);
    }

    public static String normalizarPlaca(String placa) {
        if (placa == null) {
            return null;
        }
        String limpa = placa.strip().toUpperCase(Locale.ROOT);
        if (limpa.length() != PLACA_TAMANHO) {
            return null;
        }
        for (char c : limpa.toCharArray()) {
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return null;
            }
        }
        return limpa;
    }

    public static boolean placaValida(String placa) {
        return normalizarPlaca(placa) != null;
    }

    public static boolean nomeVagaValido(String nome) {
        if (nome == null || nome.length() < VAGA_MIN || nome.length() > VAGA_MAX) {
            return false;
        }
        for (char c : nome.toCharArray()) {
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    public static boolean marcaValida(String marca) {
        return textoLivreValido(marca, MARCA_MAX);
    }

    public static boolean modeloValido(String modelo) {
        return modelo == null || textoLivreValido(modelo, MODELO_MAX);
    }

    private static boolean textoLivreValido(String texto, int max) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        if (texto.codePointCount(0, texto.length()) > max) {
            return false;
        }
        return texto.codePoints().noneMatch(Character::isISOControl);
    }
}
