package lucaschristen.sistemasdistribuidos.servidor;

import java.util.LinkedHashMap;
import java.util.Map;

public record Veiculo(String licensePlate, String brand, String model, String type, String ownerUsername) {
    public Veiculo comDados(String novaMarca, String novoModelo, String novoTipo) {
        return new Veiculo(licensePlate, novaMarca, novoModelo, novoTipo, ownerUsername);
    }

    public Map<String, Object> paraProtocolo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("model", model);
        m.put("type", type);
        m.put("licensePlate", licensePlate);
        return m;
    }
}
