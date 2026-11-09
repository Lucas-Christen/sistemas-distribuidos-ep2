package lucaschristen.sistemasdistribuidos.servidor;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Json;
import lucaschristen.sistemasdistribuidos.protocolo.JsonInvalidoException;
import lucaschristen.sistemasdistribuidos.protocolo.Protocolo;
import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class Banco {
    public static final class Estado {
        private final Map<String, Usuario> usuarios;
        private final Map<String, Vaga> vagas;
        private final Map<String, Veiculo> veiculos;
        private boolean alterado;

        private Estado() {
            usuarios = new LinkedHashMap<>();
            vagas = new LinkedHashMap<>();
            veiculos = new LinkedHashMap<>();
        }

        private Estado(Estado origem) {
            usuarios = new LinkedHashMap<>(origem.usuarios);
            vagas = new LinkedHashMap<>(origem.vagas);
            veiculos = new LinkedHashMap<>(origem.veiculos);
        }

        public Optional<Usuario> usuario(String username) {
            return Optional.ofNullable(username == null ? null : usuarios.get(username));
        }

        public List<Usuario> usuarios() {
            return new ArrayList<>(usuarios.values());
        }

        public void gravarUsuario(Usuario usuario) {
            usuarios.put(usuario.username(), usuario);
            alterado = true;
        }

        public void removerUsuario(String username) {
            if (usuarios.remove(username) != null) {
                alterado = true;
            }
        }

        public Optional<Vaga> vaga(String spotName) {
            if (spotName == null) {
                return Optional.empty();
            }
            Vaga exata = vagas.get(spotName);
            if (exata != null) {
                return Optional.of(exata);
            }
            return vagas.values().stream().filter(v -> v.spotName().equalsIgnoreCase(spotName)).findFirst();
        }

        public List<Vaga> vagas() {
            List<Vaga> lista = new ArrayList<>(vagas.values());
            lista.sort(Comparator.comparingInt(Vaga::floor).thenComparing(Vaga::spotName, String.CASE_INSENSITIVE_ORDER));
            return lista;
        }

        public Optional<Vaga> vagaDoVeiculo(String licensePlate) {
            return vagas.values().stream()
                    .filter(v -> licensePlate != null && licensePlate.equals(v.licensePlate()))
                    .findFirst();
        }

        public void gravarVaga(Vaga vaga) {
            vagas.put(vaga.spotName(), vaga);
            alterado = true;
        }

        public void removerVaga(String spotName) {
            if (vagas.remove(spotName) != null) {
                alterado = true;
            }
        }

        public Optional<Veiculo> veiculo(String licensePlate) {
            return Optional.ofNullable(licensePlate == null ? null : veiculos.get(licensePlate));
        }

        public List<Veiculo> veiculos() {
            List<Veiculo> lista = new ArrayList<>(veiculos.values());
            lista.sort(Comparator.comparing(Veiculo::licensePlate));
            return lista;
        }

        public List<Veiculo> veiculosDe(String ownerUsername) {
            List<Veiculo> lista = new ArrayList<>();
            for (Veiculo v : veiculos()) {
                if (v.ownerUsername().equals(ownerUsername)) {
                    lista.add(v);
                }
            }
            return lista;
        }

        public void gravarVeiculo(Veiculo veiculo) {
            veiculos.put(veiculo.licensePlate(), veiculo);
            alterado = true;
        }

        public void removerVeiculo(String licensePlate) {
            if (veiculos.remove(licensePlate) != null) {
                alterado = true;
            }
        }
    }

    private final Path arquivo;
    private final RegistroLog log;
    private Estado estado = new Estado();

    public Banco(Path arquivo, RegistroLog log) {
        this.arquivo = arquivo.toAbsolutePath().normalize();
        this.log = log;
        carregar();
    }

    public synchronized <T> T ler(Function<Estado, T> consulta) {
        return consulta.apply(estado);
    }

    public synchronized <T> T alterar(Function<Estado, T> transacao) {
        Estado copia = new Estado(estado);
        T resultado = transacao.apply(copia);
        if (copia.alterado) {
            try {
                salvar(copia);
            } catch (IOException e) {
                throw new UncheckedIOException("não foi possível gravar " + arquivo, e);
            }
            copia.alterado = false;
            estado = copia;
        }
        return resultado;
    }

    public Path getArquivo() {
        return arquivo;
    }

    private void salvar(Estado e) throws IOException {
        List<Map<String, Object>> usuarios = new ArrayList<>();
        for (Usuario u : e.usuarios.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", u.name());
            item.put("username", u.username());
            item.put("password", u.password());
            item.put("role", u.role());
            usuarios.add(item);
        }
        List<Map<String, Object>> vagas = new ArrayList<>();
        for (Vaga v : e.vagas.values()) {
            Map<String, Object> item = v.paraProtocolo();
            item.put("occupiedBy", v.ocupadaPor());
            vagas.add(item);
        }
        List<Map<String, Object>> veiculos = new ArrayList<>();
        for (Veiculo v : e.veiculos.values()) {
            Map<String, Object> item = v.paraProtocolo();
            item.put("ownerUsername", v.ownerUsername());
            veiculos.add(item);
        }
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("users", usuarios);
        raiz.put("spots", vagas);
        raiz.put("vehicles", veiculos);

        Files.createDirectories(arquivo.getParent());
        Path temporario = Files.createTempFile(arquivo.getParent(), "banco-", ".tmp");
        try {
            Files.writeString(temporario, Json.escrever(raiz) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException semMoveAtomico) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private void carregar() {
        Path origem = arquivo;
        if (!Files.exists(origem)) {
            Path legado = arquivo.resolveSibling("usuarios.json");
            if (!Files.exists(legado)) {
                log.info("Banco de dados ainda não existe; será criado em " + arquivo);
                return;
            }
            log.info("Importando usuários da EP1 de " + legado);
            origem = legado;
        }
        try {
            Object conteudo = Json.ler(Files.readString(origem, StandardCharsets.UTF_8));
            Estado lido = new Estado();
            if (conteudo instanceof List<?> listaLegada) {
                lerUsuarios(listaLegada, lido);
            } else {
                Map<String, Object> raiz = Dados.comoObjeto(conteudo);
                if (raiz == null) {
                    throw new JsonInvalidoException("formato desconhecido", 0);
                }
                lerUsuarios(Dados.campo(raiz, "users"), lido);
                lerVagas(Dados.campo(raiz, "spots"), lido);
                lerVeiculos(Dados.campo(raiz, "vehicles"), lido);
            }
            estado = lido;
            log.info(lido.usuarios.size() + " usuário(s), " + lido.vagas.size() + " vaga(s) e "
                    + lido.veiculos.size() + " veículo(s) carregados de " + origem);
        } catch (IOException | JsonInvalidoException | ClassCastException e) {
            Path copia = origem.resolveSibling(origem.getFileName() + ".corrompido");
            log.erro("Banco de dados ilegível (" + e.getMessage() + "). Movido para " + copia + " e começando vazio.");
            try {
                Files.move(origem, copia, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException naoMoveu) {
                log.erro("Também não foi possível mover o arquivo: " + naoMoveu.getMessage());
            }
        }
    }

    private static void lerUsuarios(Object lista, Estado e) {
        for (Map<String, Object> o : objetos(lista)) {
            String name = Dados.texto(o, "name");
            String username = Dados.texto(o, "username");
            String password = Dados.texto(o, "password");
            String role = Dados.texto(o, "role");
            if (name != null && username != null && password != null) {
                e.usuarios.put(username, new Usuario(name, username, password,
                        Validacao.roleValida(role) ? role : Protocolo.ROLE_USER));
            }
        }
    }

    private static void lerVagas(Object lista, Estado e) {
        for (Map<String, Object> o : objetos(lista)) {
            String nome = Dados.texto(o, "spotName");
            String tipo = Dados.texto(o, "type");
            String status = Dados.texto(o, "status");
            Object andar = Dados.campo(o, "floor");
            if (nome == null || !Validacao.tipoValido(tipo) || !Validacao.statusVagaValido(status)
                    || !(andar instanceof Number n)) {
                continue;
            }
            String placa = Dados.texto(o, "licensePlate");
            String ocupadaPor = Dados.texto(o, "occupiedBy");
            if (!Protocolo.VAGA_OCUPADA.equals(status)) {
                placa = null;
                ocupadaPor = null;
            }
            e.vagas.put(nome, new Vaga(nome, n.intValue(), tipo, status, placa, ocupadaPor));
        }
    }

    private static void lerVeiculos(Object lista, Estado e) {
        for (Map<String, Object> o : objetos(lista)) {
            String placa = Dados.texto(o, "licensePlate");
            String marca = Dados.texto(o, "brand");
            String tipo = Dados.texto(o, "type");
            String dono = Dados.texto(o, "ownerUsername");
            if (placa != null && marca != null && Validacao.tipoValido(tipo) && dono != null) {
                e.veiculos.put(placa, new Veiculo(placa, marca, Dados.texto(o, "model"), tipo, dono));
            }
        }
    }

    private static List<Map<String, Object>> objetos(Object lista) {
        List<Map<String, Object>> saida = new ArrayList<>();
        if (lista instanceof List<?> itens) {
            for (Object item : itens) {
                Map<String, Object> o = Dados.comoObjeto(item);
                if (o != null) {
                    saida.add(o);
                }
            }
        }
        return saida;
    }
}
