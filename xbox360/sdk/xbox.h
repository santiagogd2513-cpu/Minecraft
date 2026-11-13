#ifndef XBOX_H
#define XBOX_H
/* Declaraciones mínimas de la consola (sin el kit oficial de Microsoft). Todo es big-endian, punteros de 32 bits. */
typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;
typedef signed short s16;
typedef signed int s32;
typedef unsigned long long u64;

#define NULL ((void*)0)
typedef unsigned short wchar_t;

typedef struct { u32 packet; u16 buttons; u8 lt, rt; s16 lx, ly, rx, ry; } XINPUT_STATE;
enum {
  PAD_UP = 0x0001, PAD_DOWN = 0x0002, PAD_LEFT = 0x0004, PAD_RIGHT = 0x0008, PAD_START = 0x0010, PAD_BACK = 0x0020,
  PAD_LTHUMB = 0x0040, PAD_RTHUMB = 0x0080, PAD_LB = 0x0100, PAD_RB = 0x0200,
  PAD_A = 0x1000, PAD_B = 0x2000, PAD_X = 0x4000, PAD_Y = 0x8000
};

int  DbgPrint(const char* fmt, ...);
u32  ExCreateThread(u32* handle, u32 stack, u32* tid, void* xapiStartup, void* fn, void* ctx, u32 flags);
void ExTerminateThread(u32 code);
u32  KeDelayExecutionThread(u32 mode, u32 alertable, s32* interval100ns_ptr_as_s64);
u32  NtClose(u32 handle);
u32  XamInputGetState(u32 user, u32 flags, XINPUT_STATE* st);
u32  XamGetCurrentTitleId(void);
/* areas = 64 bits en la consola; aquí se pasa en un registro, igual que hace el compilador oficial */
u32  XNotifyQueueUI(u32 type, u32 user, u32 areas, const wchar_t* text, void* ctx);
#endif
