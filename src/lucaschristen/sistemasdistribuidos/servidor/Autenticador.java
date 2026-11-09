package lucaschristen.sistemasdistribuidos.servidor;

import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import java.util.Map;
import java.util.Optional;

final class Autenticador {
    record Sessao(String token, String username, Resposta erro) {
        boolean falhou() {
            return erro != null;
        }
    }

    private final Sessoes sessoes;

    Autenticador(Sessoes sessoes) {
        this.sessoes = sessoes;
    }

    static boolean informado(Map<String, Object> dados, String campo) {
        Object valor = Dados.campo(dados, campo);
        return valor != null && !(valor instanceof String s && s.isBlank());
    }

    Sessao sessao(Map<String, Object> dados) {
        if (!informado(dados, "token")) {
            return new Sessao(null, null, Resposta.erro(REQUISICAO_INVALIDA, MSG_TOKEN_AUSENTE));
        }
        Object valor = Dados.campo(dados, "token");
        String token = valor instanceof String s ? s.strip() : String.valueOf(valor);
        Optional<String> username = sessoes.usuarioDoToken(token);
        if (username.isEmpty()) {
            return new Sessao(null, null, sessaoInvalida());
        }
        return new Sessao(token, username.get(), null);
    }

    static Resposta sessaoInvalida() {
        return Resposta.erro(NAO_AUTORIZADO, MSG_SESSAO_INVALIDA);
    }
}
