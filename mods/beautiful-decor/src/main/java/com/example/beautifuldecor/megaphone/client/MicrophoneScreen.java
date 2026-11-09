package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.megaphone.PaDsp;
import com.example.beautifuldecor.network.MicControlPacket;
import com.example.beautifuldecor.network.MicStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * V4.0.0: menú del micrófono. Aquí se prende y se apaga «Hablar» (se usa el micrófono de tu dispositivo, el que sea), se ve el nivel de
 * tu voz, se ajusta la sensibilidad y se vinculan o desvinculan las bocinas. Si cierras el menú mientras hablas, el micrófono sigue abierto
 * (sale el aviso «EN VIVO»); para apagarlo, vuelve a hacer clic en el micrófono.
 */
public class MicrophoneScreen extends Screen {
    private static final int WIDTH = 200;
    private final BlockPos pos;
    private Button talkButton;
    private int ticks;

    public MicrophoneScreen(BlockPos pos) {
        super(Component.translatable("screen.beautifuldecor.microphone.title"));
        this.pos = pos;
        PaClient.sendControl(pos, MicControlPacket.QUERY);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - WIDTH / 2;
        int top = Math.max(6, this.height / 2 - 98);
        this.addRenderableWidget(new SensitivitySlider(left, top + 64, WIDTH, 20));
        this.talkButton = this.addRenderableWidget(Button.builder(this.talkLabel(), button -> this.toggleTalk())
                .bounds(left, top + 90, WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.beautifuldecor.microphone.link"),
                        button -> PaClient.sendControl(this.pos, MicControlPacket.LINK_NEARBY))
                .bounds(left, top + 114, WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.beautifuldecor.microphone.unlink"),
                        button -> PaClient.sendControl(this.pos, MicControlPacket.UNLINK_ALL))
                .bounds(left + WIDTH / 2 + 2, top + 114, WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(this.listenLabel(), button -> {
            PaClient.setListenSelf(!PaClient.listenSelf());
            button.setMessage(this.listenLabel());
        }).bounds(left, top + 138, WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left, top + 162, WIDTH, 20).build());
    }

    private Component talkLabel() {
        return Component.translatable(MicCapture.isActiveOn(this.pos) ? "screen.beautifuldecor.microphone.stop_talk"
                : "screen.beautifuldecor.microphone.talk");
    }

    private Component listenLabel() {
        return Component.translatable("screen.beautifuldecor.microphone.listen_self",
                Component.translatable(PaClient.listenSelf() ? "options.on" : "options.off"));
    }

    private void toggleTalk() {
        if (MicCapture.isActiveOn(this.pos)) {
            MicCapture.stop();
            PaClient.sendControl(this.pos, MicControlPacket.STOP_TALK);
        } else {
            PaClient.resetStats();
            MicCapture.start(this.pos);
            PaClient.sendControl(this.pos, MicControlPacket.START_TALK);
        }
        this.talkButton.setMessage(this.talkLabel());
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.ticks % 40 == 0) {
            PaClient.sendControl(this.pos, MicControlPacket.QUERY);       // para ver si otro empieza o deja de hablar
        }
        this.talkButton.setMessage(this.talkLabel());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int left = cx - WIDTH / 2;
        int top = Math.max(6, this.height / 2 - 98);

        graphics.drawCenteredString(this.font, this.title, cx, top, 0xFFFFFFFF);

        MicStatePacket state = PaClient.stateOf(this.pos);
        Component speakers = state == null
                ? Component.translatable("screen.beautifuldecor.microphone.speakers_unknown")
                : Component.translatable("screen.beautifuldecor.microphone.speakers", state.speakers, state.maxSpeakers);
        graphics.drawCenteredString(this.font, speakers, cx, top + 14, 0xFFC8C8C8);

        graphics.drawCenteredString(this.font, this.statusLine(state), cx, top + 27, this.statusColor(state));
        Component notice = PaClient.recentMessage(this.pos);
        if (notice != null) {
            graphics.drawCenteredString(this.font, notice, cx, top + 39, 0xFFFFD060);
        } else if (MicCapture.isActiveOn(this.pos) && MicCapture.status() == MicCapture.Status.OK) {
            // qué pasa con el audio de vuelta: así se ve si el fallo es que no llega del servidor o que no suena aquí
            Component line;
            int lineColor = 0xFF9AA0A8;
            if (PaPlayback.problem() != null) {
                line = Component.translatable("screen.beautifuldecor.microphone.playback_problem", PaPlayback.problem());
                lineColor = 0xFFFFA040;
            } else if (!PaClient.listenSelf()) {
                line = Component.translatable("screen.beautifuldecor.microphone.hint_self");
            } else if (PaClient.received() == 0) {
                line = Component.translatable("screen.beautifuldecor.microphone.playback_none");
            } else {
                line = Component.translatable("screen.beautifuldecor.microphone.playback_ok", PaClient.received(), PaPlayback.played());
                lineColor = 0xFF60E070;
            }
            graphics.drawCenteredString(this.font, line, cx, top + 39, lineColor);
        }

        // barra de nivel con la marca de la sensibilidad
        int barTop = top + 52;
        graphics.fill(left, barTop, left + WIDTH, barTop + 8, 0xFF202020);
        int filled = Math.round(WIDTH * MicCapture.level());
        int color = MicCapture.level() > 0.85f ? 0xFFE04040 : MicCapture.level() > 0.65f ? 0xFFE0C040 : 0xFF40D060;
        graphics.fill(left, barTop, left + filled, barTop + 8, MicCapture.gateOpen() ? color : 0xFF707070);
        int mark = left + Math.round(WIDTH * PaDsp.meter(PaDsp.threshold(MicCapture.sensitivity())));
        graphics.fill(mark, barTop - 1, mark + 1, barTop + 9, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component statusLine(@Nullable MicStatePacket state) {
        if (state != null && !state.talkerName.isEmpty() && !state.youTalking && !MicCapture.isActiveOn(this.pos)) {
            return Component.translatable("screen.beautifuldecor.microphone.talker_other", state.talkerName);
        }
        if (!MicCapture.isActiveOn(this.pos)) {
            if (MicCapture.status() == MicCapture.Status.ERROR) {
                return Component.translatable("screen.beautifuldecor.microphone.status_error", MicCapture.error());
            }
            return Component.translatable("screen.beautifuldecor.microphone.status_off");
        }
        return switch (MicCapture.status()) {
            case STARTING -> Component.translatable("screen.beautifuldecor.microphone.status_starting");
            case SILENT -> Component.translatable("screen.beautifuldecor.microphone.status_silent");
            case ERROR -> Component.translatable("screen.beautifuldecor.microphone.status_error", MicCapture.error());
            default -> Component.translatable("screen.beautifuldecor.microphone.status_live", MicCapture.backendName());
        };
    }

    private int statusColor(@Nullable MicStatePacket state) {
        if (!MicCapture.isActiveOn(this.pos)) {
            return MicCapture.status() == MicCapture.Status.ERROR ? 0xFFFF6060
                    : state != null && !state.talkerName.isEmpty() && !state.youTalking ? 0xFFFFD060 : 0xFF9AA0A8;
        }
        return switch (MicCapture.status()) {
            case SILENT -> 0xFFFFA040;
            case ERROR -> 0xFFFF6060;
            case STARTING -> 0xFFC8C8C8;
            default -> 0xFF60E070;
        };
    }

    /** Sensibilidad del micrófono: a la izquierda solo capta voces fuertes, a la derecha casi todo. */
    private static final class SensitivitySlider extends AbstractSliderButton {
        SensitivitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), MicCapture.sensitivity());
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("screen.beautifuldecor.microphone.sensitivity", Math.round(this.value * 100.0)));
        }

        @Override
        protected void applyValue() {
            MicCapture.setSensitivity((float) this.value);
        }
    }
}
