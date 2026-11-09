package lucaschristen.sistemasdistribuidos.cliente.ui;

import static lucaschristen.sistemasdistribuidos.cliente.ui.Formularios.*;
import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

final class PainelVeiculos extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Contexto ctx;
    private final DefaultTableModel modelo = modelo("Placa", "Marca", "Modelo", "Tipo");
    private final JTable tabela = tabela(modelo);

    private final JTextField placa = new JTextField(10);
    private final JTextField marca = new JTextField(14);
    private final JTextField modeloVeiculo = new JTextField(14);
    private final JComboBox<String> tipo = new JComboBox<>(new String[] {TIPO_COMBUSTAO, TIPO_ELETRICO});

    PainelVeiculos(Contexto ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;

        JButton atualizar = ctx.acao(new JButton("Atualizar lista (getvehicles)"));
        atualizar.addActionListener(e -> carregar());
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(atualizar);
        topo.add(new JLabel("Selecione um veículo para editar ou excluir. A placa não pode ser alterada."));
        add(topo, BorderLayout.NORTH);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JButton criar = ctx.acao(new JButton("Cadastrar (createvehicle)"));
        criar.addActionListener(e -> criar());
        JButton salvar = ctx.acao(new JButton("Salvar alterações (updatevehicle)"));
        salvar.addActionListener(e -> salvar());
        JButton excluir = ctx.acao(new JButton("Excluir (deletevehicle)"));
        excluir.addActionListener(e -> excluir());
        JButton limpar = new JButton("Limpar");
        limpar.addActionListener(e -> {
            tabela.clearSelection();
            placa.setText("");
            marca.setText("");
            modeloVeiculo.setText("");
        });

        JPanel form = formulario("Veículo", new String[] {"Placa:", "Marca:", "Modelo:", "Tipo:"},
                new JComponent[] {placa, marca, modeloVeiculo, tipo});
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        botoes.add(criar);
        botoes.add(salvar);
        botoes.add(excluir);
        botoes.add(limpar);
        adicionarLinha(form, botoes);
        adicionarLinha(form, dica(Validacao.REGRA_PLACA, Validacao.REGRA_MARCA,
                Validacao.REGRA_MODELO + " Deixe em branco para enviar model:null."));
        add(form, BorderLayout.SOUTH);

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tabela.getSelectedRow() >= 0) {
                placa.setText(celula(tabela, 0));
                marca.setText(celula(tabela, 1));
                modeloVeiculo.setText(celula(tabela, 2));
                tipo.setSelectedItem(celula(tabela, 3));
            }
        });
    }

    void limpar() {
        modelo.setRowCount(0);
    }

    void carregar() {
        ctx.executar(ctx.api()::listarVeiculos, r -> {
            if (r.statusCode() == OK) {
                modelo.setRowCount(0);
                for (Map<String, Object> v : r.listaEmData()) {
                    modelo.addRow(new Object[] {texto(v, "licensePlate"), texto(v, "brand"), texto(v, "model"),
                            texto(v, "type")});
                }
            }
            ctx.resultado(r);
        });
    }

    private boolean camposValidos(String p, String m, String mod) {
        if (!ctx.validarAntes()) {
            return true;
        }
        if (!Validacao.placaValida(p)) {
            ctx.aviso(Validacao.REGRA_PLACA);
            return false;
        }
        if (!Validacao.marcaValida(m)) {
            ctx.aviso(Validacao.REGRA_MARCA);
            return false;
        }
        if (!Validacao.modeloValido(mod)) {
            ctx.aviso(Validacao.REGRA_MODELO);
            return false;
        }
        return true;
    }

    private void criar() {
        String p = placa.getText().strip();
        String m = marca.getText().strip();
        String mod = modeloVeiculo.getText().strip().isEmpty() ? null : modeloVeiculo.getText().strip();
        String t = (String) tipo.getSelectedItem();
        if (!camposValidos(p, m, mod)) {
            return;
        }
        ctx.executar(() -> ctx.api().criarVeiculo(p, m, mod, t), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                carregar();
            }
        });
    }

    private void salvar() {
        String p = placa.getText().strip();
        String m = marca.getText().strip();
        String mod = modeloVeiculo.getText().strip().isEmpty() ? null : modeloVeiculo.getText().strip();
        String t = (String) tipo.getSelectedItem();
        if (p.isEmpty()) {
            ctx.aviso("Selecione um veículo ou informe a placa.");
            return;
        }
        if (!camposValidos(p, m, mod)) {
            return;
        }
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("brand", m);
        alteracoes.put("model", mod);
        alteracoes.put("type", t);
        ctx.executar(() -> ctx.api().atualizarVeiculo(p, alteracoes), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                carregar();
            }
        });
    }

    private void excluir() {
        String p = placa.getText().strip();
        if (p.isEmpty()) {
            ctx.aviso("Selecione um veículo ou informe a placa.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Excluir o veículo " + p + "?", "Excluir veículo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        ctx.executar(() -> ctx.api().excluirVeiculo(p), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                placa.setText("");
                carregar();
            }
        });
    }
}
