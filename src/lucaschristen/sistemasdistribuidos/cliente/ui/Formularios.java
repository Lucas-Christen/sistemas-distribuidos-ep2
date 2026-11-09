package lucaschristen.sistemasdistribuidos.cliente.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

final class Formularios {
    private Formularios() {
    }

    static JPanel formulario(String titulo, String[] rotulos, JComponent[] campos) {
        JPanel painel = new JPanel(new GridBagLayout());
        if (titulo != null) {
            painel.setBorder(BorderFactory.createTitledBorder(titulo));
        } else {
            painel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        }
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rotulos.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            painel.add(new JLabel(rotulos[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            painel.add(campos[i], c);
        }
        return painel;
    }

    static void adicionarLinha(JPanel formulario, JComponent componente) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = GridBagConstraints.RELATIVE;
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        formulario.add(componente, c);
    }

    static JPanel noTopo(JComponent componente) {
        JPanel painel = new JPanel(new BorderLayout());
        painel.add(componente, BorderLayout.NORTH);
        return painel;
    }

    static JLabel dica(String... linhas) {
        JLabel rotulo = new JLabel("<html><body style='width:340px'><small>" + String.join("<br>", linhas)
                + "</small></body></html>");
        rotulo.setForeground(Color.DARK_GRAY);
        return rotulo;
    }

    static DefaultTableModel modelo(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
    }

    static JTable tabela(DefaultTableModel modelo) {
        JTable tabela = new JTable(modelo);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setAutoCreateRowSorter(true);
        return tabela;
    }

    static String celula(JTable tabela, int coluna) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return null;
        }
        Object valor = tabela.getModel().getValueAt(tabela.convertRowIndexToModel(linha), coluna);
        return valor == null ? null : String.valueOf(valor);
    }

    static String texto(Map<String, Object> item, String campo) {
        Object valor = item.get(campo);
        if (valor == null) {
            for (Map.Entry<String, Object> e : item.entrySet()) {
                if (e.getKey().equalsIgnoreCase(campo)) {
                    valor = e.getValue();
                    break;
                }
            }
        }
        if (valor instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return String.valueOf(n.longValue());
        }
        return valor == null ? "" : String.valueOf(valor);
    }
}
