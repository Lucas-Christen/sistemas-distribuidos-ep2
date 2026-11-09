package lucaschristen.sistemasdistribuidos.cliente.ui;

import static lucaschristen.sistemasdistribuidos.cliente.ui.Formularios.*;
import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class PainelBusca extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Contexto ctx;
    private final JTextField placa = new JTextField(10);
    private final JLabel veiculo = new JLabel("—");
    private final JLabel localizacao = new JLabel("—");

    PainelBusca(Contexto ctx) {
        super(new BorderLayout());
        this.ctx = ctx;

        JButton localizar = ctx.acao(new JButton("Onde está? (findvehicle)"));
        localizar.addActionListener(e -> localizar());
        placa.addActionListener(e -> localizar());
        JButton consultar = ctx.acao(new JButton("Dados do veículo (getvehicle)"));
        consultar.addActionListener(e -> consultar());
        JButton excluir = ctx.acao(new JButton("Excluir veículo (deletevehicle)"));
        excluir.addActionListener(e -> excluir());

        JPanel form = formulario("Buscar veículo pela placa", new String[] {"Placa:", "Veículo:", "Localização:"},
                new JComponent[] {placa, veiculo, localizacao});
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        botoes.add(localizar);
        botoes.add(consultar);
        botoes.add(excluir);
        adicionarLinha(form, botoes);
        adicionarLinha(form, dica("Usuário comum: só encontra os próprios veículos. Administrador: qualquer veículo."));
        add(form, BorderLayout.NORTH);
    }

    void limpar() {
        placa.setText("");
        veiculo.setText("—");
        localizacao.setText("—");
    }

    private String placaInformada() {
        String p = placa.getText().strip();
        if (p.isEmpty() || (ctx.validarAntes() && !Validacao.placaValida(p))) {
            ctx.aviso(Validacao.REGRA_PLACA);
            return null;
        }
        return p;
    }

    private void localizar() {
        String p = placaInformada();
        if (p == null) {
            return;
        }
        ctx.executar(() -> ctx.api().localizarVeiculo(p), r -> {
            if (r.statusCode() == OK) {
                localizacao.setText("Vaga " + r.textoEmData("spotName") + " · andar " + r.textoEmData("floor")
                        + " · " + r.textoEmData("status"));
            } else {
                localizacao.setText("—");
            }
            ctx.resultado(r);
        });
    }

    private void consultar() {
        String p = placaInformada();
        if (p == null) {
            return;
        }
        ctx.executar(() -> ctx.api().consultarVeiculo(p), r -> {
            Map<String, Object> d = Dados.comoObjeto(r.data());
            if (r.statusCode() == OK && d != null) {
                String modelo = texto(d, "model");
                veiculo.setText(texto(d, "licensePlate") + " · " + texto(d, "brand")
                        + (modelo.isEmpty() || "null".equals(modelo) ? "" : " " + modelo) + " · " + texto(d, "type"));
            } else {
                veiculo.setText("—");
            }
            ctx.resultado(r);
        });
    }

    private void excluir() {
        String p = placaInformada();
        if (p == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Excluir o veículo " + p + "?", "Excluir veículo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        ctx.executar(() -> ctx.api().excluirVeiculo(p), (Resposta r) -> {
            if (r.sucesso()) {
                veiculo.setText("—");
                localizacao.setText("—");
            }
            ctx.resultado(r);
        });
    }
}
