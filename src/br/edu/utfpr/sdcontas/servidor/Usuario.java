package br.edu.utfpr.sdcontas.servidor;

public record Usuario(String name, String username, String password) {
    public Usuario comNome(String novoNome) {
        return new Usuario(novoNome, username, password);
    }

    public Usuario comSenha(String novoHash) {
        return new Usuario(name, username, novoHash);
    }
}
