package lucaschristen.sistemasdistribuidos.servidor;

import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ServicoContas {
    private final Banco banco;
    private final Sessoes sessoes;
    private final Autenticador autenticador;
    private final RegistroLog log;

    public ServicoContas(Banco banco, Sessoes sessoes, RegistroLog log) {
        this.banco = banco;
        this.sessoes = sessoes;
        this.autenticador = new Autenticador(sessoes);
        this.log = log;
    }

    public void garantirAdminInicial() {
        String hash = Senhas.gerarHash(ADMIN_INICIAL_SENHA);
        String acao = banco.alterar(e -> {
            Optional<Usuario> atual = e.usuario(ADMIN_INICIAL_USERNAME);
            if (atual.isEmpty()) {
                e.gravarUsuario(new Usuario(ADMIN_INICIAL_NOME, ADMIN_INICIAL_USERNAME, hash, ROLE_ADMIN));
                return "criada";
            }
            if (!atual.get().admin()) {
                e.gravarUsuario(atual.get().comRole(ROLE_ADMIN));
                return "promovida a admin";
            }
            return null;
        });
        if (acao != null) {
            log.info("Conta inicial '" + ADMIN_INICIAL_USERNAME + "' " + acao);
        }
    }

    public Resposta login(Map<String, Object> dados) {
        String username = Dados.texto(dados, "username");
        String senha = Dados.texto(dados, "password");

        Optional<Usuario> usuario = banco.ler(e -> e.usuario(username));

        String hash = usuario.map(Usuario::password).orElse(Senhas.HASH_FICTICIO);
        boolean senhaConfere = Senhas.confere(senha, hash);
        if (usuario.isEmpty() || !senhaConfere) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_LOGIN_INVALIDO);
        }

        String token = sessoes.abrir(username);
        if (banco.ler(e -> e.usuario(username)).isEmpty()) {
            sessoes.encerrar(token);
            return Resposta.erro(NAO_AUTORIZADO, MSG_LOGIN_INVALIDO);
        }
        log.cliente("Login: " + username + " [" + usuario.get().role() + "] (token " + token
                + ", sessões ativas: " + sessoes.quantidade() + ")");
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("token", token);
        return Resposta.ok(MSG_LOGIN_OK, resposta);
    }

    public Resposta register(Map<String, Object> dados) {
        boolean temToken = Autenticador.informado(dados, "token");
        boolean temRole = Autenticador.informado(dados, "role");
        String role = ROLE_USER;
        Autenticador.Sessao auth = null;
        if (temToken || temRole) {
            if (!temToken) {
                return Resposta.erro(REQUISICAO_INVALIDA, MSG_TOKEN_AUSENTE);
            }
            auth = autenticador.sessao(dados);
            if (auth.falhou()) {
                return auth.erro();
            }
            String pedida = Dados.texto(dados, "role");
            pedida = pedida == null ? null : pedida.strip();
            if (!Validacao.roleValida(pedida)) {
                return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("role"));
            }
            role = pedida;
        }

        String nome = normalizar(Dados.texto(dados, "name"));
        String username = Dados.texto(dados, "username");
        String senha = Dados.texto(dados, "password");

        Resposta permissao = auth == null ? null : exigirAdmin(auth.username(), MSG_ADMIN_CADASTRO);
        if (permissao != null) {
            return permissao;
        }
        if (banco.ler(e -> e.usuario(username)).isPresent()) {
            return Resposta.erro(CONFLITO, MSG_USERNAME_EM_USO);
        }
        if (!Validacao.nomeValido(nome)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("name"));
        }
        if (!Validacao.usernameValido(username)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("username"));
        }
        if (!Validacao.senhaValida(senha)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("password"));
        }
        String hash = Senhas.gerarHash(senha);
        String roleFinal = role;
        String criador = auth == null ? null : auth.username();
        Resposta resultado = banco.alterar(e -> {
            if (criador != null) {
                Optional<Usuario> admin = e.usuario(criador);
                if (admin.isEmpty()) {
                    return Autenticador.sessaoInvalida();
                }
                if (!admin.get().admin()) {
                    return Resposta.erro(NAO_AUTORIZADO, MSG_ADMIN_CADASTRO);
                }
            }
            if (e.usuario(username).isPresent()) {
                return Resposta.erro(CONFLITO, MSG_USERNAME_EM_USO);
            }
            e.gravarUsuario(new Usuario(nome, username, hash, roleFinal));
            return Resposta.criado(MSG_CADASTRO_OK);
        });
        if (resultado.sucesso()) {
            log.info("Usuário cadastrado: " + username + " [" + roleFinal + "]"
                    + (criador == null ? "" : " por " + criador));
        }
        return resultado;
    }

    public Resposta logout(Map<String, Object> dados) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        sessoes.encerrar(auth.token());
        log.cliente("Logout: " + auth.username() + " (sessões ativas: " + sessoes.quantidade() + ")");
        return Resposta.ok(MSG_LOGOUT_OK);
    }

    public Resposta getUser(Map<String, Object> dados) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        String pedido = usernamePedido(dados);
        return banco.ler(e -> {
            Optional<Usuario> eu = e.usuario(auth.username());
            if (eu.isEmpty()) {
                return Autenticador.sessaoInvalida();
            }
            Usuario alvo = eu.get();
            if (alvo.admin() && pedido != null && !pedido.equals(alvo.username())) {
                Optional<Usuario> outro = e.usuario(pedido);
                if (outro.isEmpty()) {
                    return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
                }
                alvo = outro.get();
            }
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("name", alvo.name());
            resposta.put("username", alvo.username());
            resposta.put("role", alvo.role());
            return Resposta.ok(MSG_USUARIO_ENCONTRADO, resposta);
        });
    }

    public Resposta updateUserName(Map<String, Object> dados) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        String pedido = usernamePedido(dados);
        String nome = normalizar(Dados.texto(dados, "name"));
        return banco.alterar(e -> {
            Optional<Usuario> eu = e.usuario(auth.username());
            if (eu.isEmpty()) {
                return Autenticador.sessaoInvalida();
            }
            String alvo = pedido == null ? auth.username() : pedido;
            if (!alvo.equals(auth.username()) && !eu.get().admin()) {
                return Resposta.erro(NAO_AUTORIZADO, MSG_OPERACAO_NAO_AUTORIZADA);
            }
            if (!Validacao.nomeValido(nome)) {
                return Resposta.erro(REQUISICAO_INVALIDA, MSG_NOME_FORA_DO_PADRAO);
            }
            Optional<Usuario> usuario = e.usuario(alvo);
            if (usuario.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
            }
            e.gravarUsuario(usuario.get().comNome(nome));
            return Resposta.ok(MSG_NOME_ATUALIZADO);
        });
    }

    public Resposta updateUserPassword(Map<String, Object> dados) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        String pedido = usernamePedido(dados);
        if (pedido != null && !pedido.equals(auth.username())) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_OPERACAO_NAO_AUTORIZADA);
        }
        Optional<Usuario> usuario = banco.ler(e -> e.usuario(auth.username()));
        if (usuario.isEmpty()) {
            return Autenticador.sessaoInvalida();
        }
        String senhaAtual = Dados.texto(dados, "oldPassword");
        String novaSenha = Dados.texto(dados, "newPassword");
        if (!Senhas.confere(senhaAtual, usuario.get().password())) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_SENHA_ATUAL_INCORRETA);
        }
        if (senhaAtual.equals(novaSenha) || !Validacao.senhaValida(novaSenha)) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_SENHA_FORA_DO_PADRAO);
        }
        String hash = Senhas.gerarHash(novaSenha);
        return banco.alterar(e -> {
            Optional<Usuario> atual = e.usuario(auth.username());
            if (atual.isEmpty()) {
                return Autenticador.sessaoInvalida();
            }
            e.gravarUsuario(atual.get().comSenha(hash));
            return Resposta.ok(MSG_SENHA_ATUALIZADA);
        });
    }

    public Resposta deleteUser(Map<String, Object> dados) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        String pedido = usernamePedido(dados);
        String alvo = pedido == null ? auth.username() : pedido;
        int[] veiculosRemovidos = {0};
        Resposta resultado = banco.alterar(e -> {
            Optional<Usuario> eu = e.usuario(auth.username());
            if (eu.isEmpty()) {
                return Autenticador.sessaoInvalida();
            }
            if (!alvo.equals(auth.username()) && !eu.get().admin()) {
                return Resposta.erro(NAO_AUTORIZADO, MSG_OPERACAO_NAO_AUTORIZADA);
            }
            if (e.usuario(alvo).isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
            }
            for (Veiculo v : e.veiculosDe(alvo)) {
                if (e.vagaDoVeiculo(v.licensePlate()).isPresent()) {
                    return Resposta.erro(CONFLITO, MSG_USUARIO_COM_VEICULO_ESTACIONADO);
                }
            }
            for (Veiculo v : e.veiculosDe(alvo)) {
                e.removerVeiculo(v.licensePlate());
                veiculosRemovidos[0]++;
            }
            e.removerUsuario(alvo);
            return Resposta.ok(MSG_USUARIO_DELETADO);
        });
        if (resultado.sucesso()) {
            int derrubadas = sessoes.encerrarTodasDe(alvo);
            log.cliente("Conta excluída: " + alvo + (alvo.equals(auth.username()) ? "" : " por " + auth.username())
                    + " (" + derrubadas + " sessão(ões) encerrada(s), " + veiculosRemovidos[0] + " veículo(s) removido(s))");
        }
        return resultado;
    }

    private Resposta exigirAdmin(String username, String mensagem) {
        Optional<Usuario> usuario = banco.ler(e -> e.usuario(username));
        if (usuario.isEmpty()) {
            return Autenticador.sessaoInvalida();
        }
        return usuario.get().admin() ? null : Resposta.erro(NAO_AUTORIZADO, mensagem);
    }

    private static String usernamePedido(Map<String, Object> dados) {
        Object enviado = Dados.campo(dados, "username");
        if (enviado == null || (enviado instanceof String s && s.isBlank())) {
            return null;
        }
        return enviado instanceof String s ? s.strip() : String.valueOf(enviado);
    }

    private static String normalizar(String texto) {
        return texto == null ? null : Normalizer.normalize(texto, Normalizer.Form.NFC);
    }
}
