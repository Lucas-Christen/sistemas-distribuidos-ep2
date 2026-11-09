package lucaschristen.sistemasdistribuidos.servidor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ServidorContas {
    private final Banco banco;
    private final Sessoes sessoes;
    private final ServidorTcp tcp;

    public ServidorContas(int porta, Path arquivoBanco, RegistroLog log) {
        this(porta, arquivoBanco, log, ServidorTcp.TEMPO_LIMITE_PADRAO_MS);
    }

    public ServidorContas(int porta, Path arquivoBanco, RegistroLog log, int tempoLimiteMs) {
        this.banco = new Banco(arquivoBanco, log);
        this.sessoes = new Sessoes();
        ServicoContas contas = new ServicoContas(banco, sessoes, log);
        contas.garantirAdminInicial();
        ServicoEstacionamento estacionamento = new ServicoEstacionamento(banco, sessoes, log);
        this.tcp = new ServidorTcp(porta, new Despachante(contas, estacionamento, log), log, tempoLimiteMs);
    }

    public void iniciar() throws IOException {
        tcp.iniciar();
    }

    public void parar() {
        tcp.parar();
    }

    public boolean isRodando() {
        return tcp.isRodando();
    }

    public int getPorta() {
        return tcp.getPorta();
    }

    public List<Usuario> listarUsuarios() {
        return banco.ler(Banco.Estado::usuarios);
    }

    public List<Vaga> listarVagas() {
        return banco.ler(Banco.Estado::vagas);
    }

    public List<Veiculo> listarVeiculos() {
        return banco.ler(Banco.Estado::veiculos);
    }

    public List<Sessoes.Sessao> listarSessoes() {
        return sessoes.listar();
    }

    public Path getArquivoBanco() {
        return banco.getArquivo();
    }
}
