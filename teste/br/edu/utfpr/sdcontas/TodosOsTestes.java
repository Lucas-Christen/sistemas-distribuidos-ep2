package br.edu.utfpr.sdcontas;

public final class TodosOsTestes {
    private TodosOsTestes() {
    }

    public static void main(String[] args) throws Exception {
        Verifica v = new Verifica();
        TesteProtocolo.executar(v);
        TesteServidor.executar(v);
        System.exit(v.resumo() ? 0 : 1);
    }
}
