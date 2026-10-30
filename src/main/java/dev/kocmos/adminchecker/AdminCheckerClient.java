package dev.kocmos.adminchecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Admin Checker: /mo включает/выключает список админов в правом нижнем углу.
 *  - в игре   -> зелёный
 *  - в спеке  -> красный  (В СПЕКЕ)
 *  - в ванише -> голубой  (невидимость)
 *  - не на сервере -> серый
 * Когда статус любого админа меняется, звенит колокольчик.
 */
public class AdminCheckerClient implements ClientModInitializer {

    public static final String MOD_ID = "adminchecker";

    // ===== НАСТРОЙКИ =====
    /** Ники, за которыми следим (регистр не важен). */
    private static final List<String> ADMINS = List.of("KOCMOC", "X111AM");
    /** Отступ от правого и нижнего края экрана. */
    private static final int MARGIN_RIGHT = 6;
    private static final int MARGIN_BOTTOM = 6;
    private static final int LINE_HEIGHT = 11;
    /** Колокольчик при смене статуса. */
    private static final boolean BELL_ENABLED = true;
    // Цвета в формате ARGB (первые FF = непрозрачный)
    private static final int GREEN = 0xFF55FF55;
    private static final int RED   = 0xFFFF5555;
    private static final int AQUA  = 0xFF55FFFF;
    private static final int GRAY  = 0xFFAAAAAA;
    // =====================

    private record Status(String label, int color) {}

    private static boolean enabled = false;
    /** Последние известные статусы (обновляются раз в тик). */
    private static final Map<String, Status> current = new HashMap<>();
    /** Предыдущие метки, чтобы заметить смену статуса. */
    private static final Map<String, String> lastLabels = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("mo").executes(ctx -> {
                enabled = !enabled;
                // при каждом переключении начинаем «с чистого листа», чтобы не звенеть зря
                current.clear();
                lastLabels.clear();
                ctx.getSource().sendFeedback(
                    Component.literal("Admin Checker: " + (enabled ? "ВКЛ" : "ВЫКЛ")));
                return 1;
            }))
        );

        ClientTickEvents.END_CLIENT_TICK.register(AdminCheckerClient::tick);

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(MOD_ID, "admin_list"),
            AdminCheckerClient::render
        );
    }

    // ---------- логика (раз в тик) ----------

    private static void tick(Minecraft mc) {
        if (!enabled) return;

        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            // вышли с сервера — сбрасываем состояние
            current.clear();
            lastLabels.clear();
            return;
        }

        boolean ring = false;
        for (String name : ADMINS) {
            Status status = getStatus(mc, name);
            current.put(name, status);

            String previous = lastLabels.put(name, status.label());
            // previous == null — первый замер, не звеним
            if (previous != null && !previous.equals(status.label())) {
                ring = true;
            }
        }

        if (ring && BELL_ENABLED) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0F));
        }
    }

    private static Status getStatus(Minecraft mc, String name) {
        // 1) Есть ли игрок в списке игроков (таб) и какой у него режим?
        PlayerInfo info = null;
        for (PlayerInfo candidate : mc.getConnection().getOnlinePlayers()) {
            if (candidate.getProfile().name().equalsIgnoreCase(name)) {
                info = candidate;
                break;
            }
        }
        if (info == null) {
            return new Status("не на сервере", GRAY);
        }

        // 2) Спектатор -> красный
        if (info.getGameMode() == GameType.SPECTATOR) {
            return new Status("В СПЕКЕ", RED);
        }

        // 3) Невидимость (ваниш / зелье) -> голубой. Работает, когда игрок загружен рядом с нами.
        for (Player p : mc.level.players()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                if (p.isInvisible() || p.hasEffect(MobEffects.INVISIBILITY)) {
                    return new Status("в ванише", AQUA);
                }
                break;
            }
        }

        // 4) Иначе обычный режим -> зелёный
        return new Status("в игре", GREEN);
    }

    // ---------- отрисовка (правый нижний угол) ----------

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (!enabled || current.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Рисуем снизу вверх, чтобы последняя строка прилегала к нижнему краю.
        int y = screenH - MARGIN_BOTTOM - LINE_HEIGHT;
        for (int i = ADMINS.size() - 1; i >= 0; i--) {
            String name = ADMINS.get(i);
            Status status = current.get(name);
            if (status == null) continue;

            String text = name + " - " + status.label();
            int x = screenW - MARGIN_RIGHT - font.width(text); // выравнивание по правому краю
            graphics.text(font, text, x, y, status.color(), true);
            y -= LINE_HEIGHT;
        }
    }
}
