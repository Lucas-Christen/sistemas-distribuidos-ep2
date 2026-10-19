package lucaschristen.sistemasdistribuidos.protocolo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class Transporte {
    private Transporte() {
    }

    public static final int TAMANHO_MAXIMO = 64 * 1024;
    public static final int ESPERA_APOS_QUEBRA_MS = 300;
    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public record MensagemRecebida(String texto, Charset charset) {
    }

    public static final class MensagemGrandeException extends IOException {
        private static final long serialVersionUID = 1L;

        public MensagemGrandeException() {
            super("mensagem maior que " + TAMANHO_MAXIMO + " bytes");
        }
    }

    public static final class MensagemIncompletaException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String parcial;

        public MensagemIncompletaException(String parcial) {
            super("mensagem incompleta");
            this.parcial = parcial;
        }

        public String getParcial() {
            return parcial;
        }
    }

    public static void escreverMensagem(OutputStream saida, String json, Charset charset) throws IOException {
        saida.write((json + "\n").getBytes(charset));
        saida.flush();
    }

    public static MensagemRecebida lerMensagem(InputStream entrada, Socket socket, long prazoTotalMs)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long limite = prazoTotalMs > 0 ? System.nanoTime() + prazoTotalMs * 1_000_000L : 0;
        int nivel = 0;
        boolean dentroDeTexto = false;
        boolean escapando = false;
        boolean comecou = false;
        try {
            while (true) {
                aplicarPrazo(entrada, socket, limite);
                int b = entrada.read();
                if (b == -1) {
                    break;
                }
                if (!comecou) {
                    if (b == '\n') {
                        return new MensagemRecebida("", StandardCharsets.UTF_8);
                    }
                    if (b == ' ' || b == '\t' || b == '\r') {
                        continue;
                    }
                    comecou = true;
                }
                if (bytes.size() >= TAMANHO_MAXIMO) {
                    throw new MensagemGrandeException();
                }
                bytes.write(b);

                if (b == '\n') {
                    if (nivel <= 0 || silencioDepoisDaQuebra(entrada, socket)) {
                        break;
                    }
                    escapando = false;
                    continue;
                }
                if (dentroDeTexto) {
                    if (escapando) {
                        escapando = false;
                    } else if (b == '\\') {
                        escapando = true;
                    } else if (b == '"') {
                        dentroDeTexto = false;
                    }
                    continue;
                }
                if (b == '"') {
                    dentroDeTexto = true;
                } else if (b == '{' || b == '[') {
                    nivel++;
                } else if (b == '}' || b == ']') {
                    nivel--;
                    if (nivel <= 0) {
                        break;
                    }
                }
            }
        } catch (SocketTimeoutException tempoEsgotado) {
            if (comecou) {
                throw new MensagemIncompletaException(decodificar(bytes.toByteArray()).texto());
            }
            throw tempoEsgotado;
        }
        if (!comecou) {
            return null;
        }
        return decodificar(bytes.toByteArray());
    }

    private static boolean silencioDepoisDaQuebra(InputStream entrada, Socket socket) throws IOException {
        if (entrada.available() > 0 || socket == null || !entrada.markSupported()) {
            return false;
        }
        int anterior = socket.getSoTimeout();
        socket.setSoTimeout(ESPERA_APOS_QUEBRA_MS);
        entrada.mark(1);
        try {
            int proximo = entrada.read();
            entrada.reset();
            return proximo == -1;
        } catch (SocketTimeoutException silencio) {
            return true;
        } finally {
            socket.setSoTimeout(anterior);
        }
    }

    public static void descartarPendentes(InputStream entrada) {
        try {
            byte[] lixo = new byte[512];
            while (entrada.available() > 0 && entrada.read(lixo) > 0) {
            }
        } catch (IOException ignorada) {
        }
    }

    private static void aplicarPrazo(InputStream entrada, Socket socket, long limite) throws IOException {
        if (socket == null || limite == 0 || entrada.available() > 0) {
            return;
        }
        long restanteMs = (limite - System.nanoTime()) / 1_000_000L;
        if (restanteMs <= 0) {
            throw new SocketTimeoutException("prazo total esgotado");
        }
        int atual = socket.getSoTimeout();
        if (atual == 0 || atual > restanteMs) {
            socket.setSoTimeout((int) Math.max(1, restanteMs));
        }
    }

    static MensagemRecebida decodificar(byte[] bytes) {
        int inicio = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            inicio = 3;
        }
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, inicio, bytes.length - inicio))
                    .toString();
            return new MensagemRecebida(texto.strip(), StandardCharsets.UTF_8);
        } catch (CharacterCodingException naoEhUtf8) {
            String texto = new String(bytes, inicio, bytes.length - inicio, WINDOWS_1252);
            return new MensagemRecebida(texto.strip(), WINDOWS_1252);
        }
    }
}
