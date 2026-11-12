package com.example.jjsmod;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Tiny scheduler for multi-tick effects. */
@EventBusSubscriber(modid = JJSMod.MODID)
public class Tasks {
    public interface Task {
        /** @return true when the task is finished */
        boolean tick();
    }

    private static final List<Task> active = new ArrayList<>();
    private static final List<Task> pending = new ArrayList<>();

    public static void add(Task t) {
        pending.add(t);
    }

    public static void later(int ticks, Runnable r) {
        int[] left = {ticks};
        add(() -> {
            if (--left[0] <= 0) {
                r.run();
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        active.clear();
        pending.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        active.addAll(pending);
        pending.clear();
        active.removeIf(t -> {
            try {
                return t.tick();
            } catch (Exception ex) {
                ex.printStackTrace();
                return true;
            }
        });
    }
}
