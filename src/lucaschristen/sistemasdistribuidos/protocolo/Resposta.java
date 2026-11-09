package lucaschristen.sistemasdistribuidos.protocolo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Resposta(int statusCode, String message, Object data) {
    public static Resposta ok(String mensagem) {
        return new Resposta(Protocolo.OK, mensagem, null);
    }

    public static Resposta ok(String mensagem, Object dados) {
        return new Resposta(Protocolo.OK, mensagem, dados);
    }

    public static Resposta criado(String mensagem) {
        return new Resposta(Protocolo.CRIADO, mensagem, null);
    }

    public static Resposta criado(String mensagem, Object dados) {
        return new Resposta(Protocolo.CRIADO, mensagem, dados);
    }

    public static Resposta erro(int status, String mensagem) {
        return new Resposta(status, mensagem, null);
    }

    public boolean sucesso() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String paraJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("statusCode", statusCode);
        json.put("message", message == null ? "" : message);
        json.put("data", data);
        return Json.escrever(json);
    }

    public static Resposta deJson(String texto) {
        Map<String, Object> objeto = Dados.comoObjeto(Json.ler(texto));
        if (objeto == null) {
            throw new JsonInvalidoException("a resposta não é um objeto JSON", 0);
        }
        int status = converterStatus(Dados.campo(objeto, "statusCode"));
        Object mensagem = Dados.campo(objeto, "message");
        String textoMensagem = mensagem == null ? Protocolo.significado(status)
                : mensagem instanceof String s ? s : Json.escrever(mensagem);
        Object dados = Dados.campo(objeto, "data");
        Map<String, Object> dadosObjeto = Dados.comoObjeto(dados);
        return new Resposta(status, textoMensagem, dadosObjeto != null ? dadosObjeto : dados);
    }

    private static int converterStatus(Object valor) {
        if (valor instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) {
                return (int) d;
            }
        } else if (valor instanceof String s) {
            try {
                double d = Double.parseDouble(s.strip());
                if (d == Math.rint(d)) {
                    return (int) d;
                }
            } catch (NumberFormatException ignorada) {
            }
        }
        throw new JsonInvalidoException("resposta sem statusCode inteiro", 0);
    }

    public String textoEmData(String campo) {
        Map<String, Object> objeto = Dados.comoObjeto(data);
        if (objeto == null) {
            return null;
        }
        Object valor = Dados.campo(objeto, campo);
        if (valor == null) {
            return null;
        }
        String texto = valor instanceof String s ? s : String.valueOf(valor);
        return texto.isBlank() ? null : texto.strip();
    }

    public List<Map<String, Object>> listaEmData() {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (data instanceof List<?> itens) {
            for (Object item : itens) {
                Map<String, Object> objeto = Dados.comoObjeto(item);
                if (objeto != null) {
                    lista.add(objeto);
                }
            }
        }
        return lista;
    }
}
