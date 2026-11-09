# LucasChristenSistemasDistribuidos — EP2

Cliente e servidor de **estacionamento** sobre **sockets TCP com mensagens JSON**, seguindo o protocolo combinado na disciplina de Sistemas Distribuídos (UTFPR).

Esta é a **Parte 2**. Ela parte da [EP1](https://github.com/Lucas-Christen/sistemas-distribuidos) (contas e sessão, que continua funcionando igual) e acrescenta:
- **perfis** `admin` e `user`, com uma conta admin criada na inicialização;
- **CRUD de vagas** (admin) e estacionar/liberar (user);
- **CRUD de veículos** (cada user só enxerga os próprios) e **busca do veículo estacionado** pela placa.

- Java **17+**, sem nenhuma dependência externa (nem Maven): compila com `javac` ou no Eclipse.
- Cliente e servidor com interface gráfica (Swing). O servidor também roda só no terminal.
- Feito para **conversar com as implementações dos colegas**: segue o protocolo à risca no que envia e é tolerante no que recebe.

---

## Como rodar

### Pelo terminal (Linux/macOS ou Windows)
```bash
./compilar.sh                            # Windows: compilar.bat
java -jar dist/servidor.jar              # janela do servidor (porta padrão 21500)
java -jar dist/servidor.jar --console    # servidor no terminal (pergunta a porta)
java -jar dist/servidor.jar --console 21500 --dados minha-pasta
java -jar dist/cliente.jar               # janela do cliente
./testar.sh                              # Windows: testar.bat (ou ./testar.sh no Git Bash): roda os 370 testes
```

**Conta admin inicial:** `admin` / `Admin123!` (name `admin`, role `admin`). Se ela não existir quando o servidor sobe, é criada.

### Pelo Eclipse
`File → Import → Existing Projects into Workspace` e escolha esta pasta. Depois, `Run As → Java Application` em:
- `lucaschristen.sistemasdistribuidos.servidor.ServidorApp`
- `lucaschristen.sistemasdistribuidos.cliente.ClienteApp`
- `lucaschristen.sistemasdistribuidos.TodosOsTestes` (testes)

### Testar na mão com netcat
```bash
echo '{"method":"login","data":{"username":"admin","password":"Admin123!"}}' | nc localhost 21500
```

---

## Protocolo (resumo)

Requisição: `{"method": "<minúsculo>", "data": {...}}` · Resposta: `{"statusCode": int, "message": String, "data": T|null}`.
Uma conexão TCP por requisição; o servidor responde uma linha JSON terminada em `\n` e encerra a conexão.

### Contas (EP1 + regras de admin)
| method | data enviado | sucesso |
| :--- | :--- | :--- |
| `login` | `username`, `password` | `200 "Sucesso no Login"`, `data: {token}` |
| `register` | `name`, `username`, `password`, `role?`, `token?` | `201 "Usuário criado com sucesso"` |
| `logout` | `token` | `200 "Usuário deslogado com sucesso"` |
| `getuser` | `token`, `username` | `200 "Usuário encontrado com sucesso"`, `data: {name, username, role}` |
| `updateusername` | `token`, `username`, `name` | `200 "Nome do usuário atualizado com sucesso"` |
| `updateuserpassword` | `token`, `username`, `oldPassword`, `newPassword` | `200 "Senha atualizada com sucesso"` |
| `deleteuser` | `token`, `username` | `200 "Usuário deletado com sucesso"` |

- `register` sem `role` e sem `token` cria `user` (EP1). Com os dois, o token precisa ser de admin e a conta é criada com a role pedida.
- `getuser`: o admin recebe os dados do `username` pedido; o user recebe sempre os próprios.
- `updateusername` / `deleteuser`: o admin age sobre qualquer conta; o user só sobre a própria. O `username` nunca é renomeado.
- `deleteuser` recusa (409) se a conta tiver veículo estacionado; se passar, apaga os veículos dela em cascata e derruba as sessões dela.

### Vagas
| method | perfil | data enviado | sucesso |
| :--- | :--- | :--- | :--- |
| `createvaga` | admin | `token`, `spotName`, `floor`, `type` | `201 "Vaga criada com sucesso"` (status `disponivel` posto pelo servidor) |
| `getvagas` | ambos | `token` | `200 "Vagas obtidas com sucesso"`, `data: [{floor, spotName, status, licensePlate, type}]` |
| `updatevaga` | ambos | `token`, `spotName`, `newSpotName?`, `floor?`, `status?`, `licensePlate?` | `200 "Vaga atualizada com sucesso"` |
| `deletevaga` | admin | `token`, `spotName` | `200 "Vaga removida com sucesso"` |

`updatevaga`:
- **admin** altera `newSpotName`, `floor` e `status` (`disponivel` / `fora_de_servico`). Nome e andar só mudam com a vaga `disponivel` ou `fora_de_servico`. `fora_de_servico` só a partir de `disponivel`.
- **user** estaciona (`status: "ocupada"` + `licensePlate` de um veículo seu, com o mesmo `type` da vaga) e libera (`status: "disponivel"` ou `licensePlate: null`).
- Só quem ocupou a vaga, ou um admin, pode liberá-la. Campos omitidos preservam o valor atual.

### Veículos
| method | perfil | data enviado | sucesso |
| :--- | :--- | :--- | :--- |
| `createvehicle` | user | `token`, `brand`, `model`, `type`, `licensePlate` | `201 "Veículo criado com sucesso"` |
| `getvehicles` | user | `token` | `200 "Veículos obtidos com sucesso"`, `data: [{brand, model, type, licensePlate}]` (ou `[]`) |
| `getvehicle` | ambos | `token`, `licensePlate` | `200 "Veículo encontrado com sucesso"`, `data: {brand, model, type, licensePlate}` |
| `updatevehicle` | user | `token`, `licensePlate`, `brand?`, `model?`, `type?` | `200 "Veículo atualizado com sucesso"` |
| `deletevehicle` | ambos | `token`, `licensePlate` | `200 "Veículo removido com sucesso"` |
| `findvehicle` | ambos | `token`, `licensePlate` | `200 "Veiculo localizado com sucesso"`, `data: {floor, spotName, status}` |

- O dono (`ownerUsername`) vem do token, é gravado junto com o veículo e nunca aparece na resposta. `username`/`ownerUsername` no payload são ignorados.
- O user só enxerga os próprios veículos: placa de outra conta e placa inexistente dão **a mesma** resposta, `404 "Veículo não encontrado."`. O admin enxerga qualquer veículo.
- Placa: 7 letras/números, única no sistema e guardada em maiúsculas. `brand`: 1 a 20 caracteres. `model`: até 20 caracteres ou `null`. `type`: `eletrico` / `combustao`.
- Mudar `type`, ou excluir um veículo que está numa vaga: `409 "Veículo associado a uma vaga. Libere a vaga antes desta operação."`.

### Erros comuns
| Situação | Resposta |
| :--- | :--- |
| Token ausente | `400 "Token de autenticação não fornecido."` |
| Token inválido, expirado ou encerrado | `401 "Sessão expirada ou encerrada."` (o cliente encerra a sessão) |
| Token válido sem permissão | `401` com a mensagem da permissão, por ex. `"Operação permitida apenas para administradores."` / `"Operação permitida apenas para usuários comuns."` (o cliente mantém a sessão) |
| Campo ausente ou inválido nos métodos novos | `400 "Campos obrigatórios ausentes ou inválidos."` |
| Placa ou nome de vaga repetido | `409 "Placa já cadastrada."` / `409 "Já existe uma vaga com este nome."` |
| Falha interna (inclusive ao gravar o arquivo) | `500 "Erro interno no servidor."` (nada é confirmado nem gravado pela metade) |

Todas as mensagens estão centralizadas em `protocolo/Protocolo.java`.

### Decisões onde a especificação não define
| Situação | Resposta escolhida | Por quê |
| :--- | :--- | :--- |
| `register` com `role` e sem `token` | `400 "Token de autenticação não fornecido."` | Combinação incompleta; falta justamente o token |
| `register` com `token` e sem `role` (ou role inválida) | `400 "O campo role não está no padrão esperado."` | Mesmo formato dos outros erros de campo do `register` |
| Admin estacionando (`licensePlate` ou `status: "ocupada"`) | `401 "Administradores não podem estacionar veículos."` | `licensePlate` é campo do user na especificação |
| `findvehicle` de veículo cadastrado mas fora de vaga | `404 "Veículo não está estacionado em nenhuma vaga."` | Só depois de confirmar que a placa é visível para quem pergunta |
| `updatevaga`/`updatevehicle` sem nenhum campo para mudar | `400 "Campos obrigatórios ausentes ou inválidos."` | Nada para atualizar |
| Nome de vaga com outra caixa (`a1` × `A1`) | Tratado como o mesmo nome | Evita duas vagas "iguais" |
| Placa em minúsculas | Aceita e guardada em maiúsculas | `abc1234` e `ABC1234` são a mesma placa |
| User libera vaga `fora_de_servico` | `401 "Operação permitida apenas para administradores."` | Só o admin tira a vaga de fora de serviço |
| Admin exclui a própria conta | Permitido; a conta `admin` volta a ser criada no próximo início | A especificação não proíbe |

As decisões da EP1 continuam valendo (token não-UUID → 401, token vazio → 400, `method` com maiúscula aceito, etc.).

---

## Cliente

Depois do login, o cliente pergunta ao servidor (`getuser`) qual é o perfil da sessão e mostra as abas que fazem sentido:

| Aba | user | admin |
| :--- | :---: | :---: |
| Minha conta (dados, nome, senha, logout, excluir conta) | ✔ | ✔ |
| Vagas (lista; user estaciona/libera; admin cria/edita/exclui/libera) | ✔ | ✔ |
| Meus veículos (cadastrar, editar, excluir) | ✔ | |
| Buscar veículo (`findvehicle`, `getvehicle`, `deletevehicle`) | ✔ | ✔ |
| Administração (cadastrar com role, consultar/renomear/excluir outra conta) | | ✔ |

Esconder abas é só conveniência: quem decide é o servidor. Num 401, o cliente pergunta ao servidor se o token ainda vale antes de deslogar. Assim, uma recusa por falta de permissão aparece na barra de status e a sessão continua aberta. Os dados de outra conta consultada pelo admin ficam separados da sessão dele.

---

## Estrutura

```
src/lucaschristen/sistemasdistribuidos/
├── protocolo/   Json (parser próprio), Transporte (enquadramento TCP), Protocolo (constantes),
│                Resposta, Validacao, Dados (leitura tolerante), LogSeguro (mascara senhas)
├── servidor/    ServidorApp (main), ServidorTcp (accept + pool), Atendimento (1 conexão),
│                Despachante (method → operação), ServicoContas, ServicoEstacionamento, Autenticador,
│                Banco (usuários + vagas + veículos), Usuario, Vaga, Veiculo, Sessoes, Senhas (PBKDF2),
│                RegistroLog, ui/JanelaServidor
└── cliente/     ClienteApp (main), ClienteTcp (1 conexão por requisição), ApiContas (sessão + chamadas),
                 ui/JanelaCliente, ui/PainelVagas, ui/PainelVeiculos, ui/PainelBusca, ui/PainelAdmin
teste/lucaschristen/sistemasdistribuidos/   TodosOsTestes, TesteProtocolo, TesteServidor (EP1), TesteEp2, Verifica
```

### Servidor
- **Banco único** (`dados/banco.json`) com usuários, vagas e veículos. Cada operação roda numa transação: trabalha sobre uma cópia, grava o arquivo (temporário + move atômico) e só então passa a valer. Se a gravação falhar, a resposta é 500 e nada muda, nem em memória.
- **Transações serializadas:** a verificação de dono, de vínculo com vaga e a gravação acontecem juntas. Duas pessoas cadastrando a mesma placa, ou o mesmo veículo em duas vagas ao mesmo tempo, não passam.
- **Perfil sempre lido do banco** a cada requisição (o token só diz quem é a pessoa).
- **Migração da EP1:** se `banco.json` não existe mas há um `usuarios.json` da EP1 na mesma pasta, os usuários são importados como `user`.
- Continua da EP1: pool de threads com limite, tempo limite de 30 s por conexão, senhas com PBKDF2 + sal, sessões só em memória, log no formato da especificação.
- A janela do servidor ganhou as abas **Vagas** e **Veículos** e a coluna **Perfil** na lista de usuários.

---

## Testes

`./testar.sh` roda **370 verificações** sem JUnit: as 182 da EP1 (uma ajustada para a nova regra do `getuser`) e as novas da EP2:
- conta admin inicial; `register` com cada combinação de role/token;
- admin consultando, renomeando e excluindo outras contas, com a sessão dele intacta;
- vagas: criação, nome duplicado, renomear e depois achar o veículo pelo nome novo, fora de serviço, exclusão;
- veículos com duas contas user: listagem e consulta não mostram veículos de outra conta, e edição/exclusão por outro user não os alteram;
- admin consultando e excluindo veículos de terceiros, e sendo recusado nos endpoints exclusivos de user;
- 400 × 401 de sessão × 401 de permissão, lista vazia, campos omitidos/`null`, placa duplicada, conflitos com vaga;
- concorrência (mesma placa e mesmo veículo em várias vagas ao mesmo tempo);
- persistência após reinício, falha de gravação (500 sem efeito) e importação do arquivo da EP1.
