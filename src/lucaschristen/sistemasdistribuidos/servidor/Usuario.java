package lucaschristen.sistemasdistribuidos.servidor;

import lucaschristen.sistemasdistribuidos.protocolo.Protocolo;

public record Usuario(String name, String username, String password, String role) {
    public Usuario {
        if (role == null) {
            role = Protocolo.ROLE_USER;
        }
    }

    public boolean admin() {
        return Protocolo.ROLE_ADMIN.equals(role);
    }

    public Usuario comNome(String novoNome) {
        return new Usuario(novoNome, username, password, role);
    }

    public Usuario comSenha(String novoHash) {
        return new Usuario(name, username, novoHash, role);
    }

    public Usuario comRole(String novaRole) {
        return new Usuario(name, username, password, novaRole);
    }
}
