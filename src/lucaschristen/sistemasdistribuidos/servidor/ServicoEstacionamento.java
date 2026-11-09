package lucaschristen.sistemasdistribuidos.servidor;

import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import lucaschristen.sistemasdistribuidos.protocolo.Validacao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class ServicoEstacionamento {
    private enum Perfil { QUALQUER, ADMIN, USER }

    private final Banco banco;
    private final Autenticador autenticador;
    private final RegistroLog log;

    public ServicoEstacionamento(Banco banco, Sessoes sessoes, RegistroLog log) {
        this.banco = banco;
        this.autenticador = new Autenticador(sessoes);
        this.log = log;
    }

    public Resposta createVaga(Map<String, Object> dados) {
        return alterar(dados, Perfil.ADMIN, (e, eu) -> {
            String nome = textoLimpo(dados, "spotName");
            Integer andar = inteiro(Dados.campo(dados, "floor"));
            String tipo = textoLimpo(dados, "type");
            if (!Validacao.nomeVagaValido(nome) || andar == null || !Validacao.tipoValido(tipo)) {
                return camposInvalidos();
            }
            if (e.vaga(nome).isPresent()) {
                return Resposta.erro(CONFLITO, MSG_VAGA_DUPLICADA);
            }
            e.gravarVaga(new Vaga(nome, andar, tipo, VAGA_DISPONIVEL, null, null));
            log.info("Vaga criada: " + nome + " (andar " + andar + ", " + tipo + ") por " + eu.username());
            return Resposta.criado(MSG_VAGA_CRIADA, null);
        });
    }

    public Resposta getVagas(Map<String, Object> dados) {
        return ler(dados, Perfil.QUALQUER, (e, eu) -> {
            List<Map<String, Object>> lista = new ArrayList<>();
            for (Vaga v : e.vagas()) {
                lista.add(v.paraProtocolo());
            }
            return Resposta.ok(MSG_VAGAS_OBTIDAS, lista);
        });
    }

    public Resposta updateVaga(Map<String, Object> dados) {
        return alterar(dados, Perfil.QUALQUER, (e, eu) -> {
            String nome = textoLimpo(dados, "spotName");
            if (nome == null) {
                return camposInvalidos();
            }
            boolean temNovoNome = Dados.campo(dados, "newSpotName") != null;
            boolean temAndar = Dados.campo(dados, "floor") != null;
            boolean temStatus = Dados.campo(dados, "status") != null;
            boolean temPlaca = Dados.temCampo(dados, "licensePlate");
            String novoNome = textoLimpo(dados, "newSpotName");
            Integer andar = inteiro(Dados.campo(dados, "floor"));
            String status = textoLimpo(dados, "status");
            Object placaBruta = Dados.campo(dados, "licensePlate");
            String placa = placaBruta == null ? null : Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));

            if ((temNovoNome && !Validacao.nomeVagaValido(novoNome)) || (temAndar && andar == null)
                    || (temStatus && !Validacao.statusVagaValido(status)) || (placaBruta != null && placa == null)) {
                return camposInvalidos();
            }
            if (!temNovoNome && !temAndar && !temStatus && !temPlaca) {
                return camposInvalidos();
            }

            Optional<Vaga> encontrada = e.vaga(nome);
            if (encontrada.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VAGA_NAO_ENCONTRADA);
            }
            Vaga vaga = encontrada.get();
            boolean admin = eu.admin();

            boolean estacionar = placa != null;
            boolean colocarForaDeServico = VAGA_FORA_DE_SERVICO.equals(status);
            boolean liberar = VAGA_DISPONIVEL.equals(status) || (temPlaca && placaBruta == null && !temStatus);
            if (VAGA_OCUPADA.equals(status) && placa == null) {
                return camposInvalidos();
            }
            if (estacionar && temStatus && !VAGA_OCUPADA.equals(status)) {
                return camposInvalidos();
            }

            if (admin && estacionar) {
                return Resposta.erro(NAO_AUTORIZADO, MSG_VAGA_ADMIN_NAO_ESTACIONA);
            }
            if (!admin && (temNovoNome || temAndar)) {
                return Resposta.erro(NAO_AUTORIZADO, MSG_VAGA_USER_NAO_RENOMEIA);
            }
            if (!admin && colocarForaDeServico) {
                return Resposta.erro(NAO_AUTORIZADO, MSG_VAGA_USER_FORA_DE_SERVICO);
            }

            Vaga nova = vaga;
            boolean mudaNome = temNovoNome && !novoNome.equals(vaga.spotName());
            boolean mudaAndar = temAndar && andar != vaga.floor();
            if (mudaNome || mudaAndar) {
                if (vaga.ocupada()) {
                    return Resposta.erro(CONFLITO, MSG_VAGA_EM_USO_NAO_ALTERA);
                }
                if (mudaNome) {
                    Optional<Vaga> outra = e.vaga(novoNome);
                    if (outra.isPresent() && !outra.get().spotName().equals(vaga.spotName())) {
                        return Resposta.erro(CONFLITO, MSG_VAGA_DUPLICADA);
                    }
                }
                nova = nova.renomeada(mudaNome ? novoNome : vaga.spotName(), mudaAndar ? andar : vaga.floor());
            }

            if (estacionar) {
                Optional<Veiculo> veiculo = e.veiculo(placa);
                if (veiculo.isEmpty() || !veiculo.get().ownerUsername().equals(eu.username())) {
                    return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ENCONTRADO);
                }
                if (!vaga.disponivel()) {
                    return Resposta.erro(CONFLITO, MSG_VAGA_NAO_DISPONIVEL);
                }
                if (!veiculo.get().type().equals(vaga.type())) {
                    return Resposta.erro(CONFLITO, MSG_VAGA_TIPO_INCOMPATIVEL);
                }
                if (e.vagaDoVeiculo(placa).isPresent()) {
                    return Resposta.erro(CONFLITO, MSG_VEICULO_JA_ESTACIONADO);
                }
                nova = nova.ocupadaCom(placa, eu.username());
            } else if (liberar) {
                if (vaga.ocupada()) {
                    if (!admin && !eu.username().equals(vaga.ocupadaPor())) {
                        return Resposta.erro(NAO_AUTORIZADO, MSG_VAGA_LIBERAR_NAO_AUTORIZADO);
                    }
                    nova = nova.comStatus(VAGA_DISPONIVEL);
                } else if (vaga.foraDeServico() && temStatus) {
                    if (!admin) {
                        return Resposta.erro(NAO_AUTORIZADO, MSG_APENAS_ADMIN);
                    }
                    nova = nova.comStatus(VAGA_DISPONIVEL);
                }
            } else if (colocarForaDeServico) {
                if (vaga.ocupada()) {
                    return Resposta.erro(CONFLITO, MSG_VAGA_FORA_DE_SERVICO_SO_DISPONIVEL);
                }
                nova = nova.comStatus(VAGA_FORA_DE_SERVICO);
            }

            if (!nova.equals(vaga)) {
                if (!nova.spotName().equals(vaga.spotName())) {
                    e.removerVaga(vaga.spotName());
                }
                e.gravarVaga(nova);
                log.info("Vaga " + vaga.spotName() + " atualizada por " + eu.username() + ": " + descrever(vaga)
                        + " → " + descrever(nova));
            }
            return Resposta.ok(MSG_VAGA_ATUALIZADA);
        });
    }

    public Resposta deleteVaga(Map<String, Object> dados) {
        return alterar(dados, Perfil.ADMIN, (e, eu) -> {
            String nome = textoLimpo(dados, "spotName");
            if (nome == null) {
                return camposInvalidos();
            }
            Optional<Vaga> vaga = e.vaga(nome);
            if (vaga.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VAGA_NAO_ENCONTRADA);
            }
            if (vaga.get().ocupada()) {
                return Resposta.erro(CONFLITO, MSG_VAGA_OCUPADA_NAO_EXCLUI);
            }
            e.removerVaga(vaga.get().spotName());
            log.info("Vaga removida: " + vaga.get().spotName() + " por " + eu.username());
            return Resposta.ok(MSG_VAGA_REMOVIDA);
        });
    }

    public Resposta createVehicle(Map<String, Object> dados) {
        return alterar(dados, Perfil.USER, (e, eu) -> {
            String placa = Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));
            String marca = textoLimpo(dados, "brand");
            String tipo = textoLimpo(dados, "type");
            Object modeloBruto = Dados.campo(dados, "model");
            String modelo = modeloOpcional(modeloBruto);
            if (placa == null || !Validacao.marcaValida(marca) || !Validacao.tipoValido(tipo)
                    || (modeloBruto != null && !(modeloBruto instanceof String)) || !Validacao.modeloValido(modelo)) {
                return camposInvalidos();
            }
            if (e.veiculo(placa).isPresent()) {
                return Resposta.erro(CONFLITO, MSG_PLACA_DUPLICADA);
            }
            e.gravarVeiculo(new Veiculo(placa, marca, modelo, tipo, eu.username()));
            log.info("Veículo cadastrado: " + placa + " de " + eu.username());
            return Resposta.criado(MSG_VEICULO_CRIADO, null);
        });
    }

    public Resposta getVehicles(Map<String, Object> dados) {
        return ler(dados, Perfil.USER, (e, eu) -> {
            List<Map<String, Object>> lista = new ArrayList<>();
            for (Veiculo v : e.veiculosDe(eu.username())) {
                lista.add(v.paraProtocolo());
            }
            return Resposta.ok(MSG_VEICULOS_OBTIDOS, lista);
        });
    }

    public Resposta getVehicle(Map<String, Object> dados) {
        return ler(dados, Perfil.QUALQUER, (e, eu) -> {
            String placa = Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));
            if (placa == null) {
                return camposInvalidos();
            }
            Optional<Veiculo> veiculo = visivel(e, eu, placa);
            if (veiculo.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ENCONTRADO);
            }
            return Resposta.ok(MSG_VEICULO_ENCONTRADO, veiculo.get().paraProtocolo());
        });
    }

    public Resposta updateVehicle(Map<String, Object> dados) {
        return alterar(dados, Perfil.USER, (e, eu) -> {
            String placa = Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));
            boolean temMarca = Dados.temCampo(dados, "brand");
            boolean temModelo = Dados.temCampo(dados, "model");
            boolean temTipo = Dados.temCampo(dados, "type");
            if (placa == null || (!temMarca && !temModelo && !temTipo)) {
                return camposInvalidos();
            }
            String marca = textoLimpo(dados, "brand");
            String tipo = textoLimpo(dados, "type");
            Object modeloBruto = Dados.campo(dados, "model");
            String modelo = modeloOpcional(modeloBruto);
            if ((temMarca && !Validacao.marcaValida(marca)) || (temTipo && !Validacao.tipoValido(tipo))
                    || (temModelo && ((modeloBruto != null && !(modeloBruto instanceof String))
                            || !Validacao.modeloValido(modelo)))) {
                return camposInvalidos();
            }
            Optional<Veiculo> encontrado = visivel(e, eu, placa);
            if (encontrado.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ENCONTRADO);
            }
            Veiculo atual = encontrado.get();
            Veiculo novo = atual.comDados(temMarca ? marca : atual.brand(), temModelo ? modelo : atual.model(),
                    temTipo ? tipo : atual.type());
            if (!novo.type().equals(atual.type()) && e.vagaDoVeiculo(placa).isPresent()) {
                return Resposta.erro(CONFLITO, MSG_VEICULO_EM_VAGA);
            }
            if (!novo.equals(atual)) {
                e.gravarVeiculo(novo);
            }
            return Resposta.ok(MSG_VEICULO_ATUALIZADO);
        });
    }

    public Resposta deleteVehicle(Map<String, Object> dados) {
        return alterar(dados, Perfil.QUALQUER, (e, eu) -> {
            String placa = Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));
            if (placa == null) {
                return camposInvalidos();
            }
            Optional<Veiculo> veiculo = visivel(e, eu, placa);
            if (veiculo.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ENCONTRADO);
            }
            if (e.vagaDoVeiculo(placa).isPresent()) {
                return Resposta.erro(CONFLITO, MSG_VEICULO_EM_VAGA);
            }
            e.removerVeiculo(placa);
            log.info("Veículo removido: " + placa + " por " + eu.username());
            return Resposta.ok(MSG_VEICULO_REMOVIDO);
        });
    }

    public Resposta findVehicle(Map<String, Object> dados) {
        return ler(dados, Perfil.QUALQUER, (e, eu) -> {
            String placa = Validacao.normalizarPlaca(Dados.texto(dados, "licensePlate"));
            if (placa == null) {
                return camposInvalidos();
            }
            if (visivel(e, eu, placa).isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ENCONTRADO);
            }
            Optional<Vaga> vaga = e.vagaDoVeiculo(placa);
            if (vaga.isEmpty()) {
                return Resposta.erro(NAO_ENCONTRADO, MSG_VEICULO_NAO_ESTACIONADO);
            }
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("floor", vaga.get().floor());
            resposta.put("spotName", vaga.get().spotName());
            resposta.put("status", vaga.get().status());
            return Resposta.ok(MSG_VEICULO_LOCALIZADO, resposta);
        });
    }

    private Resposta ler(Map<String, Object> dados, Perfil perfil, BiFunction<Banco.Estado, Usuario, Resposta> acao) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        return banco.ler(e -> executar(e, auth, perfil, acao));
    }

    private Resposta alterar(Map<String, Object> dados, Perfil perfil, BiFunction<Banco.Estado, Usuario, Resposta> acao) {
        Autenticador.Sessao auth = autenticador.sessao(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        return banco.alterar(e -> executar(e, auth, perfil, acao));
    }

    private static Resposta executar(Banco.Estado e, Autenticador.Sessao auth, Perfil perfil,
                                     BiFunction<Banco.Estado, Usuario, Resposta> acao) {
        Optional<Usuario> eu = e.usuario(auth.username());
        if (eu.isEmpty()) {
            return Autenticador.sessaoInvalida();
        }
        if (perfil == Perfil.ADMIN && !eu.get().admin()) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_APENAS_ADMIN);
        }
        if (perfil == Perfil.USER && eu.get().admin()) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_APENAS_USER);
        }
        return acao.apply(e, eu.get());
    }

    private static Optional<Veiculo> visivel(Banco.Estado e, Usuario eu, String placa) {
        return e.veiculo(placa).filter(v -> eu.admin() || v.ownerUsername().equals(eu.username()));
    }

    private static Resposta camposInvalidos() {
        return Resposta.erro(REQUISICAO_INVALIDA, MSG_CAMPOS_INVALIDOS);
    }

    private static String textoLimpo(Map<String, Object> dados, String campo) {
        String texto = Dados.texto(dados, campo);
        return texto == null ? null : texto.strip();
    }

    private static String modeloOpcional(Object bruto) {
        if (!(bruto instanceof String s) || s.isBlank()) {
            return null;
        }
        return s.strip();
    }

    private static Integer inteiro(Object valor) {
        if (valor instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        } else if (valor instanceof String s) {
            try {
                return Integer.parseInt(s.strip());
            } catch (NumberFormatException ignorada) {
            }
        }
        return null;
    }

    private static String descrever(Vaga v) {
        return v.spotName() + "/andar " + v.floor() + "/" + v.status()
                + (v.licensePlate() == null ? "" : "/" + v.licensePlate());
    }
}
