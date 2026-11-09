package lucaschristen.sistemasdistribuidos.servidor;

import static lucaschristen.sistemasdistribuidos.protocolo.Protocolo.*;

import lucaschristen.sistemasdistribuidos.protocolo.Dados;
import lucaschristen.sistemasdistribuidos.protocolo.Json;
import lucaschristen.sistemasdistribuidos.protocolo.JsonInvalidoException;
import lucaschristen.sistemasdistribuidos.protocolo.Resposta;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class Despachante {
    private final Map<String, Function<Map<String, Object>, Resposta>> rotas = new HashMap<>();
    private final RegistroLog log;

    public Despachante(ServicoContas servico, ServicoEstacionamento estacionamento, RegistroLog log) {
        this.log = log;
        rotas.put(LOGIN, servico::login);
        rotas.put(REGISTER, servico::register);
        rotas.put(LOGOUT, servico::logout);
        rotas.put(GET_USER, servico::getUser);
        rotas.put(UPDATE_USER_NAME, servico::updateUserName);
        rotas.put(UPDATE_USER_PASSWORD, servico::updateUserPassword);
        rotas.put(DELETE_USER, servico::deleteUser);
        rotas.put(CREATE_VAGA, estacionamento::createVaga);
        rotas.put(GET_VAGAS, estacionamento::getVagas);
        rotas.put(UPDATE_VAGA, estacionamento::updateVaga);
        rotas.put(DELETE_VAGA, estacionamento::deleteVaga);
        rotas.put(CREATE_VEHICLE, estacionamento::createVehicle);
        rotas.put(GET_VEHICLES, estacionamento::getVehicles);
        rotas.put(GET_VEHICLE, estacionamento::getVehicle);
        rotas.put(UPDATE_VEHICLE, estacionamento::updateVehicle);
        rotas.put(DELETE_VEHICLE, estacionamento::deleteVehicle);
        rotas.put(FIND_VEHICLE, estacionamento::findVehicle);
    }

    public Resposta processar(String textoRequisicao) {
        Object raiz;
        try {
            raiz = Json.ler(textoRequisicao);
        } catch (JsonInvalidoException e) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_JSON_MALFORMADO);
        }
        Map<String, Object> requisicao = Dados.comoObjeto(raiz);
        if (requisicao == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_REQUISICAO_NAO_OBJETO);
        }

        Object metodoBruto = Dados.campo(requisicao, "method");
        if (!(metodoBruto instanceof String metodoTexto) || metodoTexto.isBlank()) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_METODO_AUSENTE);
        }

        String metodo = metodoTexto.strip().toLowerCase(Locale.ROOT);

        Object dataBruto = Dados.campo(requisicao, "data");
        Map<String, Object> dados = dataBruto == null ? new LinkedHashMap<>() : Dados.comoObjeto(dataBruto);
        if (dados == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_DATA_INVALIDO);
        }

        Function<Map<String, Object>, Resposta> operacao = rotas.get(metodo);
        if (operacao == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgMetodoDesconhecido(metodoTexto));
        }
        try {
            return operacao.apply(dados);
        } catch (RuntimeException e) {
            log.erro("Falha ao processar '" + metodo + "': " + e);
            return Resposta.erro(ERRO_INTERNO, MSG_ERRO_INTERNO);
        }
    }
}
