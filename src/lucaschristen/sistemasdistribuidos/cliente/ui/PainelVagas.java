package lucaschristen.sistemasdistribuidos.cliente.ui;

import static lucaschristen.sistemasdistribuidos.cliente.ui.Formularios.*;
import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

final class PainelVagas extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String MANTER = "(manter)";

    private final transient Contexto ctx;
    private final DefaultTableModel modelo = modelo("Andar", "Vaga", "Tipo", "Status", "Placa");
    private final JTable tabela = tabela(modelo);

    private final JTextField novaVagaNome = new JTextField(6);
    private final JSpinner novaVagaAndar = new JSpinner(new SpinnerNumberModel(1, -99, 999, 1));
    private final JComboBox<String> novaVagaTipo = new JComboBox<>(new String[] {TIPO_COMBUSTAO, TIPO_ELETRICO});

    private final JTextField editarNome = new JTextField(6);
    private final JTextField editarAndar = new JTextField(6);
    private final JComboBox<String> editarStatus =
            new JComboBox<>(new String[] {MANTER, VAGA_DISPONIVEL, VAGA_FORA_DE_SERVICO});

    private final JComboBox<String> placaEstacionar = new JComboBox<>();

    private final JPanel painelAdmin;
    private final JPanel painelUser;

    PainelVagas(Contexto ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        placaEstacionar.setEditable(true);

        JButton atualizar = ctx.acao(new JButton("Atualizar lista (getvagas)"));
        atualizar.addActionListener(e -> carregar());
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(atualizar);
        topo.add(new JLabel("Selecione uma vaga na tabela antes de agir sobre ela."));
        add(topo, BorderLayout.NORTH);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JButton criar = ctx.acao(new JButton("Criar vaga"));
        criar.addActionListener(e -> criar());
        JPanel criacao = formulario("Nova vaga (createvaga)", new String[] {"Nome:", "Andar:", "Tipo:"},
                new JComponent[] {novaVagaNome, novaVagaAndar, novaVagaTipo});
        adicionarLinha(criacao, criar);
        adicionarLinha(criacao, dica(Validacao.REGRA_VAGA));

        JButton salvar = ctx.acao(new JButton("Salvar alterações"));
        salvar.addActionListener(e -> editar());
        JButton excluir = ctx.acao(new JButton("Excluir vaga"));
        excluir.addActionListener(e -> excluir());
        JButton liberarAdmin = ctx.acao(new JButton("Liberar vaga"));
        liberarAdmin.addActionListener(e -> liberar());
        JPanel edicao = formulario("Vaga selecionada (updatevaga / deletevaga)",
                new String[] {"Novo nome:", "Novo andar:", "Status:"},
                new JComponent[] {editarNome, editarAndar, editarStatus});
        JPanel botoesEdicao = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        botoesEdicao.add(salvar);
        botoesEdicao.add(liberarAdmin);
        botoesEdicao.add(excluir);
        adicionarLinha(edicao, botoesEdicao);
        adicionarLinha(edicao, dica("Campos em branco e \"" + MANTER + "\" não são enviados."));

        painelAdmin = new JPanel(new GridLayout(1, 2, 8, 0));
        painelAdmin.add(criacao);
        painelAdmin.add(edicao);

        JButton estacionar = ctx.acao(new JButton("Estacionar"));
        estacionar.addActionListener(e -> estacionar());
        JButton liberar = ctx.acao(new JButton("Liberar vaga"));
        liberar.addActionListener(e -> liberar());
        JButton recarregarPlacas = ctx.acao(new JButton("Meus veículos"));
        recarregarPlacas.addActionListener(e -> carregarPlacas());
        painelUser = formulario("Estacionar na vaga selecionada (updatevaga)", new String[] {"Placa:"},
                new JComponent[] {placaEstacionar});
        JPanel botoesUser = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        botoesUser.add(estacionar);
        botoesUser.add(liberar);
        botoesUser.add(recarregarPlacas);
        adicionarLinha(painelUser, botoesUser);
        adicionarLinha(painelUser, dica("A vaga precisa estar disponível e ter o mesmo tipo do veículo."));

        JPanel sul = new JPanel(new BorderLayout());
        sul.add(painelAdmin, BorderLayout.NORTH);
        sul.add(painelUser, BorderLayout.SOUTH);
        add(sul, BorderLayout.SOUTH);

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                preencherEdicao();
            }
        });
    }

    void configurar(boolean admin, boolean user) {
        painelAdmin.setVisible(admin);
        painelUser.setVisible(user);
    }

    void limpar() {
        modelo.setRowCount(0);
        placaEstacionar.removeAllItems();
    }

    void carregar() {
        ctx.executar(ctx.api()::listarVagas, r -> {
            if (r.statusCode() == OK) {
                modelo.setRowCount(0);
                for (Map<String, Object> v : r.listaEmData()) {
                    modelo.addRow(new Object[] {texto(v, "floor"), texto(v, "spotName"), texto(v, "type"),
                            texto(v, "status"), texto(v, "licensePlate")});
                }
            }
            ctx.resultado(r);
        });
    }

    void carregarPlacas() {
        ctx.executar(ctx.api()::listarVeiculos, r -> {
            if (r.statusCode() == OK) {
                Object selecionada = placaEstacionar.getSelectedItem();
                placaEstacionar.removeAllItems();
                for (Map<String, Object> v : r.listaEmData()) {
                    placaEstacionar.addItem(texto(v, "licensePlate"));
                }
                if (selecionada != null) {
                    placaEstacionar.setSelectedItem(selecionada);
                }
            }
            ctx.resultado(r);
        });
    }

    private void preencherEdicao() {
        String nome = celula(tabela, 1);
        editarNome.setText(nome == null ? "" : nome);
        String andar = celula(tabela, 0);
        editarAndar.setText(andar == null ? "" : andar);
        editarStatus.setSelectedItem(MANTER);
    }

    private String vagaSelecionada() {
        String nome = celula(tabela, 1);
        if (nome == null) {
            ctx.aviso("Selecione uma vaga na tabela.");
        }
        return nome;
    }

    private void criar() {
        String nome = novaVagaNome.getText().strip();
        int andar = (Integer) novaVagaAndar.getValue();
        String tipo = (String) novaVagaTipo.getSelectedItem();
        if (ctx.validarAntes() && !Validacao.nomeVagaValido(nome)) {
            ctx.aviso(Validacao.REGRA_VAGA);
            return;
        }
        ctx.executar(() -> ctx.api().criarVaga(nome, andar, tipo), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                novaVagaNome.setText("");
                carregar();
            }
        });
    }

    private void editar() {
        String nome = vagaSelecionada();
        if (nome == null) {
            return;
        }
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        String novoNome = editarNome.getText().strip();
        if (!novoNome.isEmpty() && !novoNome.equals(nome)) {
            if (ctx.validarAntes() && !Validacao.nomeVagaValido(novoNome)) {
                ctx.aviso(Validacao.REGRA_VAGA);
                return;
            }
            alteracoes.put("newSpotName", novoNome);
        }
        String andarTexto = editarAndar.getText().strip();
        if (!andarTexto.isEmpty() && !andarTexto.equals(celula(tabela, 0))) {
            try {
                alteracoes.put("floor", Integer.parseInt(andarTexto));
            } catch (NumberFormatException e) {
                ctx.aviso("O andar deve ser um número inteiro.");
                return;
            }
        }
        String status = (String) editarStatus.getSelectedItem();
        if (!MANTER.equals(status)) {
            alteracoes.put("status", status);
        }
        if (alteracoes.isEmpty()) {
            ctx.aviso("Nada para alterar.");
            return;
        }
        atualizar(nome, alteracoes);
    }

    private void estacionar() {
        String nome = vagaSelecionada();
        if (nome == null) {
            return;
        }
        Object item = placaEstacionar.getSelectedItem();
        String placa = item == null ? "" : item.toString().strip();
        if (placa.isEmpty() || (ctx.validarAntes() && !Validacao.placaValida(placa))) {
            ctx.aviso(Validacao.REGRA_PLACA);
            return;
        }
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("status", VAGA_OCUPADA);
        alteracoes.put("licensePlate", placa);
        atualizar(nome, alteracoes);
    }

    private void liberar() {
        String nome = vagaSelecionada();
        if (nome == null) {
            return;
        }
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("status", VAGA_DISPONIVEL);
        alteracoes.put("licensePlate", null);
        atualizar(nome, alteracoes);
    }

    private void atualizar(String nome, Map<String, Object> alteracoes) {
        ctx.executar(() -> ctx.api().atualizarVaga(nome, alteracoes), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                carregar();
            }
        });
    }

    private void excluir() {
        String nome = vagaSelecionada();
        if (nome == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Excluir a vaga " + nome + "?", "Excluir vaga",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        ctx.executar(() -> ctx.api().excluirVaga(nome), (Resposta r) -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                carregar();
            }
        });
    }
}
