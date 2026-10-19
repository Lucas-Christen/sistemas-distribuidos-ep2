package lucaschristen.sistemasdistribuidos.cliente;

import lucaschristen.sistemasdistribuidos.cliente.ui.JanelaCliente;
import javax.swing.SwingUtilities;

public final class ClienteApp {
    private ClienteApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JanelaCliente.aplicarVisual();
            new JanelaCliente().setVisible(true);
        });
    }
}
