package lucaschristen.sistemasdistribuidos.cliente;

import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiContas {
    private final ClienteTcp tcp;
    private volatile String token;
    private volatile String username;
    private volatile String role;

    public ApiContas(ClienteTcp tcp) {
        this.tcp = tcp;
    }

    public ClienteTcp getTcp() {
        return tcp;
    }

    public boolean isLogado() {
        return token != null;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Resposta login(String username, String senha) throws FalhaComunicacao {
        Resposta r = tcp.enviar(LOGIN, mapa("username", username, "password", senha));
        if (r.statusCode() == OK) {
            String recebido = r.textoEmData("token");
            if (recebido == null) {
                throw new FalhaComunicacao("O servidor respondeu 200 ao login, mas sem data.token.");
            }
            this.token = recebido;
            this.username = username;
            this.role = null;
        }
        return r;
    }

    public Resposta cadastrar(String nome, String username, String senha) throws FalhaComunicacao {
        return tcp.enviar(REGISTER, mapa("name", nome, "username", username, "password", senha));
    }

    public Resposta cadastrarComPerfil(String nome, String username, String senha, String perfil)
            throws FalhaComunicacao {
        return tcp.enviar(REGISTER, mapa("name", nome, "username", username, "password", senha,
                "role", perfil, "token", token));
    }

    public Resposta consultar() throws FalhaComunicacao {
        Resposta r = tcp.enviar(GET_USER, mapa("token", token, "username", username));
        if (r.statusCode() == OK) {
            String recebido = r.textoEmData("role");
            if (recebido != null) {
                role = recebido;
            }
        }
        return r;
    }

    public Resposta consultarUsuario(String outro) throws FalhaComunicacao {
        return tcp.enviar(GET_USER, mapa("token", token, "username", outro));
    }

    public Resposta atualizarNome(String novoNome) throws FalhaComunicacao {
        return atualizarNomeDe(username, novoNome);
    }

    public Resposta atualizarNomeDe(String alvo, String novoNome) throws FalhaComunicacao {
        return tcp.enviar(UPDATE_USER_NAME, mapa("token", token, "username", alvo, "name", novoNome));
    }

    public Resposta atualizarSenha(String senhaAtual, String novaSenha) throws FalhaComunicacao {
        return tcp.enviar(UPDATE_USER_PASSWORD, mapa("token", token, "username", username,
                "oldPassword", senhaAtual, "newPassword", novaSenha));
    }

    public Resposta excluirConta() throws FalhaComunicacao {
        return excluirUsuario(username);
    }

    public Resposta excluirUsuario(String alvo) throws FalhaComunicacao {
        boolean propria = alvo != null && alvo.equals(username);
        Resposta r = tcp.enviar(DELETE_USER, mapa("token", token, "username", alvo));
        if (r.statusCode() == OK && propria) {
            encerrarSessaoLocal();
        }
        return r;
    }

    public Resposta logout() throws FalhaComunicacao {
        try {
            return tcp.enviar(LOGOUT, mapa("token", token));
        } finally {
            encerrarSessaoLocal();
        }
    }

    public Resposta criarVaga(String spotName, int floor, String type) throws FalhaComunicacao {
        return tcp.enviar(CREATE_VAGA, mapa("token", token, "spotName", spotName, "floor", floor, "type", type));
    }

    public Resposta listarVagas() throws FalhaComunicacao {
        return tcp.enviar(GET_VAGAS, mapa("token", token));
    }

    public Resposta atualizarVaga(String spotName, Map<String, Object> alteracoes) throws FalhaComunicacao {
        Map<String, Object> dados = mapa("token", token, "spotName", spotName);
        dados.putAll(alteracoes);
        return tcp.enviar(UPDATE_VAGA, dados);
    }

    public Resposta excluirVaga(String spotName) throws FalhaComunicacao {
        return tcp.enviar(DELETE_VAGA, mapa("token", token, "spotName", spotName));
    }

    public Resposta criarVeiculo(String placa, String marca, String modelo, String tipo) throws FalhaComunicacao {
        return tcp.enviar(CREATE_VEHICLE, mapa("token", token, "brand", marca, "model", modelo, "type", tipo,
                "licensePlate", placa));
    }

    public Resposta listarVeiculos() throws FalhaComunicacao {
        return tcp.enviar(GET_VEHICLES, mapa("token", token));
    }

    public Resposta consultarVeiculo(String placa) throws FalhaComunicacao {
        return tcp.enviar(GET_VEHICLE, mapa("token", token, "licensePlate", placa));
    }

    public Resposta atualizarVeiculo(String placa, Map<String, Object> alteracoes) throws FalhaComunicacao {
        Map<String, Object> dados = mapa("token", token, "licensePlate", placa);
        dados.putAll(alteracoes);
        return tcp.enviar(UPDATE_VEHICLE, dados);
    }

    public Resposta excluirVeiculo(String placa) throws FalhaComunicacao {
        return tcp.enviar(DELETE_VEHICLE, mapa("token", token, "licensePlate", placa));
    }

    public Resposta localizarVeiculo(String placa) throws FalhaComunicacao {
        return tcp.enviar(FIND_VEHICLE, mapa("token", token, "licensePlate", placa));
    }

    public boolean sessaoContinuaValida() {
        try {
            return tcp.enviar(GET_USER, mapa("token", token, "username", username)).statusCode() != NAO_AUTORIZADO;
        } catch (FalhaComunicacao e) {
            return true;
        }
    }

    public void encerrarSessaoLocal() {
        token = null;
        username = null;
        role = null;
    }

    private static Map<String, Object> mapa(Object... chavesEValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            m.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return m;
    }
}
