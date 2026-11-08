package com.example.beautifuldecor.megaphone.client;

/** V4.0.0: una forma de capturar el micrófono de la plataforma (OpenAL del propio juego, o Java Sound como plan B). */
interface MicBackend {
    String name();

    /** Abre y arranca la captura (16 kHz, mono, 16 bits). Lanza una excepción si no se puede. */
    void open() throws Exception;

    /** Lectura sin esperar: copia hasta {@code max} muestras y devuelve cuántas copió (0 si todavía no hay). */
    int read(short[] out, int max) throws Exception;

    void close();
}
