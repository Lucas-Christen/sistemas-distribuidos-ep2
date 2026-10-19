package lucaschristen.sistemasdistribuidos.protocolo;

public class JsonInvalidoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int posicao;

    public JsonInvalidoException(String motivo, int posicao) {
        super(motivo + " (posição " + posicao + ")");
        this.posicao = posicao;
    }

    public int getPosicao() {
        return posicao;
    }
}
