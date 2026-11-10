package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.SecurityCameraBlock;
import com.example.beautifuldecor.camera.CameraInfo;
import com.example.beautifuldecor.camera.CameraNight;
import com.example.beautifuldecor.camera.CameraSweep;
import com.example.beautifuldecor.camera.SecurityCameraHub;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.HashSet;
import java.util.Set;

/**
 * V4.3.0: cámara de seguridad. Guarda su nombre, su descripción y si rota (se sincroniza con los clientes para dibujar el nombre en el
 * monitor). En el servidor se anota en la lista de cámaras ({@link SecurityCameraHub}). En el cliente calcula hacia dónde mira en cada
 * momento (barrido lento dentro de un arco, según los bloques que la rodean) y si está en visión nocturna.
 */
public class SecurityCameraBlockEntity extends BlockEntity {
    /** Las cámaras cargadas en este cliente (solo se usa del lado del cliente). */
    public static final Set<SecurityCameraBlockEntity> CLIENT = new HashSet<>();

    /** Cuánto se inclina hacia abajo (grados): la de pared mira casi recto, la de techo más hacia el suelo. */
    public static final float PITCH_WALL = 20f;
    public static final float PITCH_CEILING = 35f;
    /** Distancia del pivote al punto de donde sale la imagen (bloques): un poco por delante del lente. */
    public static final double LENS_FORWARD = 0.45;

    private String name = "";
    private String description = "";
    private boolean rotate;

    // ---- solo cliente
    private CameraSweep.Arc arc;
    private long arcTick = Long.MIN_VALUE;
    private boolean night;
    private long nightTick = Long.MIN_VALUE;
    private boolean nightInit;

    public SecurityCameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_CAMERA.get(), pos, state);
    }

    public String getCameraName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isRotating() {
        return this.rotate;
    }

    // ------------------------------------------------------------------ servidor

    /** Guarda el menú de la cámara y avisa a todos (lista de cámaras y clientes). */
    public void applyEdit(ServerLevel level, String newName, String newDescription, boolean newRotate) {
        CameraInfo info = SecurityCameraHub.get(level).register(this.worldPosition, newName, newDescription, newRotate);
        info.set(newName, newDescription, newRotate);
        this.name = info.name();
        this.description = info.description();
        this.rotate = info.rotate();
        setChanged();
        level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        SecurityCameraHub.get(level).changed(level, this.worldPosition);
    }

    /** Al cargar, si la cámara no estaba en la lista (copiada o clonada), se anota con los datos que trae. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel sl) {
            SecurityCameraHub hub = SecurityCameraHub.get(sl);
            if (!hub.contains(this.worldPosition)) {
                CameraInfo info = hub.register(this.worldPosition, this.name, this.description, this.rotate);
                this.name = info.name();
                setChanged();
                hub.changed(sl, this.worldPosition);
            }
        } else if (this.level != null && this.level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CLIENT.add(this));
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide) {
            CLIENT.remove(this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        CLIENT.remove(this);
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------ guardado y sincronía

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Name", this.name);
        tag.putString("Desc", this.description);
        tag.putBoolean("Rotate", this.rotate);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.name = CameraInfo.clean(tag.getString("Name"), CameraInfo.MAX_NAME);
        this.description = CameraInfo.clean(tag.getString("Desc"), CameraInfo.MAX_DESC);
        this.rotate = tag.getBoolean("Rotate");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
            this.arcTick = Long.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ geometría y movimiento (cliente)

    private SecurityCameraBlock.Mount mount() {
        return getBlockState().getValue(SecurityCameraBlock.MOUNT);
    }

    private Direction facing() {
        return getBlockState().getValue(SecurityCameraBlock.FACING);
    }

    public float baseYaw() {
        return facing().toYRot();
    }

    public float pitch() {
        return mount() == SecurityCameraBlock.Mount.CEILING ? PITCH_CEILING : PITCH_WALL;
    }

    /** El punto sobre el que gira la cabeza de la cámara, en coordenadas del mundo. */
    public Vec3 pivot() {
        // desplazamientos desde el centro del bloque, para facing = norte (la pared queda al sur): en dieciseisavos
        double dx = 0;
        double dy;
        double dz;
        if (mount() == SecurityCameraBlock.Mount.CEILING) {
            dy = 2.5;
            dz = 0;
        } else {
            dy = 0.5;
            dz = 1.5;
        }
        double rx;
        double rz;
        switch (facing()) {
            case EAST -> {
                rx = -dz;
                rz = dx;
            }
            case SOUTH -> {
                rx = -dx;
                rz = -dz;
            }
            case WEST -> {
                rx = dz;
                rz = -dx;
            }
            default -> {
                rx = dx;
                rz = dz;
            }
        }
        return new Vec3(this.worldPosition.getX() + 0.5 + rx / 16.0, this.worldPosition.getY() + 0.5 + dy / 16.0, this.worldPosition.getZ() + 0.5 + rz / 16.0);
    }

    /** Hacia dónde mira (grados de Minecraft) en este momento. */
    public float currentYaw(Level level, float partialTick) {
        float base = baseYaw();
        if (!this.rotate) {
            return base;
        }
        long game = level.getGameTime();
        if (this.arc == null || game - this.arcTick >= 20 || game < this.arcTick) {
            this.arc = computeArc(level);
            this.arcTick = game;
        }
        double offset = (this.worldPosition.asLong() & 0xFFFFL) * 7.0;
        return CameraSweep.yaw(this.arc, base, true, game + (double) partialTick, offset);
    }

    private CameraSweep.Arc computeArc(Level level) {
        float base = baseYaw();
        if (mount() == SecurityCameraBlock.Mount.CEILING) {
            boolean[] blocked = new boolean[4];
            for (int d = 0; d < 4; d++) {
                Direction dir = Direction.from2DDataValue(d);
                blocked[d] = solid(level, this.worldPosition.relative(dir));
            }
            return CameraSweep.ceilingArc(blocked, base);
        }
        Direction minus = Direction.fromYRot(base - 90f);
        Direction plus = Direction.fromYRot(base + 90f);
        return CameraSweep.wallArc(base, solid(level, this.worldPosition.relative(minus)), solid(level, this.worldPosition.relative(plus)));
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).blocksMotion();
    }

    /** El punto desde el que se ve la imagen de la cámara. */
    public Vec3 lensPosition(float yaw) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch());
        Vec3 p = pivot();
        double h = Math.cos(pitchRad) * LENS_FORWARD;
        return new Vec3(p.x - Math.sin(yawRad) * h, p.y - Math.sin(pitchRad) * LENS_FORWARD, p.z + Math.cos(yawRad) * h);
    }

    // ------------------------------------------------------------------ visión nocturna (cliente)

    public boolean isNightVision() {
        return this.night;
    }

    /**
     * Revisa la luz de la cámara (como mucho una vez cada 10 ticks) y cambia a visión nocturna o de vuelta. Devuelve true si el modo cambió.
     */
    public boolean updateNight(Level level) {
        long game = level.getGameTime();
        if (game - this.nightTick < 10 && game >= this.nightTick) {
            return false;
        }
        this.nightTick = game;
        int brightness = level.getRawBrightness(this.worldPosition, level.getSkyDarken());
        boolean next = CameraNight.next(this.night, Mth.clamp(brightness, 0, 15), level.getDayTime());
        boolean changed = next != this.night && this.nightInit;       // la primera revisión no cuenta como cambio (sin clic al cargar)
        this.night = next;
        this.nightInit = true;
        return changed;
    }
}
