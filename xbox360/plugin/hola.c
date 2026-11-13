/* Plugin de prueba (fase 4): al apretar START + clic del stick derecho en cualquier control, muestra un aviso en pantalla. */
#include "../sdk/xbox.h"

static u32 g_thread, g_tid;

static void sleep_ms(u32 ms) {
  /* intervalo negativo = relativo, en unidades de 100 ns */
  volatile long long iv = -(long long)ms * 10000;
  KeDelayExecutionThread(1, 0, (s32*)&iv);
}

static void aviso(const wchar_t* t) { XNotifyQueueUI(34, 0xFF, 2, t, NULL); }

static u32 hilo(void* ctx) {
  (void)ctx;
  int abierto = 0;
  sleep_ms(8000);   /* deja que la consola termine de arrancar */
  aviso(L"Radio360 cargado: START + R3 para abrir");
  for (;;) {
    for (u32 u = 0; u < 4; u++) {
      XINPUT_STATE st;
      if (XamInputGetState(u, 1, &st) != 0) continue;
      int combo = (st.buttons & PAD_START) && (st.buttons & PAD_RTHUMB);
      if (combo && !abierto) {
        abierto = 1;
        aviso(L"Radio360: combinacion detectada");
      } else if (!combo) {
        abierto = 0;
      }
    }
    sleep_ms(50);
  }
  return 0;
}

int _dll_start(void* modulo, u32 motivo, void* reservado) {
  (void)modulo; (void)reservado;
  if (motivo == 1) ExCreateThread(&g_thread, 0, &g_tid, NULL, (void*)hilo, NULL, 0);
  return 1;
}
