package lucaschristen.sistemasdistribuidos;

import lucaschristen.sistemasdistribuidos.protocolo.Json;
import lucaschristen.sistemasdistribuidos.servidor.RegistroLog;
import lucaschristen.sistemasdistribuidos.servidor.ServidorContas;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class TesteEp2 {
    private static final String SENHA = "Senha123!";
    private static final String CAMPOS = "Campos obrigatórios ausentes ou inválidos.";
    private static final String SESSAO = "Sessão expirada ou encerrada.";
    private static final String TOKEN_AUSENTE = "Token de autenticação não fornecido.";
    private static final String APENAS_ADMIN = "Operação permitida apenas para administradores.";
    private static final String APENAS_USER = "Operação permitida apenas para usuários comuns.";
    private static final String VEICULO_404 = "Veículo não encontrado.";
    private static final String VEICULO_EM_VAGA = "Veículo associado a uma vaga. Libere a vaga antes desta operação.";

    private static int porta;

    private TesteEp2() {
    }

    static void executar(Verifica v) throws Exception {
        Path pasta = Files.createTempDirectory("lucaschristen-sd-ep2");
        Path arquivo = pasta.resolve("banco.json");
        ServidorContas servidor = new ServidorContas(0, arquivo, new RegistroLog(false), 1500);
        servidor.iniciar();
        porta = servidor.getPorta();
        try {
            cenarios(v);
        } finally {
            servidor.parar();
        }
        persistencia(v, arquivo);
        falhaDeGravacao(v);
        importacaoEp1(v);
    }

    private static String admin;
    private static String ana;
    private static String bob;

    private static void cenarios(Verifica v) throws Exception {
        v.grupo("EP2 — conta admin inicial");
        Map<String, Object> r = esperar(v, "login admin/Admin123!", req("login", "username", "admin", "password", "Admin123!"),
                200, "Sucesso no Login");
        admin = token(r);
        r = esperar(v, "getuser do admin", req("getuser", "token", admin, "username", "admin"), 200, "Usuário encontrado com sucesso");
        v.igual("role do admin", "admin", data(r, "role"));
        v.igual("name do admin", "admin", data(r, "name"));

        v.grupo("EP2 — register com role/token");
        esperar(v, "cadastro comum (sem role e sem token)", req("register", "name", "Ana Lima", "username", "ana", "password", SENHA),
                201, "Usuário criado com sucesso");
        esperar(v, "role e token null explícitos criam user", req("register", "name", "Bob", "username", "bob", "password", SENHA,
                "role", null, "token", null), 201, "Usuário criado com sucesso");
        ana = token(requisitar(req("login", "username", "ana", "password", SENHA)));
        bob = token(requisitar(req("login", "username", "bob", "password", SENHA)));
        v.igual("usuário comum nasce com role user", "user",
                data(requisitar(req("getuser", "token", ana, "username", "ana")), "role"));
        esperar(v, "role sem token", req("register", "name", "X", "username", "xx1", "password", SENHA, "role", "admin"),
                400, TOKEN_AUSENTE);
        esperar(v, "token sem role", req("register", "name", "X", "username", "xx1", "password", SENHA, "token", admin),
                400, "O campo role não está no padrão esperado.");
        esperar(v, "role inválida", req("register", "name", "X", "username", "xx1", "password", SENHA, "token", admin,
                "role", "chefe"), 400, "O campo role não está no padrão esperado.");
        esperar(v, "token inválido com role", req("register", "name", "X", "username", "xx1", "password", SENHA,
                "token", UUID.randomUUID().toString(), "role", "user"), 401, SESSAO);
        esperar(v, "user tentando criar conta com role", req("register", "name", "X", "username", "xx1", "password", SENHA,
                "token", ana, "role", "admin"), 401, "Apenas administradores podem cadastrar usuários informando role.");
        v.igual("… e nenhuma conta foi criada", 401L,
                status(requisitar(req("login", "username", "xx1", "password", SENHA))));
        esperar(v, "admin cria outro admin", req("register", "name", "Segundo Admin", "username", "adm2", "password", SENHA,
                "token", admin, "role", "admin"), 201, "Usuário criado com sucesso");
        esperar(v, "admin cria user", req("register", "name", "Carla", "username", "carla", "password", SENHA,
                "token", admin, "role", "user"), 201, "Usuário criado com sucesso");
        esperar(v, "admin criando username repetido", req("register", "name", "Carla", "username", "carla", "password", SENHA,
                "token", admin, "role", "user"), 409, "O username já está em uso.");
        String adm2 = token(requisitar(req("login", "username", "adm2", "password", SENHA)));
        v.igual("adm2 tem role admin", "admin", data(requisitar(req("getuser", "token", adm2)), "role"));

        v.grupo("EP2 — admin gerenciando outras contas");
        r = esperar(v, "admin consulta outra conta", req("getuser", "token", admin, "username", "ana"),
                200, "Usuário encontrado com sucesso");
        v.igual("… name", "Ana Lima", data(r, "name"));
        v.igual("… username", "ana", data(r, "username"));
        v.igual("… role", "user", data(r, "role"));
        esperar(v, "admin consulta conta inexistente", req("getuser", "token", admin, "username", "ninguem"),
                404, "Usuário não encontrado");
        r = esperar(v, "user consultando outra conta recebe a própria", req("getuser", "token", ana, "username", "bob"),
                200, "Usuário encontrado com sucesso");
        v.igual("… username é o do token", "ana", data(r, "username"));
        esperar(v, "admin altera name de outra conta", req("updateusername", "token", admin, "username", "carla", "name", "Carla Dias"),
                200, "Nome do usuário atualizado com sucesso");
        r = requisitar(req("getuser", "token", admin, "username", "carla"));
        v.igual("… name mudou", "Carla Dias", data(r, "name"));
        v.igual("… username não foi renomeado", "carla", data(r, "username"));
        esperar(v, "admin com name inválido", req("updateusername", "token", admin, "username", "carla", "name", "C4rla"),
                400, "Nome do usuário fora do padrão");
        esperar(v, "admin altera conta inexistente", req("updateusername", "token", admin, "username", "ninguem", "name", "Nome"),
                404, "Usuário não encontrado");
        esperar(v, "user alterando name de outra conta", req("updateusername", "token", ana, "username", "carla", "name", "Hacker"),
                401, "Operação não autorizada");
        esperar(v, "user excluindo outra conta", req("deleteuser", "token", ana, "username", "carla"),
                401, "Operação não autorizada");
        String carla = token(requisitar(req("login", "username", "carla", "password", SENHA)));
        esperar(v, "admin exclui outra conta", req("deleteuser", "token", admin, "username", "carla"),
                200, "Usuário deletado com sucesso");
        esperar(v, "sessão da conta excluída foi invalidada", req("getuser", "token", carla), 401, SESSAO);
        r = esperar(v, "sessão do admin continua a mesma", req("getuser", "token", admin), 200, "Usuário encontrado com sucesso");
        v.igual("… e continua admin", "admin", data(r, "role"));
        esperar(v, "admin exclui conta inexistente", req("deleteuser", "token", admin, "username", "carla"),
                404, "Usuário não encontrado");

        v.grupo("EP2 — createvaga / getvagas");
        esperar(v, "user não cria vaga", req("createvaga", "token", ana, "spotName", "A1", "floor", 1, "type", "combustao"),
                401, APENAS_ADMIN);
        esperar(v, "sem token", req("createvaga", "spotName", "A1", "floor", 1, "type", "combustao"), 400, TOKEN_AUSENTE);
        esperar(v, "token inválido", req("createvaga", "token", "abc", "spotName", "A1", "floor", 1, "type", "combustao"),
                401, SESSAO);
        r = esperar(v, "admin cria A1", req("createvaga", "token", admin, "spotName", "A1", "floor", 1, "type", "combustao"),
                201, "Vaga criada com sucesso");
        v.verdadeiro("… com data null", r.containsKey("data") && r.get("data") == null);
        esperar(v, "nome duplicado", req("createvaga", "token", admin, "spotName", "A1", "floor", 2, "type", "eletrico"),
                409, "Já existe uma vaga com este nome.");
        esperar(v, "nome duplicado com outra caixa", req("createvaga", "token", admin, "spotName", "a1", "floor", 2, "type", "eletrico"),
                409, "Já existe uma vaga com este nome.");
        esperar(v, "nome com 5 caracteres", req("createvaga", "token", admin, "spotName", "ABCDE", "floor", 1, "type", "combustao"),
                400, CAMPOS);
        esperar(v, "floor não inteiro", req("createvaga", "token", admin, "spotName", "Z1", "floor", "x", "type", "combustao"),
                400, CAMPOS);
        esperar(v, "floor 1.5", req("createvaga", "token", admin, "spotName", "Z1", "floor", 1.5, "type", "combustao"),
                400, CAMPOS);
        esperar(v, "type inválido", req("createvaga", "token", admin, "spotName", "Z1", "floor", 1, "type", "diesel"),
                400, CAMPOS);
        esperar(v, "sem floor", req("createvaga", "token", admin, "spotName", "Z1", "type", "combustao"), 400, CAMPOS);
        requisitar(req("createvaga", "token", admin, "spotName", "B1", "floor", 1, "type", "combustao"));
        requisitar(req("createvaga", "token", admin, "spotName", "E1", "floor", 2, "type", "eletrico"));
        r = esperar(v, "user lista vagas", req("getvagas", "token", ana), 200, "Vagas obtidas com sucesso");
        List<Map<String, Object>> vagas = lista(r);
        v.igual("3 vagas", 3, vagas.size());
        Map<String, Object> a1 = vaga(vagas, "A1");
        v.igual("status inicial atribuído pelo servidor", "disponivel", a1 == null ? null : a1.get("status"));
        v.verdadeiro("cada vaga traz floor, spotName, status, licensePlate e type",
                a1 != null && a1.keySet().containsAll(List.of("floor", "spotName", "status", "licensePlate", "type")));
        v.igual("floor como inteiro", 1L, a1 == null ? null : a1.get("floor"));
        esperar(v, "admin lista vagas", req("getvagas", "token", admin), 200, "Vagas obtidas com sucesso");
        esperar(v, "getvagas sem token", req("getvagas"), 400, TOKEN_AUSENTE);

        v.grupo("EP2 — veículos: perfil e isolamento");
        r = esperar(v, "lista vazia", req("getvehicles", "token", ana), 200, "Veículos obtidos com sucesso");
        v.verdadeiro("… data é []", r.get("data") instanceof List<?> l && l.isEmpty());
        esperar(v, "admin não cria veículo", req("createvehicle", "token", admin, "brand", "Fiat", "model", "Uno",
                "type", "combustao", "licensePlate", "ADM0001"), 401, APENAS_USER);
        esperar(v, "admin não lista veículos próprios", req("getvehicles", "token", admin), 401, APENAS_USER);
        esperar(v, "admin não atualiza veículo", req("updatevehicle", "token", admin, "licensePlate", "ABC1234", "brand", "X"),
                401, APENAS_USER);
        esperar(v, "admin recusado continua com sessão válida", req("getuser", "token", admin), 200, "Usuário encontrado com sucesso");
        esperar(v, "createvehicle sem token", req("createvehicle", "brand", "Fiat", "type", "combustao", "licensePlate", "AAA0001"),
                400, TOKEN_AUSENTE);
        esperar(v, "createvehicle com token inválido", req("createvehicle", "token", UUID.randomUUID().toString(),
                "brand", "Fiat", "type", "combustao", "licensePlate", "AAA0001"), 401, SESSAO);
        r = esperar(v, "ana cadastra ABC1234", req("createvehicle", "token", ana, "brand", "chevrolet", "model", "onix",
                "type", "combustao", "licensePlate", "ABC1234", "ownerUsername", "bob", "username", "bob"),
                201, "Veículo criado com sucesso");
        v.verdadeiro("… data null", r.containsKey("data") && r.get("data") == null);
        r = esperar(v, "placa duplicada (outra conta)", req("createvehicle", "token", bob, "brand", "fiat", "model", "uno",
                "type", "combustao", "licensePlate", "ABC1234"), 409, "Placa já cadastrada.");
        v.falso("… sem revelar o proprietário", String.valueOf(r).contains("ana"));
        esperar(v, "placa duplicada em minúsculas", req("createvehicle", "token", bob, "brand", "fiat",
                "type", "combustao", "licensePlate", "abc1234"), 409, "Placa já cadastrada.");
        esperar(v, "placa com 4 caracteres", req("createvehicle", "token", ana, "brand", "fiat", "type", "combustao",
                "licensePlate", "AB12"), 400, CAMPOS);
        esperar(v, "placa com símbolo", req("createvehicle", "token", ana, "brand", "fiat", "type", "combustao",
                "licensePlate", "ABC-123"), 400, CAMPOS);
        esperar(v, "brand null", req("createvehicle", "token", ana, "brand", null, "type", "combustao",
                "licensePlate", "AAA0001"), 400, CAMPOS);
        esperar(v, "brand com 21 caracteres", req("createvehicle", "token", ana, "brand", "b".repeat(21), "type", "combustao",
                "licensePlate", "AAA0001"), 400, CAMPOS);
        esperar(v, "model com 21 caracteres", req("createvehicle", "token", ana, "brand", "fiat", "model", "m".repeat(21),
                "type", "combustao", "licensePlate", "AAA0001"), 400, CAMPOS);
        esperar(v, "type inválido", req("createvehicle", "token", ana, "brand", "fiat", "type", "hibrido",
                "licensePlate", "AAA0001"), 400, CAMPOS);
        esperar(v, "sem placa", req("createvehicle", "token", ana, "brand", "fiat", "type", "combustao"), 400, CAMPOS);
        esperar(v, "model null é aceito", req("createvehicle", "token", ana, "brand", "tesla", "model", null, "type", "eletrico",
                "licensePlate", "ELE0001"), 201, "Veículo criado com sucesso");
        esperar(v, "bob cadastra placa em minúsculas", req("createvehicle", "token", bob, "brand", "fiat", "model", "uno",
                "type", "combustao", "licensePlate", "xyz9876"), 201, "Veículo criado com sucesso");

        r = esperar(v, "ana lista os próprios", req("getvehicles", "token", ana), 200, "Veículos obtidos com sucesso");
        List<Map<String, Object>> veiculos = lista(r);
        v.igual("ana tem 2 veículos", 2, veiculos.size());
        v.verdadeiro("ownerUsername do payload foi ignorado (ABC1234 é da ana)", placas(veiculos).contains("ABC1234"));
        v.verdadeiro("itens trazem brand, model, type e licensePlate",
                veiculos.stream().allMatch(m -> m.keySet().equals(java.util.Set.of("brand", "model", "type", "licensePlate"))));
        v.falso("resposta não expõe ownerUsername", String.valueOf(r).contains("ownerUsername"));
        r = requisitar(req("getvehicles", "token", bob));
        v.igual("bob vê só o dele", List.of("XYZ9876"), placas(lista(r)));

        esperar(v, "bob consulta veículo da ana", req("getvehicle", "token", bob, "licensePlate", "ABC1234"), 404, VEICULO_404);
        esperar(v, "consulta de placa inexistente (mesma resposta)", req("getvehicle", "token", bob, "licensePlate", "ZZZ9999"),
                404, VEICULO_404);
        r = esperar(v, "ana consulta o próprio", req("getvehicle", "token", ana, "licensePlate", "abc1234"),
                200, "Veículo encontrado com sucesso");
        v.igual("… brand", "chevrolet", data(r, "brand"));
        r = esperar(v, "admin consulta veículo de terceiro", req("getvehicle", "token", admin, "licensePlate", "ABC1234"),
                200, "Veículo encontrado com sucesso");
        v.igual("… model", "onix", data(r, "model"));
        esperar(v, "getvehicle sem placa", req("getvehicle", "token", ana), 400, CAMPOS);
        esperar(v, "bob tenta atualizar veículo da ana", req("updatevehicle", "token", bob, "licensePlate", "ABC1234",
                "brand", "roubado"), 404, VEICULO_404);
        esperar(v, "bob tenta excluir veículo da ana", req("deletevehicle", "token", bob, "licensePlate", "ABC1234"),
                404, VEICULO_404);
        v.igual("… o veículo da ana continua intacto", "chevrolet",
                data(requisitar(req("getvehicle", "token", ana, "licensePlate", "ABC1234")), "brand"));

        v.grupo("EP2 — updatevehicle");
        esperar(v, "altera só o model", req("updatevehicle", "token", ana, "licensePlate", "ABC1234", "model", "onix plus"),
                200, "Veículo atualizado com sucesso");
        r = requisitar(req("getvehicle", "token", ana, "licensePlate", "ABC1234"));
        v.igual("… model novo", "onix plus", data(r, "model"));
        v.igual("… brand preservada (campo omitido)", "chevrolet", data(r, "brand"));
        esperar(v, "model null limpa o modelo", req("updatevehicle", "token", ana, "licensePlate", "ABC1234", "model", null),
                200, "Veículo atualizado com sucesso");
        r = requisitar(req("getvehicle", "token", ana, "licensePlate", "ABC1234"));
        v.verdadeiro("… model ficou null", r.get("data") instanceof Map<?, ?> d && d.containsKey("model") && d.get("model") == null);
        esperar(v, "brand null", req("updatevehicle", "token", ana, "licensePlate", "ABC1234", "brand", null), 400, CAMPOS);
        esperar(v, "type null", req("updatevehicle", "token", ana, "licensePlate", "ABC1234", "type", null), 400, CAMPOS);
        esperar(v, "brand válida + type inválido não grava nada", req("updatevehicle", "token", ana, "licensePlate", "ABC1234",
                "brand", "gm", "type", "xyz"), 400, CAMPOS);
        v.igual("… brand não mudou", "chevrolet",
                data(requisitar(req("getvehicle", "token", ana, "licensePlate", "ABC1234")), "brand"));
        esperar(v, "nenhum campo para alterar", req("updatevehicle", "token", ana, "licensePlate", "ABC1234"), 400, CAMPOS);
        esperar(v, "placa inexistente", req("updatevehicle", "token", ana, "licensePlate", "QQQ1111", "brand", "x"), 404, VEICULO_404);
        esperar(v, "volta o model", req("updatevehicle", "token", ana, "licensePlate", "ABC1234", "model", "onix"),
                200, "Veículo atualizado com sucesso");

        v.grupo("EP2 — estacionar, findvehicle e conflitos com vaga");
        esperar(v, "findvehicle de veículo não estacionado", req("findvehicle", "token", ana, "licensePlate", "ABC1234"),
                404, "Veículo não está estacionado em nenhuma vaga.");
        esperar(v, "findvehicle sem placa", req("findvehicle", "token", ana), 400, CAMPOS);
        esperar(v, "ana estaciona ABC1234 na A1", req("updatevaga", "token", ana, "spotName", "A1", "status", "ocupada",
                "licensePlate", "ABC1234"), 200, "Vaga atualizada com sucesso");
        Map<String, Object> a1Ocupada = vaga(lista(requisitar(req("getvagas", "token", bob))), "A1");
        v.igual("A1 ficou ocupada", "ocupada", a1Ocupada == null ? null : a1Ocupada.get("status"));
        v.igual("A1 guarda a placa", "ABC1234", a1Ocupada == null ? null : a1Ocupada.get("licensePlate"));
        r = esperar(v, "ana localiza o próprio veículo", req("findvehicle", "token", ana, "licensePlate", "ABC1234"),
                200, "Veiculo localizado com sucesso");
        v.igual("… floor", 1L, data(r, "floor"));
        v.igual("… spotName", "A1", data(r, "spotName"));
        v.igual("… status", "ocupada", data(r, "status"));
        esperar(v, "bob não localiza veículo da ana", req("findvehicle", "token", bob, "licensePlate", "ABC1234"), 404, VEICULO_404);
        esperar(v, "admin localiza qualquer veículo", req("findvehicle", "token", admin, "licensePlate", "ABC1234"),
                200, "Veiculo localizado com sucesso");
        esperar(v, "tipo incompatível (elétrico em vaga a combustão)", req("updatevaga", "token", ana, "spotName", "B1",
                "status", "ocupada", "licensePlate", "ELE0001"), 409, "O tipo do veículo é incompatível com o tipo da vaga.");
        esperar(v, "vaga já ocupada", req("updatevaga", "token", ana, "spotName", "A1", "status", "ocupada",
                "licensePlate", "ELE0001"), 409, "A vaga não está disponível.");
        esperar(v, "veículo já estacionado em outra vaga", req("updatevaga", "token", ana, "spotName", "B1",
                "licensePlate", "ABC1234"), 409, "Veículo já está estacionado em outra vaga.");
        esperar(v, "bob estacionando veículo da ana", req("updatevaga", "token", bob, "spotName", "B1", "status", "ocupada",
                "licensePlate", "ABC1234"), 404, VEICULO_404);
        esperar(v, "ocupada sem placa", req("updatevaga", "token", ana, "spotName", "B1", "status", "ocupada"), 400, CAMPOS);
        esperar(v, "mudar type com veículo na vaga", req("updatevehicle", "token", ana, "licensePlate", "ABC1234",
                "type", "eletrico"), 409, VEICULO_EM_VAGA);
        esperar(v, "mudar brand com veículo na vaga é permitido", req("updatevehicle", "token", ana, "licensePlate", "ABC1234",
                "brand", "Chevrolet"), 200, "Veículo atualizado com sucesso");
        esperar(v, "user não exclui veículo estacionado", req("deletevehicle", "token", ana, "licensePlate", "ABC1234"),
                409, VEICULO_EM_VAGA);
        esperar(v, "admin não exclui veículo estacionado", req("deletevehicle", "token", admin, "licensePlate", "ABC1234"),
                409, VEICULO_EM_VAGA);
        esperar(v, "admin não exclui conta com veículo estacionado", req("deleteuser", "token", admin, "username", "ana"),
                409, "O usuário possui veículo estacionado. Libere a vaga antes desta operação.");
        esperar(v, "admin não renomeia vaga ocupada", req("updatevaga", "token", admin, "spotName", "A1", "newSpotName", "A9"),
                409, "O nome e o andar só podem ser alterados com a vaga disponível ou fora de serviço.");
        esperar(v, "admin não põe vaga ocupada fora de serviço", req("updatevaga", "token", admin, "spotName", "A1",
                "status", "fora_de_servico"), 409, "A vaga só pode ficar fora de serviço quando estiver disponível.");
        esperar(v, "admin não exclui vaga ocupada", req("deletevaga", "token", admin, "spotName", "A1"),
                409, "Vagas ocupadas não podem ser excluídas.");
        esperar(v, "user não renomeia vaga", req("updatevaga", "token", ana, "spotName", "B1", "newSpotName", "B2"),
                401, "Apenas administradores podem alterar o nome ou o andar da vaga.");
        esperar(v, "user não altera floor", req("updatevaga", "token", ana, "spotName", "B1", "floor", 5),
                401, "Apenas administradores podem alterar o nome ou o andar da vaga.");
        esperar(v, "user não põe vaga fora de serviço", req("updatevaga", "token", ana, "spotName", "B1", "status", "fora_de_servico"),
                401, "Apenas administradores podem colocar a vaga fora de serviço.");
        esperar(v, "admin não estaciona veículo", req("updatevaga", "token", admin, "spotName", "B1", "licensePlate", "XYZ9876"),
                401, "Administradores não podem estacionar veículos.");
        esperar(v, "bob não libera a vaga que a ana ocupou", req("updatevaga", "token", bob, "spotName", "A1", "status", "disponivel"),
                401, "Somente quem ocupou a vaga ou um administrador pode liberá-la.");
        esperar(v, "ana libera com licensePlate null", req("updatevaga", "token", ana, "spotName", "A1", "licensePlate", null),
                200, "Vaga atualizada com sucesso");
        Map<String, Object> a1Livre = vaga(lista(requisitar(req("getvagas", "token", ana))), "A1");
        v.igual("A1 voltou a disponível", "disponivel", a1Livre == null ? null : a1Livre.get("status"));
        v.verdadeiro("… sem placa", a1Livre != null && a1Livre.containsKey("licensePlate") && a1Livre.get("licensePlate") == null);
        requisitar(req("updatevaga", "token", ana, "spotName", "A1", "status", "ocupada", "licensePlate", "ABC1234"));
        esperar(v, "admin libera vaga ocupada por outra pessoa", req("updatevaga", "token", admin, "spotName", "A1",
                "status", "disponivel"), 200, "Vaga atualizada com sucesso");
        esperar(v, "veículo liberado pode ser excluído… (mas não agora)", req("findvehicle", "token", ana, "licensePlate", "ABC1234"),
                404, "Veículo não está estacionado em nenhuma vaga.");

        v.grupo("EP2 — renomear vaga e buscar pela placa");
        esperar(v, "admin renomeia A1 → A2 e muda o andar", req("updatevaga", "token", admin, "spotName", "A1",
                "newSpotName", "A2", "floor", 3), 200, "Vaga atualizada com sucesso");
        esperar(v, "nome antigo não existe mais", req("updatevaga", "token", admin, "spotName", "A1", "floor", 1),
                404, "Vaga não encontrada.");
        esperar(v, "renomear para nome existente", req("updatevaga", "token", admin, "spotName", "B1", "newSpotName", "A2"),
                409, "Já existe uma vaga com este nome.");
        esperar(v, "newSpotName inválido", req("updatevaga", "token", admin, "spotName", "B1", "newSpotName", "B-1"),
                400, CAMPOS);
        esperar(v, "ana estaciona na vaga renomeada", req("updatevaga", "token", ana, "spotName", "A2", "status", "ocupada",
                "licensePlate", "ABC1234"), 200, "Vaga atualizada com sucesso");
        r = esperar(v, "findvehicle mostra o nome novo", req("findvehicle", "token", ana, "licensePlate", "ABC1234"),
                200, "Veiculo localizado com sucesso");
        v.igual("… spotName A2", "A2", data(r, "spotName"));
        v.igual("… floor 3", 3L, data(r, "floor"));

        v.grupo("EP2 — fora de serviço, updatevaga e deletevaga");
        esperar(v, "admin põe B1 fora de serviço", req("updatevaga", "token", admin, "spotName", "B1", "status", "fora_de_servico"),
                200, "Vaga atualizada com sucesso");
        esperar(v, "não estaciona em vaga fora de serviço", req("updatevaga", "token", bob, "spotName", "B1", "status", "ocupada",
                "licensePlate", "XYZ9876"), 409, "A vaga não está disponível.");
        esperar(v, "user não tira vaga de fora de serviço", req("updatevaga", "token", bob, "spotName", "B1", "status", "disponivel"),
                401, APENAS_ADMIN);
        esperar(v, "admin renomeia vaga fora de serviço", req("updatevaga", "token", admin, "spotName", "B1", "newSpotName", "B2"),
                200, "Vaga atualizada com sucesso");
        esperar(v, "admin devolve B2 ao serviço", req("updatevaga", "token", admin, "spotName", "B2", "status", "disponivel"),
                200, "Vaga atualizada com sucesso");
        esperar(v, "updatevaga sem nada para alterar", req("updatevaga", "token", admin, "spotName", "B2"), 400, CAMPOS);
        esperar(v, "updatevaga sem spotName", req("updatevaga", "token", admin, "status", "disponivel"), 400, CAMPOS);
        esperar(v, "updatevaga de vaga inexistente", req("updatevaga", "token", admin, "spotName", "Q9", "status", "disponivel"),
                404, "Vaga não encontrada.");
        esperar(v, "status inválido", req("updatevaga", "token", admin, "spotName", "B2", "status", "reservada"), 400, CAMPOS);
        esperar(v, "user não exclui vaga", req("deletevaga", "token", ana, "spotName", "E1"), 401, APENAS_ADMIN);
        esperar(v, "excluir vaga inexistente", req("deletevaga", "token", admin, "spotName", "Q9"), 404, "Vaga não encontrada.");
        esperar(v, "admin exclui E1", req("deletevaga", "token", admin, "spotName", "E1"), 200, "Vaga removida com sucesso");
        v.igual("restam 2 vagas", 2, lista(requisitar(req("getvagas", "token", ana))).size());

        v.grupo("EP2 — exclusões pelo admin e cascata");
        requisitar(req("createvehicle", "token", ana, "brand", "vw", "type", "combustao", "licensePlate", "DEL0001"));
        esperar(v, "admin exclui veículo de terceiro", req("deletevehicle", "token", admin, "licensePlate", "DEL0001"),
                200, "Veículo removido com sucesso");
        esperar(v, "… e ele some para o dono", req("getvehicle", "token", ana, "licensePlate", "DEL0001"), 404, VEICULO_404);
        esperar(v, "admin exclui bob (sem veículo estacionado)", req("deleteuser", "token", admin, "username", "bob"),
                200, "Usuário deletado com sucesso");
        esperar(v, "token do bob foi invalidado", req("getvehicles", "token", bob), 401, SESSAO);
        esperar(v, "veículos do bob foram removidos em cascata", req("getvehicle", "token", admin, "licensePlate", "XYZ9876"),
                404, VEICULO_404);
        esperar(v, "a placa ficou livre de novo", req("createvehicle", "token", ana, "brand", "fiat", "type", "combustao",
                "licensePlate", "XYZ9876"), 201, "Veículo criado com sucesso");

        v.grupo("EP2 — concorrência");
        AtomicInteger criados = new AtomicInteger();
        rodarJuntos(20, i -> {
            if (status(requisitar(req("createvehicle", "token", ana, "brand", "corrida", "type", "combustao",
                    "licensePlate", "CON0001"))) == 201) {
                criados.incrementAndGet();
            }
        });
        v.igual("20 cadastros simultâneos da mesma placa: 1 criado", 1, criados.get());
        for (int i = 1; i <= 15; i++) {
            requisitar(req("createvaga", "token", admin, "spotName", "C" + i, "floor", 4, "type", "combustao"));
        }
        AtomicInteger estacionou = new AtomicInteger();
        rodarJuntos(15, i -> {
            if (status(requisitar(req("updatevaga", "token", ana, "spotName", "C" + (i + 1), "status", "ocupada",
                    "licensePlate", "CON0001"))) == 200) {
                estacionou.incrementAndGet();
            }
        });
        v.igual("mesmo veículo em 15 vagas ao mesmo tempo: só 1 deu certo", 1, estacionou.get());
        long ocupadasComCon = lista(requisitar(req("getvagas", "token", admin))).stream()
                .filter(m -> "CON0001".equals(m.get("licensePlate"))).count();
        v.igual("… e só 1 vaga guarda essa placa", 1L, ocupadasComCon);

        v.grupo("EP2 — regressão EP1 com o admin");
        esperar(v, "logout do admin", req("logout", "token", admin), 200, "Usuário deslogado com sucesso");
        esperar(v, "token do admin não vale mais", req("getvagas", "token", admin), 401, SESSAO);
    }

    private static void persistencia(Verifica v, Path arquivo) throws Exception {
        v.grupo("EP2 — persistência após reinício");
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        v.verdadeiro("ownerUsername gravado no arquivo", conteudo.contains("\"ownerUsername\":\"ana\""));
        v.falso("senha não aparece no arquivo", conteudo.contains(SENHA) || conteudo.contains("Admin123!"));
        ServidorContas outro = new ServidorContas(0, arquivo, new RegistroLog(false));
        outro.iniciar();
        porta = outro.getPorta();
        try {
            String t = token(requisitar(req("login", "username", "ana", "password", SENHA)));
            String adm = token(requisitar(req("login", "username", "admin", "password", "Admin123!")));
            v.igual("role sobrevive", "user", data(requisitar(req("getuser", "token", t)), "role"));
            v.igual("admin criado no 1º início continua admin", "admin", data(requisitar(req("getuser", "token", adm)), "role"));
            v.igual("adm2 continua admin", "admin",
                    data(requisitar(req("getuser", "token", adm, "username", "adm2")), "role"));
            Map<String, Object> r = requisitar(req("findvehicle", "token", t, "licensePlate", "ABC1234"));
            v.igual("vínculo placa↔vaga sobrevive (vaga renomeada)", "A2", data(r, "spotName"));
            v.igual("… andar 3", 3L, data(r, "floor"));
            List<String> placas = placas(lista(requisitar(req("getvehicles", "token", t))));
            v.verdadeiro("veículos continuam da ana", placas.containsAll(List.of("ABC1234", "ELE0001", "XYZ9876", "CON0001")));
            esperar(v, "ocupante continua sendo a ana (admin ainda libera)", req("updatevaga", "token", adm, "spotName", "A2",
                    "status", "disponivel"), 200, "Vaga atualizada com sucesso");
            v.igual("vagas sobrevivem", 17, lista(requisitar(req("getvagas", "token", t))).size());
        } finally {
            outro.parar();
        }
    }

    private static void falhaDeGravacao(Verifica v) throws Exception {
        v.grupo("EP2 — falha de gravação não confirma sucesso");
        Path pasta = Files.createTempDirectory("lucaschristen-sd-ep2-falha");
        Path arquivo = pasta.resolve("banco.json");
        ServidorContas servidor = new ServidorContas(0, arquivo, new RegistroLog(false));
        servidor.iniciar();
        porta = servidor.getPorta();
        try {
            String adm = token(requisitar(req("login", "username", "admin", "password", "Admin123!")));
            Files.delete(arquivo);
            Files.createDirectories(arquivo.resolve("bloqueio"));
            esperar(v, "gravação impossível → 500", req("createvaga", "token", adm, "spotName", "F1", "floor", 1, "type", "eletrico"),
                    500, "Erro interno no servidor.");
            v.igual("… e a vaga não existe em memória", 0, lista(requisitar(req("getvagas", "token", adm))).size());
        } finally {
            servidor.parar();
        }
    }

    private static void importacaoEp1(Verifica v) throws Exception {
        v.grupo("EP2 — importa usuarios.json da EP1");
        Path pasta = Files.createTempDirectory("lucaschristen-sd-ep2-legado");
        Path origem = Files.createTempDirectory("lucaschristen-sd-ep1");
        ServidorContas ep1 = new ServidorContas(0, origem.resolve("banco.json"), new RegistroLog(false));
        ep1.iniciar();
        porta = ep1.getPorta();
        requisitar(req("register", "name", "Legado", "username", "legado", "password", SENHA));
        ep1.parar();
        String hash = Files.readString(origem.resolve("banco.json"), StandardCharsets.UTF_8)
                .replaceAll("(?s).*\"username\":\"legado\",\"password\":\"([^\"]+)\".*", "$1");
        Files.writeString(pasta.resolve("usuarios.json"),
                "[{\"name\":\"Legado\",\"username\":\"legado\",\"password\":\"" + hash + "\"}]\n", StandardCharsets.UTF_8);
        ServidorContas servidor = new ServidorContas(0, pasta.resolve("banco.json"), new RegistroLog(false));
        servidor.iniciar();
        porta = servidor.getPorta();
        try {
            Map<String, Object> r = requisitar(req("login", "username", "legado", "password", SENHA));
            v.igual("usuário da EP1 loga", 200L, status(r));
            v.igual("… como role user", "user", data(requisitar(req("getuser", "token", token(r))), "role"));
            v.igual("admin inicial também foi criado", 200L,
                    status(requisitar(req("login", "username", "admin", "password", "Admin123!"))));
        } finally {
            servidor.parar();
        }
    }

    private static Map<String, Object> req(String metodo, Object... chavesEValores) {
        Map<String, Object> dados = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            dados.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", metodo);
        m.put("data", dados);
        return m;
    }

    private static Map<String, Object> esperar(Verifica v, String descricao, Map<String, Object> requisicao,
                                               int status, String mensagem) throws IOException {
        Map<String, Object> r = requisitar(requisicao);
        boolean ok = Long.valueOf(status).equals(r.get("statusCode")) && mensagem.equals(r.get("message"))
                && r.containsKey("data");
        v.verdadeiro(descricao + " → " + status + " \"" + mensagem + "\"" + (ok ? "" : "   recebido: " + Json.escrever(r)), ok);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requisitar(Map<String, Object> requisicao) throws IOException {
        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(10_000);
            s.getOutputStream().write((Json.escrever(requisicao) + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                b.write(buf, 0, n);
            }
            return (Map<String, Object>) Json.ler(b.toString(StandardCharsets.UTF_8).strip());
        }
    }

    private static long status(Map<String, Object> r) {
        return (Long) r.get("statusCode");
    }

    private static Object data(Map<String, Object> r, String campo) {
        return r.get("data") instanceof Map<?, ?> d ? d.get(campo) : null;
    }

    private static String token(Map<String, Object> r) {
        return (String) data(r, "token");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> r) {
        List<Map<String, Object>> saida = new ArrayList<>();
        if (r.get("data") instanceof List<?> l) {
            for (Object o : l) {
                saida.add((Map<String, Object>) o);
            }
        }
        return saida;
    }

    private static Map<String, Object> vaga(List<Map<String, Object>> vagas, String nome) {
        return vagas.stream().filter(m -> nome.equals(m.get("spotName"))).findFirst().orElse(null);
    }

    private static List<String> placas(List<Map<String, Object>> veiculos) {
        return veiculos.stream().map(m -> (String) m.get("licensePlate")).toList();
    }

    private interface Tarefa {
        void rodar(int i) throws Exception;
    }

    private static void rodarJuntos(int quantidade, Tarefa tarefa) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        List<Throwable> erros = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < quantidade; i++) {
            int n = i;
            pool.execute(() -> {
                try {
                    largada.await();
                    tarefa.rodar(n);
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        if (!erros.isEmpty()) {
            throw new IllegalStateException("falhas nas threads: " + erros);
        }
    }
}
