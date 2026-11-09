package lucaschristen.sistemasdistribuidos.servidor;

import lucaschristen.sistemasdistribuidos.protocolo.Protocolo;
import java.util.LinkedHashMap;
import java.util.Map;

public record Vaga(String spotName, int floor, String type, String status, String licensePlate, String ocupadaPor) {
    public boolean disponivel() {
        return Protocolo.VAGA_DISPONIVEL.equals(status);
    }

    public boolean ocupada() {
        return Protocolo.VAGA_OCUPADA.equals(status);
    }

    public boolean foraDeServico() {
        return Protocolo.VAGA_FORA_DE_SERVICO.equals(status);
    }

    public Vaga renomeada(String novoNome, int novoAndar) {
        return new Vaga(novoNome, novoAndar, type, status, licensePlate, ocupadaPor);
    }

    public Vaga comStatus(String novoStatus) {
        return new Vaga(spotName, floor, type, novoStatus, null, null);
    }

    public Vaga ocupadaCom(String placa, String username) {
        return new Vaga(spotName, floor, type, Protocolo.VAGA_OCUPADA, placa, username);
    }

    public Map<String, Object> paraProtocolo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("floor", floor);
        m.put("spotName", spotName);
        m.put("status", status);
        m.put("licensePlate", licensePlate);
        m.put("type", type);
        return m;
    }
}
