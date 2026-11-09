package lucaschristen.sistemasdistribuidos.protocolo;

public final class Protocolo {
    private Protocolo() {
    }

    public static final int PORTA_PADRAO = 21500;

    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String LOGOUT = "logout";
    public static final String GET_USER = "getuser";
    public static final String UPDATE_USER_NAME = "updateusername";
    public static final String UPDATE_USER_PASSWORD = "updateuserpassword";
    public static final String DELETE_USER = "deleteuser";

    public static final String CREATE_VAGA = "createvaga";
    public static final String GET_VAGAS = "getvagas";
    public static final String UPDATE_VAGA = "updatevaga";
    public static final String DELETE_VAGA = "deletevaga";

    public static final String CREATE_VEHICLE = "createvehicle";
    public static final String GET_VEHICLES = "getvehicles";
    public static final String GET_VEHICLE = "getvehicle";
    public static final String UPDATE_VEHICLE = "updatevehicle";
    public static final String DELETE_VEHICLE = "deletevehicle";
    public static final String FIND_VEHICLE = "findvehicle";

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    public static final String TIPO_ELETRICO = "eletrico";
    public static final String TIPO_COMBUSTAO = "combustao";

    public static final String VAGA_DISPONIVEL = "disponivel";
    public static final String VAGA_OCUPADA = "ocupada";
    public static final String VAGA_FORA_DE_SERVICO = "fora_de_servico";

    public static final String ADMIN_INICIAL_NOME = "admin";
    public static final String ADMIN_INICIAL_USERNAME = "admin";
    public static final String ADMIN_INICIAL_SENHA = "Admin123!";

    public static final int OK = 200;
    public static final int CRIADO = 201;
    public static final int REQUISICAO_INVALIDA = 400;
    public static final int NAO_AUTORIZADO = 401;
    public static final int NAO_ENCONTRADO = 404;
    public static final int CONFLITO = 409;
    public static final int ERRO_INTERNO = 500;

    public static String significado(int status) {
        switch (status) {
            case OK: return "Sucesso";
            case CRIADO: return "Criado";
            case REQUISICAO_INVALIDA: return "Campos errados";
            case NAO_AUTORIZADO: return "Não autorizado";
            case NAO_ENCONTRADO: return "Não encontrou";
            case CONFLITO: return "Conflito";
            case ERRO_INTERNO: return "Erro de servidor";
            default: return "Status " + status;
        }
    }

    public static final String MSG_LOGIN_OK = "Sucesso no Login";
    public static final String MSG_LOGIN_INVALIDO = "Usuario ou senhas inválidos";
    public static final String MSG_CADASTRO_OK = "Usuário criado com sucesso";
    public static final String MSG_USERNAME_EM_USO = "O username já está em uso.";
    public static final String MSG_LOGOUT_OK = "Usuário deslogado com sucesso";
    public static final String MSG_TOKEN_AUSENTE = "Token de autenticação não fornecido.";
    public static final String MSG_SESSAO_INVALIDA = "Sessão expirada ou encerrada.";
    public static final String MSG_USUARIO_ENCONTRADO = "Usuário encontrado com sucesso";
    public static final String MSG_USUARIO_NAO_ENCONTRADO = "Usuário não encontrado";
    public static final String MSG_NOME_ATUALIZADO = "Nome do usuário atualizado com sucesso";
    public static final String MSG_NOME_FORA_DO_PADRAO = "Nome do usuário fora do padrão";
    public static final String MSG_SENHA_ATUALIZADA = "Senha atualizada com sucesso";
    public static final String MSG_SENHA_FORA_DO_PADRAO = "Senha fora do padrão";
    public static final String MSG_USUARIO_DELETADO = "Usuário deletado com sucesso";
    public static final String MSG_OPERACAO_NAO_AUTORIZADA = "Operação não autorizada";

    public static String msgCampoForaDoPadrao(String campo) {
        return "O campo " + campo + " não está no padrão esperado.";
    }

    public static final String MSG_SENHA_ATUAL_INCORRETA = "Senha atual incorreta";
    public static final String MSG_JSON_MALFORMADO = "JSON malformado";
    public static final String MSG_REQUISICAO_NAO_OBJETO = "A requisição deve ser um objeto JSON.";
    public static final String MSG_METODO_AUSENTE = "O campo 'method' é obrigatório.";
    public static final String MSG_DATA_INVALIDO = "O campo 'data' deve ser um objeto JSON.";
    public static final String MSG_MENSAGEM_GRANDE = "Mensagem maior que o limite permitido.";
    public static final String MSG_ERRO_INTERNO = "Erro interno no servidor.";
    public static final String MSG_SOBRECARGA = "Servidor sobrecarregado. Tente novamente.";

    public static final String MSG_CAMPOS_INVALIDOS = "Campos obrigatórios ausentes ou inválidos.";
    public static final String MSG_APENAS_ADMIN = "Operação permitida apenas para administradores.";
    public static final String MSG_APENAS_USER = "Operação permitida apenas para usuários comuns.";
    public static final String MSG_ADMIN_CADASTRO = "Apenas administradores podem cadastrar usuários informando role.";
    public static final String MSG_USUARIO_COM_VEICULO_ESTACIONADO =
            "O usuário possui veículo estacionado. Libere a vaga antes desta operação.";

    public static final String MSG_VAGA_CRIADA = "Vaga criada com sucesso";
    public static final String MSG_VAGAS_OBTIDAS = "Vagas obtidas com sucesso";
    public static final String MSG_VAGA_ATUALIZADA = "Vaga atualizada com sucesso";
    public static final String MSG_VAGA_REMOVIDA = "Vaga removida com sucesso";
    public static final String MSG_VAGA_NAO_ENCONTRADA = "Vaga não encontrada.";
    public static final String MSG_VAGA_DUPLICADA = "Já existe uma vaga com este nome.";
    public static final String MSG_VAGA_OCUPADA_NAO_EXCLUI = "Vagas ocupadas não podem ser excluídas.";
    public static final String MSG_VAGA_NAO_DISPONIVEL = "A vaga não está disponível.";
    public static final String MSG_VAGA_NAO_OCUPADA = "A vaga não está ocupada.";
    public static final String MSG_VAGA_TIPO_INCOMPATIVEL = "O tipo do veículo é incompatível com o tipo da vaga.";
    public static final String MSG_VAGA_EM_USO_NAO_ALTERA =
            "O nome e o andar só podem ser alterados com a vaga disponível ou fora de serviço.";
    public static final String MSG_VAGA_FORA_DE_SERVICO_SO_DISPONIVEL =
            "A vaga só pode ficar fora de serviço quando estiver disponível.";
    public static final String MSG_VAGA_USER_NAO_RENOMEIA =
            "Apenas administradores podem alterar o nome ou o andar da vaga.";
    public static final String MSG_VAGA_USER_FORA_DE_SERVICO =
            "Apenas administradores podem colocar a vaga fora de serviço.";
    public static final String MSG_VAGA_ADMIN_NAO_ESTACIONA = "Administradores não podem estacionar veículos.";
    public static final String MSG_VAGA_LIBERAR_NAO_AUTORIZADO =
            "Somente quem ocupou a vaga ou um administrador pode liberá-la.";

    public static final String MSG_VEICULO_CRIADO = "Veículo criado com sucesso";
    public static final String MSG_VEICULOS_OBTIDOS = "Veículos obtidos com sucesso";
    public static final String MSG_VEICULO_ENCONTRADO = "Veículo encontrado com sucesso";
    public static final String MSG_VEICULO_ATUALIZADO = "Veículo atualizado com sucesso";
    public static final String MSG_VEICULO_REMOVIDO = "Veículo removido com sucesso";
    public static final String MSG_VEICULO_LOCALIZADO = "Veiculo localizado com sucesso";
    public static final String MSG_VEICULO_NAO_ENCONTRADO = "Veículo não encontrado.";
    public static final String MSG_VEICULO_NAO_ESTACIONADO = "Veículo não está estacionado em nenhuma vaga.";
    public static final String MSG_VEICULO_JA_ESTACIONADO = "Veículo já está estacionado em outra vaga.";
    public static final String MSG_PLACA_DUPLICADA = "Placa já cadastrada.";
    public static final String MSG_VEICULO_EM_VAGA =
            "Veículo associado a uma vaga. Libere a vaga antes desta operação.";

    public static String msgMetodoDesconhecido(String metodo) {
        return "Método desconhecido: " + metodo;
    }
}
