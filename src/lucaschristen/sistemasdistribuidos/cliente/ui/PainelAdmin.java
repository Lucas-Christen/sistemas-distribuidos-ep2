package lucaschristen.sistemasdistribuidos.cliente.ui;

import static lucaschristen.sistemasdistribuidos.cliente.ui.Formularios.*;
import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

final class PainelAdmin extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Contexto ctx;

    private final JTextField cadNome = new JTextField(16);
    private final JTextField cadUsername = new JTextField(16);
    private final JPasswordField cadSenha = new JPasswordField(16);
    private final JComboBox<String> cadRole = new JComboBox<>(new String[] {ROLE_USER, ROLE_ADMIN});

    private final JTextField alvoUsername = new JTextField(16);
    private final JLabel alvoNome = new JLabel("—");
    private final JLabel alvoRole = new JLabel("—");
    private final JTextField alvoNovoNome = new JTextField(16);

    PainelAdmin(Contexto ctx) {
        super(new BorderLayout());
        this.ctx = ctx;

        JButton cadastrar = ctx.acao(new JButton("Cadastrar (register com role)"));
        cadastrar.addActionListener(e -> cadastrar());
        JPanel cadastro = formulario("Cadastrar usuário",
                new String[] {"Nome (name):", "Username:", "Senha:", "Perfil (role):"},
                new JComponent[] {cadNome, cadUsername, cadSenha, cadRole});
        adicionarLinha(cadastro, cadastrar);
        adicionarLinha(cadastro, dica(Validacao.REGRA_NOME, Validacao.REGRA_USERNAME, Validacao.REGRA_SENHA));

        JButton consultar = ctx.acao(new JButton("Consultar (getuser)"));
        consultar.addActionListener(e -> consultar());
        alvoUsername.addActionListener(e -> consultar());
        JButton renomear = ctx.acao(new JButton("Alterar nome (updateusername)"));
        renomear.addActionListener(e -> renomear());
        JButton excluir = ctx.acao(new JButton("Excluir conta (deleteuser)"));
        excluir.setForeground(new Color(170, 0, 0));
        excluir.addActionListener(e -> excluir());
        JPanel gestao = formulario("Gerenciar outra conta",
                new String[] {"Username:", "Nome:", "Perfil:", "Novo nome:"},
                new JComponent[] {alvoUsername, alvoNome, alvoRole, alvoNovoNome});
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        botoes.add(consultar);
        botoes.add(renomear);
        botoes.add(excluir);
        adicionarLinha(gestao, botoes);
        adicionarLinha(gestao, dica("Estes dados ficam separados da sua sessão: seu token, username e perfil não mudam.",
                "Excluir uma conta remove os veículos dela e encerra as sessões dela."));

        JPanel pilha = new JPanel(new BorderLayout(0, 8));
        pilha.add(cadastro, BorderLayout.NORTH);
        pilha.add(gestao, BorderLayout.CENTER);
        add(new JScrollPane(pilha), BorderLayout.CENTER);
    }

    void limpar() {
        cadNome.setText("");
        cadUsername.setText("");
        cadSenha.setText("");
        alvoUsername.setText("");
        alvoNome.setText("—");
        alvoRole.setText("—");
        alvoNovoNome.setText("");
    }

    private void cadastrar() {
        String nome = cadNome.getText();
        String username = cadUsername.getText().strip();
        String senha = new String(cadSenha.getPassword());
        String role = (String) cadRole.getSelectedItem();
        if (ctx.validarAntes()) {
            if (!Validacao.nomeValido(nome)) {
                ctx.aviso(Validacao.REGRA_NOME);
                return;
            }
            if (!Validacao.usernameValido(username)) {
                ctx.aviso(Validacao.REGRA_USERNAME);
                return;
            }
            if (!Validacao.senhaValida(senha)) {
                ctx.aviso(Validacao.REGRA_SENHA);
                return;
            }
        }
        ctx.executar(() -> ctx.api().cadastrarComPerfil(nome, username, senha, role), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                cadNome.setText("");
                cadUsername.setText("");
                cadSenha.setText("");
                alvoUsername.setText(username);
            }
        });
    }

    private String alvo() {
        String username = alvoUsername.getText().strip();
        if (username.isEmpty()) {
            ctx.aviso("Informe o username da conta.");
            return null;
        }
        return username;
    }

    private void consultar() {
        String username = alvo();
        if (username == null) {
            return;
        }
        ctx.executar(() -> ctx.api().consultarUsuario(username), r -> {
            if (r.statusCode() == OK) {
                String nome = r.textoEmData("name");
                alvoNome.setText(nome == null ? "(não informado)" : nome);
                String role = r.textoEmData("role");
                alvoRole.setText(role == null ? "(não informado)" : role);
                if (nome != null) {
                    alvoNovoNome.setText(nome);
                }
            } else {
                alvoNome.setText("—");
                alvoRole.setText("—");
            }
            ctx.resultado(r);
        });
    }

    private void renomear() {
        String username = alvo();
        if (username == null) {
            return;
        }
        String nome = alvoNovoNome.getText();
        if (ctx.validarAntes() && !Validacao.nomeValido(nome)) {
            ctx.aviso(Validacao.REGRA_NOME);
            return;
        }
        ctx.executar(() -> ctx.api().atualizarNomeDe(username, nome), r -> {
            ctx.resultado(r);
            if (r.sucesso()) {
                consultar();
            }
        });
    }

    private void excluir() {
        String username = alvo();
        if (username == null) {
            return;
        }
        boolean propria = username.equals(ctx.api().getUsername());
        int ok = JOptionPane.showConfirmDialog(this,
                (propria ? "Esta é a SUA conta. " : "") + "Excluir a conta " + username
                        + " e todos os veículos dela?\nIsso não tem volta.",
                "Excluir conta", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        ctx.executar(() -> ctx.api().excluirUsuario(username), r -> {
            if (r.sucesso() && propria) {
                ctx.contaPropriaExcluida(r);
                return;
            }
            ctx.resultado(r);
            if (r.sucesso()) {
                alvoNome.setText("—");
                alvoRole.setText("—");
                alvoNovoNome.setText("");
            }
        });
    }
}
