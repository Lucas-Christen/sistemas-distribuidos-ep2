package lucaschristen.sistemasdistribuidos.servidor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Sessoes {
    public record Sessao(String token, String username, Instant inicio) {
    }

    private final ConcurrentHashMap<String, Sessao> porToken = new ConcurrentHashMap<>();

    public String abrir(String username) {
        String token = UUID.randomUUID().toString();
        porToken.put(token, new Sessao(token, username, Instant.now()));
        return token;
    }

    public Optional<String> usuarioDoToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Sessao sessao = porToken.get(token);
        return sessao == null ? Optional.empty() : Optional.of(sessao.username());
    }

    public boolean encerrar(String token) {
        return token != null && porToken.remove(token) != null;
    }

    public int encerrarTodasDe(String username) {
        int antes = porToken.size();
        porToken.values().removeIf(s -> s.username().equals(username));
        return antes - porToken.size();
    }

    public int quantidade() {
        return porToken.size();
    }

    public List<Sessao> listar() {
        List<Sessao> lista = new ArrayList<>(porToken.values());
        lista.sort(Comparator.comparing(Sessao::inicio));
        return lista;
    }
}
