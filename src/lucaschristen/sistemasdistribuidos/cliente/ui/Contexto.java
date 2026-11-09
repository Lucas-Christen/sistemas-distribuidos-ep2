package lucaschristen.sistemasdistribuidos.cliente.ui;

import lucaschristen.sistemasdistribuidos.cliente.ApiContas;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.JButton;

interface Contexto {
    ApiContas api();

    boolean validarAntes();

    JButton acao(JButton botao);

    void executar(Callable<Resposta> chamada, Consumer<Resposta> depois);

    void resultado(Resposta r);

    void aviso(String texto);

    void contaPropriaExcluida(Resposta r);
}
