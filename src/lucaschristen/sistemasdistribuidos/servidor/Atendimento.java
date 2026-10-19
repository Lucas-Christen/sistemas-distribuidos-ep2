package lucaschristen.sistemasdistribuidos.servidor;

import lucaschristen.sistemasdistribuidos.protocolo.LogSeguro;
import lucaschristen.sistemasdistribuidos.protocolo.Protocolo;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import lucaschristen.sistemasdistribuidos.protocolo.Transporte;
import lucaschristen.sistemasdistribuidos.protocolo.Transporte.MensagemGrandeException;
import lucaschristen.sistemasdistribuidos.protocolo.Transporte.MensagemIncompletaException;
import lucaschristen.sistemasdistribuidos.protocolo.Transporte.MensagemRecebida;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class Atendimento implements Runnable {
    private final Socket socket;
    private final Despachante despachante;
    private final RegistroLog log;
    private final int tempoLimiteMs;

    Atendimento(Socket socket, Despachante despachante, RegistroLog log, int tempoLimiteMs) {
        this.socket = socket;
        this.despachante = despachante;
        this.log = log;
        this.tempoLimiteMs = tempoLimiteMs;
    }

    @Override
    public void run() {
        String origem = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        log.info("Cliente conectado: " + origem);
        try (Socket s = socket) {
            s.setSoTimeout(tempoLimiteMs);
            InputStream entrada = new BufferedInputStream(s.getInputStream());
            OutputStream saida = new BufferedOutputStream(s.getOutputStream());

            Resposta resposta;
            Charset charset = StandardCharsets.UTF_8;
            try {
                MensagemRecebida mensagem = Transporte.lerMensagem(entrada, s, tempoLimiteMs);
                if (mensagem == null) {
                    log.info("Cliente " + origem + " fechou a conexão sem enviar nada");
                    return;
                }
                charset = mensagem.charset();
                String aviso = charset.equals(StandardCharsets.UTF_8) ? "" : "  (recebida em " + charset + ")";
                log.servidor("Requisição recebida: " + LogSeguro.mascararSenhas(mensagem.texto()) + aviso);
                resposta = despachante.processar(mensagem.texto());
            } catch (MensagemGrandeException e) {
                log.erro("Requisição de " + origem + " passou do limite de tamanho");
                resposta = Resposta.erro(Protocolo.REQUISICAO_INVALIDA, Protocolo.MSG_MENSAGEM_GRANDE);
            } catch (MensagemIncompletaException e) {
                log.servidor("Requisição incompleta (tempo esgotado): " + LogSeguro.mascararSenhas(e.getParcial()));
                resposta = Resposta.erro(Protocolo.REQUISICAO_INVALIDA, Protocolo.MSG_JSON_MALFORMADO);
            } catch (SocketTimeoutException e) {
                log.info("Cliente " + origem + " não enviou nada em " + tempoLimiteMs / 1000 + " s; conexão encerrada");
                return;
            }

            String json = resposta.paraJson();

            Transporte.escreverMensagem(saida, json, charset);
            log.servidor("Resposta enviada: " + json);
            encerrarComCalma(s, entrada);
        } catch (IOException e) {
            log.erro("Falha de comunicação com " + origem + ": " + e.getMessage());
        } finally {
            log.info("Cliente desconectado: " + origem);
        }
    }

    private static void encerrarComCalma(Socket s, InputStream entrada) {
        try {
            s.shutdownOutput();
            s.setSoTimeout(500);
            long limite = System.nanoTime() + 1_000_000_000L;
            byte[] lixo = new byte[1024];
            while (System.nanoTime() < limite && entrada.read(lixo) != -1) {
            }
        } catch (IOException ignorada) {
        }
    }
}
