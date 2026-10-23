package avilaos.kernel.process;

/**
 * Los 3 semáforos de cada buffer (algoritmo productor-consumidor del anexo).
 * Cada uno sabe con qué motivo se bloquea un proceso que espera en él.
 */
public enum BufferSemaphore {
    MUTEX("s", BlockReason.SEM_MUTEX),   // exclusión mutua sobre el buffer
    FULL("n", BlockReason.SEM_FULL),     // cuántos elementos hay (consumidor espera aquí)
    EMPTY("e", BlockReason.SEM_EMPTY);   // cuántos espacios libres hay (productor espera aquí)

    private final String symbol;
    private final BlockReason blockReason;

    BufferSemaphore(String symbol, BlockReason blockReason) {
        this.symbol = symbol;
        this.blockReason = blockReason;
    }

    public String getSymbol() { return symbol; }

    public BlockReason getBlockReason() { return blockReason; }
}
